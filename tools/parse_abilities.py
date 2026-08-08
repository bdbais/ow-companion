"""Extracts the abilities that deal damage, with their cooldowns.

    python tools/parse_abilities.py

The damage chart covers what a hero holds the trigger on. That leaves out a hundred-odd
buttons that also kill people - Ana's grenade, Cassidy's flashbang, Moira's orb - and
without them the app cannot answer either "how much does this ability do" or "what does a
combo add up to".

An ability is not a weapon and the difference matters when ranking them. A gun sustains; an
ability fires once and then waits out a cooldown, so its comparable figure is damage divided
by that cooldown. Both are recorded.

The one thing this refuses to do is add numbers together. A damage field with several lines
is usually conditional - `10 per second (base)` and `20 per second (fan the flames)` are the
same ability in two states, not thirty damage - so the headline figure is always a number
the wiki states outright, and every line is kept so a reader can see the rest.
"""

from __future__ import annotations

import re
import sys

from common import DATASET, RAW, read_json, write_json
from wikitext import clean_value, find_templates, first_number, resolve_vars, strip_comments

TEMPLATE_NAMES = {"ability_details"}

# `{{tt|15|115 total}}` - the tooltip carries the figure for the whole cast, which is the
# one worth ranking. Read before clean_value throws the tooltip away.
TOTAL = re.compile(r"\{\{\s*tt\s*\|[^|{}]*\|\s*([\d.]+)\s*total", re.IGNORECASE)

# Lines describing what the ability does to the hero using it, which is not damage dealt.
SELF = re.compile(r"\bself\b|\bto you\b|\bown\b", re.IGNORECASE)

PER_SECOND = re.compile(r"([\d.]+)\s*per second", re.IGNORECASE)
OVER_SECONDS = re.compile(r"([\d.]+)\s*over\s*([\d.]+)\s*seconds?", re.IGNORECASE)

# `60 per second, up to 250` - a burn with a ceiling. The ceiling is what one cast is worth,
# and without reading it Moira's orb has a rate and no total at all.
UP_TO = re.compile(r"up to\s*\{*\s*([\d.]+)", re.IGNORECASE)


def lines_of(value: str) -> list[str]:
    return [line.strip() for line in clean_value(value).split("\n") if line.strip()]


def headline(raw: str) -> tuple[float | None, bool]:
    """The most damage one cast does to one enemy, and whether it needed a judgement call.

    Never a sum. Where the wiki states a total for the whole cast it is used; otherwise the
    largest single enemy-facing line is taken, which is a figure the wiki actually contains
    even when it is not the whole story.
    """
    total = TOTAL.search(raw)
    if total:
        return float(total.group(1)), False

    candidates: list[float] = []
    for line in lines_of(raw):
        if SELF.search(line):
            continue
        capped = UP_TO.search(line)
        if capped:
            candidates.append(float(capped.group(1)))
            continue
        # `100 over 5 seconds` is a total already; `25 per second` is a rate and only
        # becomes a total once something says how long it lasts.
        spread = OVER_SECONDS.search(line)
        if spread:
            candidates.append(float(spread.group(1)))
            continue
        if PER_SECOND.search(line):
            continue
        value = first_number(line)
        if value is not None:
            candidates.append(value)

    if not candidates:
        return None, True
    # More than one line and no stated total means the reader is being shown the biggest
    # component rather than the sum, which is worth flagging even though it is not wrong.
    return max(candidates), len(candidates) > 1


def sustained(raw: str) -> tuple[float, float] | None:
    """A rate and how long it runs, for abilities that burn rather than hit."""
    rate = PER_SECOND.search(clean_value(raw))
    if not rate:
        return None
    return float(rate.group(1)), 0.0


def parse_hero(key: str, name: str, text: str) -> list[dict]:
    text = resolve_vars(strip_comments(text))
    abilities = []

    for template in find_templates(text, TEMPLATE_NAMES):
        params = template["params"]
        ability_type = clean_value(params.get("ability_type", "")).lower()
        # Passives are not pressed, so they cannot be part of an opening. Junkrat's Total
        # Mayhem drops bombs when he dies, which is a hundred damage nobody chooses.
        if params.get("removed") or "weapon" in ability_type or "ultimate" in ability_type:
            continue
        if "passive" in ability_type:
            continue
        raw = params.get("damage", "")
        if not raw:
            continue

        ability_name = clean_value(params.get("ability_name", "")).strip()
        if not ability_name:
            continue

        damage, uncertain = headline(raw)
        cooldown = first_number(clean_value(params.get("cooldown", "")))
        cast = first_number(clean_value(params.get("cast_time", "")))
        rate = sustained(raw)

        abilities.append(
            {
                "hero": name,
                "name": ability_name,
                # `Major Perk` and `Minor Perk` are abilities you have to pick, and saying
                # so is the difference between a fact and a maybe.
                "kind": "perk" if "perk" in ability_type else "ability",
                "damage": damage,
                "damagePerSecond": rate[0] if rate else None,
                "cooldown": cooldown,
                "castTime": cast,
                # Everything the wiki says about the damage, so the breakdown a single
                # figure loses is still on the page.
                "lines": lines_of(raw),
                "uncertain": uncertain,
            }
        )
    return abilities


def main() -> int:
    roster = read_json(DATASET / "roster.json")["heroes"]
    everything = []
    for hero in roster:
        path = RAW / "wiki" / f"{hero['key']}.wiki"
        everything.extend(parse_hero(hero["key"], hero["name"], path.read_text(encoding="utf-8")))

    write_json(DATASET / "abilities-parsed.json", {"abilities": everything})

    with_damage = [a for a in everything if a["damage"] is not None]
    with_cooldown = [a for a in with_damage if a["cooldown"]]
    flagged = [a for a in with_damage if a["uncertain"]]
    print(f"parsed {len(everything)} damaging abilities")
    print(f"  {len(with_damage)} with a headline figure, {len(with_cooldown)} of those on a cooldown")
    print(f"  {len(flagged)} show the biggest of several lines rather than a stated total")

    ranked = sorted(
        (a for a in with_cooldown),
        key=lambda a: -(a["damage"] / a["cooldown"]),
    )[:8]
    print("  most damage per second of cooldown:")
    for a in ranked:
        rate = a["damage"] / a["cooldown"]
        print(f"    {a['hero']:<13} {a['name']:<24} {a['damage']:>6.0f} / {a['cooldown']:>4.1f}s = {rate:5.1f}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
