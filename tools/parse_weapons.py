"""Turns each hero's wiki ability boxes into weapon specs the engine can simulate.

    python tools/parse_weapons.py

The wiki is written for people, not for parsers: the same field holds `70 - 21`, `5.75 -
1.725 per pellet`, `15 per second (damage over time)` or a tooltip wrapping all three. So
this makes no attempt to be clever about the awkward cases. It extracts what is
unambiguous, and records everything else - with the raw wikitext - in a review list, so a
human decides rather than a regex guessing.

Nothing here overwrites a hand-made correction: `dataset/overrides.json` is merged on top
by build_dataset.py, so review work survives re-running the pipeline.
"""

from __future__ import annotations

import re
import sys

from common import DATASET, RAW, read_json, write_json
from wikitext import (
    clean_value,
    find_templates,
    first_number,
    normalise_dashes,
    number_pair,
    resolve_vars,
    strip_comments,
)

TEMPLATE_NAMES = {"ability_details"}

# Section the weapons live in. Abilities and ultimates are parsed separately for the wiki
# screen; only what a hero holds down the trigger on belongs in the damage chart.
WEAPON_SECTION = re.compile(r"^===\s*Weapons\s*===\s*$", re.MULTILINE)
NEXT_SECTION = re.compile(r"^===[^=]", re.MULTILINE)


class Review:
    """Collects everything a person needs to look at, grouped by hero and weapon."""

    def __init__(self) -> None:
        self.items: list[dict] = []

    def add(self, hero: str, weapon: str, field: str, reason: str, raw: str = "") -> None:
        self.items.append(
            {
                "hero": hero,
                "weapon": weapon,
                "field": field,
                "reason": reason,
                "raw": raw.strip()[:400],
            }
        )


def weapon_section(text: str) -> str:
    match = WEAPON_SECTION.search(text)
    if not match:
        return ""
    rest = text[match.end() :]
    following = NEXT_SECTION.search(rest)
    return rest[: following.start()] if following else rest


def lines_of(value: str) -> list[str]:
    return [line.strip() for line in clean_value(value).split("\n") if line.strip()]


def parse_damage(raw: str, review_add) -> tuple[list[float] | None, bool]:
    """Per-pellet damage, and whether the value needed a judgement call."""
    lines = lines_of(raw)
    if not lines:
        return None, True

    # When a shotgun states both, the per-pellet figure is the one the engine wants: it
    # multiplies by the pellet count itself.
    chosen = next((line for line in lines if "per pellet" in line.lower()), lines[0])

    lowered = chosen.lower()
    if "per second" in lowered or "over time" in lowered:
        review_add("damage", "damage is stated as a rate, not per shot", chosen)
        return None, True

    pair = number_pair(chosen)
    if pair:
        return pair, len(lines) > 1
    single = first_number(chosen)
    if single is None:
        review_add("damage", "no number found", chosen)
        return None, True
    return [single], len(lines) > 1


def parse_spread(raw: str, review_add) -> dict | None:
    lines = lines_of(raw)
    if not lines:
        return None
    if len(lines) == 1 and "none" in lines[0].lower():
        return None

    angles: dict[str, float] = {}
    for line in lines:
        value = first_number(line)
        if value is None:
            continue
        lowered = line.lower()
        if "(max" in lowered:
            angles["max"] = value
        elif "(min" in lowered:
            angles["min"] = value
        elif "(base" in lowered:
            angles.setdefault("min", value)
        else:
            angles.setdefault("flat", value)

    if "max" in angles and "min" in angles:
        # The wiki says how wide the cone gets but not over how many rounds it blooms, so
        # the ammo range has to come from a person.
        review_add("spread", "blooming spread needs a spreadingAmmoRange", " / ".join(lines))
        return {"maxAngle": angles["max"], "minAngle": angles["min"]}
    if "flat" in angles:
        if len(lines) > 1:
            review_add("spread", "several spread values, took the first", " / ".join(lines))
        return {"angle": angles["flat"]}
    if "max" in angles:
        return {"angle": angles["max"]}
    return None


def parse_shot_type(params: dict, pellets: float, review_add) -> tuple[str, bool]:
    shot_type = clean_value(params.get("shot_type", "")).lower()
    name = clean_value(params.get("ability_name", "")).lower()

    if "beam" in shot_type or "beam" in name:
        return "beam", True
    if "melee" in shot_type:
        return "melee", False

    hitscan = "hitscan" in shot_type
    base = "hitscan" if hitscan else "linear projectile"
    if pellets > 1:
        return (f"{base} shotgun" if hitscan else "projectile shotgun"), False
    if not hitscan and not shot_type:
        review_add("type", "no shot type given, assumed a linear projectile", "")
    return base, False


def parse_hero(key: str, name: str, text: str, review: Review) -> list[dict]:
    text = resolve_vars(strip_comments(text))
    section = weapon_section(text)
    if not section:
        review.add(name, "", "weapons", "no ===Weapons=== section on the page")
        return []

    weapons = []
    for template in find_templates(section, TEMPLATE_NAMES):
        params = template["params"]
        weapon_name = clean_value(params.get("ability_name", "")).strip()
        if not weapon_name:
            continue

        def review_add(field: str, reason: str, raw: str = "", _w=weapon_name) -> None:
            review.add(name, _w, field, reason, raw)

        ability_type = clean_value(params.get("ability_type", "")).lower()
        if "weapon" not in ability_type:
            continue

        pellets = first_number(params.get("pellets", "")) or 1.0
        damage, damage_uncertain = parse_damage(params.get("damage", ""), review_add)
        weapon_type, is_beam = parse_shot_type(params, pellets, review_add)

        fire_rate = first_number(clean_value(params.get("fire_rate", "")))
        ammo = first_number(clean_value(params.get("ammo", "")))
        ammo_drain = first_number(clean_value(params.get("ammo_drain", ""))) or 1.0
        reload_time = first_number(clean_value(params.get("reload_time", "")))
        velocity = first_number(clean_value(params.get("pspeed", "")))
        headshot = clean_value(params.get("headshot", "")).strip().lower()

        falloff_raw = params.get("damage_falloff_range", "")
        falloff_lines = lines_of(falloff_raw)
        falloff = number_pair(falloff_lines[0]) if falloff_lines else None
        if falloff_lines and len(falloff_lines) > 1:
            review_add(
                "falloff",
                "falloff differs by condition, took the first",
                " / ".join(falloff_lines),
            )
        if damage and len(damage) == 2 and not falloff:
            review_add(
                "falloff",
                "damage is a near/far pair but no falloff range was found",
                normalise_dashes(clean_value(falloff_raw)),
            )

        spread = parse_spread(params.get("spread", ""), review_add)

        crit_factor = 1.0 if headshot.startswith(("✕", "no", "n/a", "x")) else 2.0

        # Magazine in shots, not in rounds: weapons that spend more than one round per shot
        # get fewer shots out of the same number.
        magazine = (ammo / ammo_drain) if ammo else None

        if is_beam:
            # Beams need a tick rate and an ammo drain per second to be normalised into
            # per-tick damage, and the wiki does not state them in a machine-readable way.
            review_add("beam", "beam weapons need their tick rate filled in by hand", "")

        missing = [
            field
            for field, value in (
                ("damage", damage),
                ("fireRate", fire_rate),
            )
            if value is None
        ]
        if missing:
            review_add("required", f"missing {', '.join(missing)}", "")

        shot_time = (1.0 / fire_rate) if fire_rate else None
        weapons.append(
            {
                "name": weapon_name,
                "hero": name,
                "mousebutton": mousebutton_of(params),
                "type": weapon_type,
                "behavior": "standard",
                "pellets": [pellets],
                "damage": {
                    "dpshot": damage,
                    "falloff": falloff,
                },
                "spread": spread,
                "velocity": velocity,
                "fireRate": fire_rate,
                "shotTime": shot_time,
                "ammo": magazine,
                "reloadTime": reload_time,
                "critFactor": crit_factor,
                "dpsPeriodBase": shot_time,
                "dpsPeriodAdd": (
                    (reload_time / magazine) if (reload_time and magazine) else 0.0
                ),
                "complete": not missing and not is_beam,
            }
        )

    if not weapons:
        review.add(name, "", "weapons", "no weapon templates matched in the section")
    return weapons


def mousebutton_of(params: dict) -> str | None:
    key = clean_value(params.get("key", "")).lower()
    ability_type = clean_value(params.get("ability_type", "")).lower()
    if "secondary" in key or "secondary" in ability_type:
        return "M2"
    if "primary" in key or "primary" in ability_type:
        return "M1"
    if "ads" in ability_type or "zoom" in ability_type:
        return "ADS"
    return None


ULTIMATE_SECTION = re.compile(r"^===\s*Ultimate Ability\s*===\s*$", re.MULTILINE)


def ultimate_section(text: str) -> str:
    match = ULTIMATE_SECTION.search(text)
    if not match:
        return ""
    rest = text[match.end() :]
    following = re.search(r"^==[^=]|^===[^=]", rest, re.MULTILINE)
    return rest[: following.start()] if following else rest


def parse_ultimate(hero_name: str, text: str) -> dict | None:
    """An ultimate's headline damage, for a ranking of its own.

    Ultimates are burst rather than sustained fire, so what is worth recording is what one
    cast can do, not a rate. Where the wiki lists several figures - a centre and an area of
    effect, a per-second figure over a duration - the largest single number is taken and the
    rest are kept as text so a reader can see what was left out.
    """
    text = resolve_vars(strip_comments(text))
    section = ultimate_section(text)
    if not section:
        return None

    for template in find_templates(section, TEMPLATE_NAMES):
        params = template["params"]
        name = clean_value(params.get("ability_name", "")).strip()
        if not name:
            continue

        lines = lines_of(params.get("damage", ""))
        best = None
        for line in lines:
            value = first_number(line)
            if value is None:
                continue
            # "150 per second (during warning)" over a stated duration is a total, not a rate.
            if "per second" in line.lower():
                duration = first_number(clean_value(params.get("duration", "")))
                if duration:
                    value *= duration
            if best is None or value > best:
                best = value

        return {
            "hero": hero_name,
            "name": name,
            "damage": best,
            "detail": " / ".join(lines) if lines else None,
            "duration": first_number(clean_value(params.get("duration", ""))),
            "radius": first_number(clean_value(params.get("radius", ""))),
            "description": clean_value(params.get("official_description", "")).strip(),
        }
    return None


def parse_healing(hero_name: str, text: str) -> list[dict]:
    """Healing output, from the same ability boxes as the weapons.

    Healing does not need the hitbox simulation: a heal lands on an ally who is not dodging,
    so its rate is arithmetic - per shot, times pellets, over the firing cycle.
    """
    text = resolve_vars(strip_comments(text))
    results = []
    for section in (weapon_section(text), ultimate_section(text)):
        if not section:
            continue
        for template in find_templates(section, TEMPLATE_NAMES):
            params = template["params"]
            raw = params.get("heal") or params.get("healing")
            if not raw:
                continue
            lines = lines_of(raw)
            value = first_number(lines[0]) if lines else None
            if value is None:
                continue

            per_second = "per second" in lines[0].lower()
            fire_rate = first_number(clean_value(params.get("fire_rate", "")))
            ammo = first_number(clean_value(params.get("ammo", "")))
            reload_time = first_number(clean_value(params.get("reload_time", "")))
            results.append(
                {
                    "hero": hero_name,
                    "name": clean_value(params.get("ability_name", "")).strip(),
                    "healPerShot": None if per_second else value,
                    "healPerSecond": value if per_second else None,
                    "fireRate": fire_rate,
                    "ammo": ammo,
                    "reloadTime": reload_time,
                    "detail": " / ".join(lines),
                }
            )
    return results


def main() -> int:
    roster = read_json(DATASET / "roster.json")["heroes"]
    review = Review()
    all_weapons = []

    ultimates = []
    healing = []

    for hero in roster:
        path = RAW / "wiki" / f"{hero['key']}.wiki"
        text = path.read_text(encoding="utf-8")
        weapons = parse_hero(hero["key"], hero["name"], text, review)
        all_weapons.extend(weapons)

        ultimate = parse_ultimate(hero["name"], text)
        if ultimate:
            ultimates.append(ultimate)
        healing.extend(parse_healing(hero["name"], text))

    complete = [w for w in all_weapons if w["complete"]]
    write_json(DATASET / "weapons-parsed.json", {"weapons": all_weapons})
    write_json(DATASET / "ultimates-parsed.json", {"ultimates": ultimates})
    write_json(DATASET / "healing-parsed.json", {"healing": healing})
    write_json(DATASET / "review-weapons.json", {"items": review.items})

    with_damage = sum(1 for u in ultimates if u.get("damage"))
    print(f"parsed {len(ultimates)} ultimates ({with_damage} deal damage)")
    print(f"parsed {len(healing)} healing sources")
    print(f"parsed {len(all_weapons)} weapons across {len(roster)} heroes")
    print(f"  {len(complete)} complete, {len(all_weapons) - len(complete)} need review")
    print(f"  {len(review.items)} review notes")

    by_reason: dict[str, int] = {}
    for item in review.items:
        by_reason[item["reason"]] = by_reason.get(item["reason"], 0) + 1
    for reason, count in sorted(by_reason.items(), key=lambda kv: -kv[1]):
        print(f"    {count:>3}  {reason}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
