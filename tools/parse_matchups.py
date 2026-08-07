"""Extracts the wiki's hero-versus-hero notes: who gives this hero trouble, and why.

    python tools/parse_matchups.py

Every hero page carries a match-ups section holding one table per enemy role and one row per
opponent worth writing about. Editors do not document all fifty-one opponents for anyone -
they document the dozen or so that change how you play - which is exactly the list a reader
wants.

The pages are mid-migration between two formats and both are live:

* the older one is a plain wikitable of `Hero | Match-Up | Team Synergy`, prose only;
* the newer `{{MatchupTable/Tank}}` adds an explicit `_rating` ("WEAK MATCHUP") and `_risk`
  ("HIGH RISK") per opponent.

The rating is the useful part, and it is the wiki's judgement rather than ours: a counter is
only called a counter here when an editor wrote that down. Pages still on the old format
carry no rating and are shown as prose, which is honest - inferring a verdict from the tone
of a paragraph would put a confident label on every row and mean nothing.
"""

from __future__ import annotations

import re
import sys
import unicodedata

from common import DATASET, RAW, read_json, write_json
from wikitext import clean_value, split_params, strip_comments, _matching_brace

SECTION = re.compile(r"==\s*Match-ups? and team synergy\s*==(.*?)(?=\n==[^=])", re.S | re.I)
SUBSECTION = re.compile(r"===\s*(Tank|Damage|Support)\s*===(.*?)(?=\n===|\Z)", re.S | re.I)

# `|<center>[[File:icon-dva.png|75px|link=D.Va]]<br>[[D.Va]]</center>`. Half the pages write
# `Image:` instead of `File:`; MediaWiki treats them as one namespace, so accepting only the
# one spelling silently loses twenty-seven heroes.
OPPONENT = re.compile(
    r"\[\[\s*(?:File|Image):icon-([a-z0-9._-]+)\.png[^\]]*?link=([^\]|]+)", re.I
)

PLACEHOLDER = re.compile(r"^\(?to be added\)?\.?$", re.I)

# The two axes the newer tables grade on. Strength answers "can I win this duel?"; priority
# answers "should I be shooting them at all?" - a support may be a low-risk opponent and
# still the first thing you kill. Keeping them apart stops one being read as the other.
STANCE = (
    ("very-weak", ("VERY WEAK",)),
    ("weak", ("WEAK",)),
    ("very-strong", ("VERY STRONG",)),
    ("strong", ("STRONG",)),
    ("even", ("EVEN", "NEUTRAL", "MIRROR", "MEDIUM MATCHUP")),
)
PRIORITY = (
    ("extreme", ("EXTREMELY HIGH",)),
    ("high", ("HIGH",)),
    ("medium", ("MEDIUM",)),
    ("low", ("LOW",)),
)
RISK = (
    ("extreme", ("EXTREME",)),
    ("high", ("HIGH",)),
    ("medium", ("MEDIUM",)),
    ("low", ("LOW",)),
)


def classify(value: str, table: tuple) -> str | None:
    """First matching label, hardest first.

    Ratings like `EVEN -> WEAK MATCHUP` describe a matchup that shifts once someone hits a
    cooldown or an ultimate. Reading the *final* state is the safer half to report: it is
    the one that gets you killed.
    """
    if not value:
        return None
    text = value.upper().split("->")[-1].strip()
    for label, tokens in table:
        if any(token in text for token in tokens):
            return label
    return None


def normalise(name: str) -> str:
    """Fold a hero name or template key to a comparable form: `D.Va` and `DVa` both `dva`.

    Accents have to be folded rather than dropped: the roster says `Lúcio` while the
    template keys are ASCII, and stripping the accent outright leaves `lcio`.
    """
    folded = unicodedata.normalize("NFKD", name).encode("ascii", "ignore").decode()
    return re.sub(r"[^a-z0-9]", "", folded.lower())


def split_row(row: str) -> list[str]:
    """A wikitable row's cells. Cells start with `|` at the head of a line, or with `||`."""
    cells = []
    for part in re.split(r"\n\s*\|(?!\})", "\n" + row):
        if part.strip():
            cells.extend(part.split("||"))
    return cells


def entry(opponent: str, key: str, role: str, matchup: str, synergy: str, **extra) -> dict | None:
    matchup = "" if PLACEHOLDER.match(matchup) else matchup
    synergy = "" if PLACEHOLDER.match(synergy) else synergy
    if not matchup and not synergy and not extra.get("stance") and not extra.get("priority"):
        return None
    row = {
        "opponent": opponent,
        "key": key,
        "role": role.lower(),
        "matchup": matchup,
        "synergy": synergy,
        "rating": None,
        "stance": None,
        "priority": None,
        "risk": None,
    }
    row.update({k: v for k, v in extra.items()})
    return row


def parse_wikitable(body: str, role: str) -> list[dict]:
    rows = []
    for chunk in re.split(r"\n\s*\|-", body):
        cells = split_row(chunk)
        if len(cells) < 2:
            continue
        found = OPPONENT.search(cells[0])
        if not found:
            continue
        row = entry(
            opponent=clean_value(found.group(2)).strip(),
            key=normalise(found.group(1)),
            role=role,
            matchup=clean_value(cells[1]),
            synergy=clean_value(cells[2]) if len(cells) > 2 else "",
        )
        if row:
            rows.append(row)
    return rows


def parse_template(body: str, role: str, names: dict[str, str]) -> list[dict]:
    """`{{MatchupTable/Tank | DVa_rating = WEAK MATCHUP | DVa_matchup = ... }}`."""
    start = body.find("{{MatchupTable/")
    if start < 0:
        return []
    end = _matching_brace(body, start)
    if end < 0:
        return []

    fields: dict[str, dict[str, str]] = {}
    for part in split_params(body[start + 2 : end - 2])[1:]:
        key, _, value = part.partition("=")
        match = re.fullmatch(r"\s*([A-Za-z0-9_.]+?)_(rating|risk|matchup|synergy|synergy_rating)\s*", key)
        if not match:
            continue
        fields.setdefault(normalise(match.group(1)), {})[match.group(2)] = value.strip()

    rows = []
    for key, values in fields.items():
        rating = values.get("rating", "")
        row = entry(
            opponent=names.get(key, key),
            key=key,
            role=role,
            matchup=clean_value(values.get("matchup", "")),
            synergy=clean_value(values.get("synergy", "")),
            rating=rating or None,
            stance=classify(rating, STANCE),
            priority=classify(rating, PRIORITY),
            risk=classify(values.get("risk", ""), RISK),
        )
        if row:
            rows.append(row)
    return rows


def parse_hero(text: str, names: dict[str, str]) -> list[dict]:
    section = SECTION.search(text)
    if not section:
        return []
    body = strip_comments(section.group(1))
    matchups: list[dict] = []
    for role, table in SUBSECTION.findall(body):
        matchups.extend(parse_template(table, role, names) or parse_wikitable(table, role))
    return matchups


def main() -> int:
    roster = read_json(DATASET / "roster.json")["heroes"]
    names = {normalise(hero["name"]): hero["name"] for hero in roster}
    keys = {normalise(hero["name"]): hero["key"] for hero in roster}

    output = []
    total = rated = 0
    unknown: set[str] = set()
    without: list[str] = []

    for hero in roster:
        path = RAW / "wiki" / f"{hero['key']}.wiki"
        matchups = parse_hero(path.read_text(encoding="utf-8"), names)
        for row in matchups:
            if row["key"] in keys:
                # Older pages still link the hero by a retired name - Cassidy's rows say
                # "McCree" - while the icon file already uses the current one. The icon is
                # the more reliable of the two, so the roster's name wins.
                row["opponent"] = names[row["key"]]
                row["key"] = keys[row["key"]]
            else:
                unknown.add(row["opponent"])
        total += len(matchups)
        rated += sum(1 for row in matchups if row["stance"] or row["priority"])
        if not matchups:
            without.append(hero["name"])
        output.append({"key": hero["key"], "hero": hero["name"], "matchups": matchups})

    write_json(DATASET / "matchups.json", {"heroes": output})
    print(f"parsed {total} matchups, {rated} carrying the wiki's own rating")
    print(f"  heroes with none: {len(without)}")
    for name in without:
        print(f"    {name}")
    if unknown:
        # Retired heroes and typos both land here; neither should reach the app silently.
        print(f"  opponents not on the roster: {', '.join(sorted(unknown))}")

    hardest = [
        (hero["hero"], row["opponent"], row["rating"])
        for hero in output
        for row in hero["matchups"]
        if row["stance"] in ("weak", "very-weak")
    ]
    print(f"  {len(hardest)} matchups the wiki calls unfavourable, for example:")
    for name, opponent, rating in hardest[:6]:
        print(f"    {name:<14} struggles against {opponent:<14} ({rating})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
