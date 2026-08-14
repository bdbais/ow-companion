"""Extracts every balance change ever made to every hero, with dates.

    python tools/parse_patchlog.py

The wiki keeps these in a genuinely structured form - `{{PatchTableElement|2026-07-14|...}}`
holding `{{al|Ability}}` headings and bullet points - so the patch list itself parses
cleanly. On top of that, bullets phrased as "reduced from 14 to 12 seconds" are turned into
numbers so a stat can be plotted over time.

Whether a change is a buff or a nerf is not the direction of the number: a cooldown going
up is a nerf while damage going up is a buff. That is decided per stat below.
"""

from __future__ import annotations

import re
import sys

from common import DATASET, RAW, read_json, write_json
from wikitext import _matching_brace, clean_value, normalise_dashes, split_params

# Both spellings are in use across the roster; neither is more correct than the other.
SECTION = re.compile(r"^==\s*Balance Change Logs?\s*==\s*$", re.MULTILINE)
NEXT_SECTION = re.compile(r"^==[^=]", re.MULTILINE)

# Stats where a bigger number is worse for the hero. Everything else is assumed to be
# better when it goes up.
LOWER_IS_BETTER = (
    "cooldown",
    "cast time",
    "recovery",
    "reload",
    "spread",
    "charge time",
    "wind-up",
    "windup",
    "ammo cost",
    "cost",
    "delay",
    "recharge",
    "ultimate cost",
)

CHANGE = re.compile(
    r"(?P<stat>[A-Za-z][A-Za-z /'\-]{2,60}?)\s+"
    r"(?P<verb>increased|reduced|decreased|lowered|raised|improved|changed)\s+"
    r"from\s+(?P<before>[\d.]+)\s*(?P<unit>%|x)?\s*"
    r"(?:seconds?|sec|s\b|meters?|m\b|hp)?\s*"
    r"to\s+(?P<after>[\d.]+)",
    re.IGNORECASE,
)

# "Now deals 60 damage and healing (up from 25)"
PARENTHETICAL = re.compile(
    r"(?P<stat>[A-Za-z][A-Za-z /'\-]{2,60}?)\D{0,20}?(?P<after>[\d.]+)[^()]*"
    r"\((?:up|down)\s+from\s+(?P<before>[\d.]+)\)",
    re.IGNORECASE,
)


def balance_section(text: str) -> str:
    match = SECTION.search(text)
    if not match:
        return ""
    rest = text[match.end() :]
    following = NEXT_SECTION.search(rest)
    return rest[: following.start()] if following else rest


def tabs_of(section: str) -> dict[str, str]:
    """Split the ChangelogsTabber into its game modes; Stadium is balanced separately."""
    start = section.find("{{ChangelogsTabber")
    if start < 0:
        return {"owpvp": section}
    end = _matching_brace(section, start)
    body = section[start + 2 : end - 2]
    tabs = {}
    for part in split_params(body)[1:]:
        key, _, value = part.partition("=")
        tabs[key.strip().lower()] = value
    return tabs or {"owpvp": section}


def normalise_date(raw: str) -> str:
    """Force a patch date to ISO so chronological sorting is string sorting.

    Editors write these by hand, so a handful are `11-11-2025` or `2016-7-19`.
    """
    text = raw.strip()
    match = re.fullmatch(r"(\d{4})-(\d{1,2})-(\d{1,2})", text)
    if match:
        year, month, day = match.groups()
        return f"{year}-{int(month):02d}-{int(day):02d}"
    match = re.fullmatch(r"(\d{1,2})-(\d{1,2})-(\d{4})", text)
    if match:
        day, month, year = match.groups()
        return f"{year}-{int(month):02d}-{int(day):02d}"
    return text


def patch_entries(tab: str) -> list[tuple[str, str]]:
    """Every `{{PatchTableElement|date|body}}` in a tab, as (date, body)."""
    entries = []
    for match in re.finditer(r"\{\{\s*PatchTableElement\s*\|", tab):
        end = _matching_brace(tab, match.start())
        if end < 0:
            continue
        body = tab[match.start() + 2 : end - 2]
        parts = split_params(body)
        if len(parts) < 3:
            continue
        entries.append((normalise_date(parts[1]), "|".join(parts[2:])))
    return entries


def parse_body(body: str) -> list[dict]:
    """Bullet points grouped under the ability heading they belong to."""
    # Dev commentary explains the change but is not the change; keep it separate.
    comments = []
    for match in re.finditer(r"\{\{\s*DevComment\s*\|", body):
        end = _matching_brace(body, match.start())
        if end > 0:
            comments.append(clean_value(body[match.start() + 2 : end - 2].partition("|")[2]))
    body = re.sub(r"\{\{\s*DevComment\s*\|", "{{DevCommentRemoved|", body)

    groups: list[dict] = []
    current = {"ability": None, "changes": [], "comments": comments}

    for raw_line in body.split("\n"):
        line = raw_line.strip()
        if not line:
            continue

        heading = re.match(r"^\{\{\s*al\s*\|([^}|]*)", line)
        if heading:
            if current["changes"]:
                groups.append(current)
                current = {"ability": None, "changes": [], "comments": []}
            ability = clean_value(heading.group(1)).strip()
            suffix = clean_value(re.sub(r"^\{\{[^}]*\}\}", "", line)).strip(" -–—")
            current["ability"] = f"{ability} ({suffix})" if suffix else ability
            continue

        if line.startswith(";"):
            current["ability"] = clean_value(line.lstrip(";")).strip()
            continue

        if line.startswith("*"):
            text = clean_value(line.lstrip("*").strip())
            if text and not text.startswith("{{"):
                current["changes"].append(text)

    if current["changes"] or current["ability"]:
        groups.append(current)
    return [g for g in groups if g["changes"]]


# Words that turn a lower-is-better stat back around. A cooldown going up is a nerf, but a
# cooldown *refund* going up is a gift: the stat names the relief, not the cost.
INVERTING = (
    "refund",
    "reduction",
    "reduced by",
    "returned",
    "restored",
    "discount",
    "saved",
)

# Direction is a heuristic over prose and will never be perfect. "Shots until max spread
# reduction" reads as relief but fewer shots is the good outcome; a phrase like that comes
# out wrong. Wrong badges are worth reporting - see CONTRIBUTING.md.


def classify(stat: str, before: float, after: float) -> str:
    if after == before:
        return "neutral"
    lowered = stat.lower()
    lower_is_better = any(token in lowered for token in LOWER_IS_BETTER)
    if lower_is_better and any(token in lowered for token in INVERTING):
        lower_is_better = False
    went_up = after > before
    if lower_is_better:
        return "nerf" if went_up else "buff"
    return "buff" if went_up else "nerf"


# Where a qualifying clause begins. Everything from here on describes *which* case the
# change applies to, not what changed.
SUBORDINATOR = re.compile(
    r"\s+(?:for|with|when|while|that|which|if|after|before|above|below|against)\s+",
    re.IGNORECASE,
)

DIGITS = tuple("0123456789")


def rejoin_subject(text: str, start: int, captured: str) -> str | None:
    """The whole phrase that changed, when a number truncated the captured stat name.

    The stat pattern cannot contain digits, so a number in the middle of a sentence cuts the
    capture short and what survives is only the tail of the phrase:

        "Global projectile size modifier for travel time projectiles with a speed greater
         than 50 meters per second reduced from 0.1 to 0.075 meters."

    captured "meters per second" - a unit, not a stat - on sixteen heroes.

    Nothing displays it today: the damage timeline only plots stats whose name contains
    "damage", and everywhere else these rows are counted by direction rather than read. So
    this is a correctness fix in published data, not a fix to something anyone saw. It is
    worth making anyway - the dataset ships as a file other people can use, the name is
    simply wrong, and the next filter that reaches for stat names inherits the mistake.

    The tell is the character just before the capture: a digit there means a number ended the
    phrase early. The repair is to put the sentence back together from its start through the
    captured tail, then cut it at the first qualifying clause - what follows one of those says
    *which case* the change applies to, not what changed.

    Both halves of that matter. Cutting alone would have mangled

        "Damage accumulated over the first 1.0 seconds increased from 20 to 80"

    where the number belongs to the name; rejoining keeps "seconds" attached and the sentence
    has no qualifying clause to cut. Rejoining alone would have labelled the projectile change
    with its entire twenty-word qualifier.

    Returns None when the capture was not truncated, or when the result is too thin to be an
    improvement - a bad name is not worth replacing with a worse one.
    """
    before = text[:start]
    if not before.rstrip().endswith(DIGITS):
        return None
    sentence = re.split(r"(?<=[.;:])\s+", before)[-1]
    whole = sentence + captured
    head = SUBORDINATOR.split(whole, maxsplit=1)[0].strip(" .,([")
    if len(head) < 3 or not re.search(r"[A-Za-z]", head):
        return None
    return head


def numeric_changes(ability: str, text: str) -> list[dict]:
    """Statements of the form "X went from A to B", as structured values."""
    results = []
    normalised = normalise_dashes(text)
    for pattern in (CHANGE, PARENTHETICAL):
        for match in pattern.finditer(normalised):
            stat = match.group("stat").strip(" .,")
            stat = rejoin_subject(normalised, match.start("stat"), stat) or stat
            # Strip leading filler so "Now the cooldown" and "Cooldown" agree.
            stat = re.sub(
                r"^(now|also|the|its|his|her|their)\s+", "", stat, flags=re.IGNORECASE
            ).strip()
            if not stat:
                continue
            try:
                before = float(match.group("before"))
                after = float(match.group("after"))
            except ValueError:
                continue
            results.append(
                {
                    "ability": ability,
                    "stat": stat,
                    "from": before,
                    "to": after,
                    "unit": (match.groupdict().get("unit") or "").strip() or None,
                    "direction": classify(stat, before, after),
                }
            )
        if results:
            break
    return results


def parse_hero(name: str, text: str) -> dict:
    section = balance_section(text)
    if not section:
        return {"hero": name, "patches": [], "note": "no balance change log on the page"}

    patches = []
    for mode, tab in tabs_of(section).items():
        for date, body in patch_entries(tab):
            groups = parse_body(body)
            if not groups:
                continue
            changes = []
            stats = []
            for group in groups:
                ability = group["ability"] or "General"
                for line in group["changes"]:
                    changes.append({"ability": ability, "text": line})
                    stats.extend(numeric_changes(ability, line))
            patches.append(
                {
                    "date": date,
                    "mode": mode,
                    "changes": changes,
                    "stats": stats,
                    "comments": [c for g in groups for c in g["comments"] if c],
                }
            )

    patches.sort(key=lambda p: p["date"], reverse=True)
    return {"hero": name, "patches": patches}


# The two sentences that taught [rejoin_subject] what it has to do, one for each half of the
# rule. Asserted on every run: this parser is re-run whenever the wiki moves, and a quiet
# regression here does not crash anything - it just puts a unit where a stat name belongs.
SELF_CHECK = [
    (
        "Global projectile size modifier for travel time projectiles with a speed greater "
        "than 50 meters per second reduced from 0.1 to 0.075 meters.",
        "Global projectile size modifier",
    ),
    (
        "Damage accumulated over the first 1.0 seconds increased from 20 to 80 "
        "(damage per second after the first 1.0 seconds is unchanged)",
        "Damage accumulated over the first 1.0 seconds",
    ),
    # Untouched: nothing truncated these, so they must come through exactly as before.
    ("Cooldown increased from 6 to 8 seconds.", "Cooldown"),
    ("Explosion damage increased from 60 to 90.", "Explosion damage"),
]


def self_check() -> None:
    for sentence, expected in SELF_CHECK:
        found = [c["stat"] for c in numeric_changes("", sentence)]
        if expected not in found:
            raise AssertionError(f"{sentence!r} gave {found}, expected {expected!r}")


def main() -> int:
    self_check()
    roster = read_json(DATASET / "roster.json")["heroes"]
    history = []
    total_patches = 0
    total_changes = 0
    total_stats = 0
    without_log = []

    for hero in roster:
        path = RAW / "wiki" / f"{hero['key']}.wiki"
        parsed = parse_hero(hero["name"], path.read_text(encoding="utf-8"))
        parsed["key"] = hero["key"]
        if not parsed["patches"]:
            without_log.append(hero["name"])
        total_patches += len(parsed["patches"])
        total_changes += sum(len(p["changes"]) for p in parsed["patches"])
        total_stats += sum(len(p["stats"]) for p in parsed["patches"])
        history.append(parsed)

    write_json(DATASET / "patch-history.json", {"heroes": history})
    print(f"parsed {total_patches} patches, {total_changes} changes, {total_stats} numeric")
    print(f"  heroes with no log: {len(without_log)}")
    for name in without_log:
        print(f"    {name}")

    busiest = sorted(history, key=lambda h: -sum(len(p["changes"]) for p in h["patches"]))[:5]
    print("  most-changed heroes:")
    for hero in busiest:
        count = sum(len(p["changes"]) for p in hero["patches"])
        print(f"    {hero['hero']:<16} {len(hero['patches']):>3} patches, {count:>4} changes")
    return 0


if __name__ == "__main__":
    sys.exit(main())
