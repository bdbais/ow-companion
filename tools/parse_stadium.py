"""Extracts the Stadium item catalogue.

    python tools/parse_stadium.py

The wiki records these in a genuinely machine-readable way - `stadium_buffs = Weapon
Power;;5%`, a rarity and a cash cost per item - which is what makes a build optimiser
possible at all.

Only some of those buffs mean anything to the damage simulation: Weapon Power scales damage
and Attack Speed scales the firing cycle, both of which the engine already models. Ability
Power, cooldown reduction and lifesteal are recorded but flagged as unsimulated, because
pretending to optimise for something the model does not compute would be worse than saying
so.
"""

from __future__ import annotations

import re
import sys

from common import DATASET, wikitext, write_json
from wikitext import clean_value, find_templates, first_number, split_params, strip_comments

ITEM_PAGE = "Stadium/Items"

# Buffs the damage model can actually act on.
SIMULATED_BUFFS = {
    "weapon power": "weaponPower",
    "attack speed": "attackSpeed",
}


def parse_buffs(raw: str) -> list[dict]:
    """`Weapon Power;;5%` and friends, including several separated by line breaks."""
    buffs = []
    for line in clean_value(raw).split("\n"):
        line = line.strip()
        if not line:
            continue
        parts = [p.strip() for p in line.split(";;")]
        if not parts or not parts[0]:
            continue
        stat = parts[0]
        value = first_number(parts[1]) if len(parts) > 1 else None
        buffs.append(
            {
                "stat": stat,
                "value": value,
                "percent": "%" in (parts[1] if len(parts) > 1 else ""),
                "simulated": SIMULATED_BUFFS.get(stat.lower()),
            }
        )
    return buffs


def main() -> int:
    text = wikitext(ITEM_PAGE)
    if text is None:
        print(f"could not fetch {ITEM_PAGE}")
        return 1
    text = strip_comments(text)

    items = []
    for template in find_templates(text, {"ability_details"}):
        params = template["params"]
        name = clean_value(params.get("ability_name", "")).strip()
        rarity = clean_value(params.get("stadium_rarity", "")).strip()
        if not name or not rarity:
            continue

        kind = [p.strip() for p in clean_value(params.get("ability_type", "")).split(";;")]
        buffs = parse_buffs(params.get("stadium_buffs", ""))
        items.append(
            {
                "name": name,
                "hero": clean_value(params.get("hero_name", "")).strip() or "All heroes",
                "category": kind[1] if len(kind) > 1 else (kind[0] if kind else ""),
                "rarity": rarity,
                "cost": first_number(clean_value(params.get("stadium_cost", ""))),
                "buffs": buffs,
                "description": clean_value(params.get("official_description", "")).strip(),
            }
        )

    simulated = sum(1 for i in items for b in i["buffs"] if b["simulated"])
    write_json(DATASET / "stadium-parsed.json", {"items": items})

    print(f"parsed {len(items)} Stadium items")
    by_category: dict[str, int] = {}
    for item in items:
        by_category[item["category"]] = by_category.get(item["category"], 0) + 1
    for category, count in sorted(by_category.items(), key=lambda kv: -kv[1]):
        print(f"   {category or '(none)':<12} {count}")
    print(f"   {simulated} buffs the damage model can act on")

    unsimulated = sorted(
        {b["stat"] for i in items for b in i["buffs"] if not b["simulated"]}
    )
    print(f"   {len(unsimulated)} buff types recorded but not simulated:")
    for stat in unsimulated:
        print(f"      {stat}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
