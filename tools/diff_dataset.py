"""Compare two generated datasets and describe the change in words.

A JSON diff of a 2.7 MB file tells you nothing: reordered keys and rounding noise bury the
one number that actually moved. This reports the change the way a patch note would, so a
regeneration can be reviewed before it reaches the app rather than trusted blindly.

    python tools/diff_dataset.py old.json new.json [--markdown]

Exit code is 0 whether or not anything changed; the caller decides what that means.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

# Fields worth reporting on. Anything else in a weapon record is either derived from these
# (dpsPeriodAdd) or descriptive (mousebutton), and reporting it would only add noise.
WEAPON_FIELDS = ("fireRate", "ammo", "reloadTime", "spread", "velocity", "pellets", "critFactor")


def load(path: Path) -> dict:
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def index(rows: list[dict]) -> dict[tuple[str, str, str], dict]:
    """Key by kind as well as by name.

    A hero can list the same name twice - Zenyatta's Transcendence is both an ultimate and,
    until it was fixed, a healing source. Keying on the name alone let one silently overwrite
    the other, so removing a stray duplicate showed up as no change at all.
    """
    indexed: dict[tuple[str, str, str], dict] = {}
    for row in rows:
        key = (row.get("hero", ""), row.get("name", ""), row.get("kind", ""))
        # Still ambiguous? Number the collisions rather than dropping them.
        if key in indexed:
            suffix = 2
            while (*key[:2], f"{key[2]}#{suffix}") in indexed:
                suffix += 1
            key = (*key[:2], f"{key[2]}#{suffix}")
        indexed[key] = row
    return indexed


def number(value) -> str:
    if isinstance(value, float):
        return f"{value:g}"
    if isinstance(value, list):
        return "[" + ", ".join(number(v) for v in value) + "]"
    return str(value)


def changed(old, new) -> bool:
    """Floats that differ only in the last bit are not a balance change."""
    if isinstance(old, float) and isinstance(new, float):
        return abs(old - new) > 1e-9
    if isinstance(old, list) and isinstance(new, list) and len(old) == len(new):
        return any(changed(a, b) for a, b in zip(old, new))
    return old != new


def damage_of(row: dict) -> list | None:
    damage = row.get("damage")
    if isinstance(damage, dict):
        return damage.get("dpshot")
    return damage


def compare_rows(old: list[dict], new: list[dict], label: str, lines: list[str]) -> None:
    before, after = index(old), index(new)

    for key in sorted(after.keys() - before.keys()):
        lines.append(f"+ {label}: {key[0]} - {key[1]}")
    for key in sorted(before.keys() - after.keys()):
        lines.append(f"- {label}: {key[0]} - {key[1]} (gone)")

    for key in sorted(before.keys() & after.keys()):
        was, now = before[key], after[key]
        hero, name = key[0], key[1]

        old_damage, new_damage = damage_of(was), damage_of(now)
        if changed(old_damage, new_damage):
            lines.append(
                f"~ {hero} - {name}: damage {number(old_damage)} -> {number(new_damage)}"
            )

        for field in WEAPON_FIELDS + ("healPerSecond", "healPerShot"):
            if field in was or field in now:
                a, b = was.get(field), now.get(field)
                if changed(a, b):
                    lines.append(f"~ {hero} - {name}: {field} {number(a)} -> {number(b)}")


def compare(old: dict, new: dict) -> list[str]:
    lines: list[str] = []

    old_heroes = {h["name"] for h in old.get("heroes", [])}
    new_heroes = {h["name"] for h in new.get("heroes", [])}
    for hero in sorted(new_heroes - old_heroes):
        lines.append(f"+ hero: {hero}")
    for hero in sorted(old_heroes - new_heroes):
        lines.append(f"- hero: {hero} (gone)")

    for hero in sorted(old_heroes & new_heroes):
        was = next(h for h in old["heroes"] if h["name"] == hero)
        now = next(h for h in new["heroes"] if h["name"] == hero)
        for field in ("health", "armor", "shields", "role"):
            if changed(was.get(field), now.get(field)):
                lines.append(
                    f"~ {hero}: {field} {number(was.get(field))} -> {number(now.get(field))}"
                )

    compare_rows(old.get("weapons", []), new.get("weapons", []), "weapon", lines)
    compare_rows(old.get("ultimates", []), new.get("ultimates", []), "ultimate", lines)
    compare_rows(old.get("healing", []), new.get("healing", []), "healing", lines)

    def patch_count(data: dict) -> int:
        return sum(len(hero.get("patches", [])) for hero in data.get("wiki", []))

    before, after = patch_count(old), patch_count(new)
    if after != before:
        lines.append(f"~ patch notes: {before} -> {after} ({after - before:+d})")

    return lines


def main() -> int:
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    markdown = "--markdown" in sys.argv
    if len(args) != 2:
        print(__doc__)
        return 2

    lines = compare(load(Path(args[0])), load(Path(args[1])))

    if not lines:
        print("No change." if not markdown else "The regenerated dataset is identical.")
        return 0

    if markdown:
        print(f"{len(lines)} change(s) found by `tools/diff_dataset.py`:\n")
        print("```")
        print("\n".join(lines))
        print("```")
    else:
        print("\n".join(lines))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
