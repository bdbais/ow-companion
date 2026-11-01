"""Decides whether a regenerated dataset can be published without anyone looking at it.

    python tools/check_refresh.py before.json after.json

The daily refresh publishes on its own, so something has to stand where the reviewer used
to. This is that something, and it is deliberately narrow: it does not judge whether a
number is right - the wiki is the authority on that - only whether the regeneration looks
like the parsers broke. A parser that breaks does not produce a slightly wrong damage
figure; it loses things. A hero disappears, a weapon table comes back empty, a patch log
stops being read. Those are the shapes checked here.

Exit 0 means publish. Exit 1 prints the reasons, and the workflow opens a pull request
instead, so a human sees the change before any phone does.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

# A rework can retire a weapon or two in one patch (Roadhog lost Scrap Gun Alt Fire in
# October). More than that in a single day is a parser, not a patch.
MAX_WEAPONS_LOST = 2


def load(path: str) -> dict:
    return json.loads(Path(path).read_text(encoding="utf-8"))


def problems(old: dict, new: dict) -> list[str]:
    found: list[str] = []

    old_heroes = {h["name"] for h in old.get("heroes", [])}
    new_heroes = {h["name"] for h in new.get("heroes", [])}
    for hero in sorted(old_heroes - new_heroes):
        found.append(f"hero gone: {hero}")

    for hero in new.get("heroes", []):
        if not hero.get("health"):
            found.append(f"no health read for {hero['name']}")

    def weapons(data: dict) -> set[tuple[str, str]]:
        return {(w.get("hero", ""), w.get("name", "")) for w in data.get("weapons", [])}

    lost = weapons(old) - weapons(new)
    if len(lost) > MAX_WEAPONS_LOST:
        found.append(f"{len(lost)} weapons gone at once (more than {MAX_WEAPONS_LOST})")

    armed_before = {hero for hero, _ in weapons(old)}
    armed_now = {hero for hero, _ in weapons(new)}
    for hero in sorted((armed_before - armed_now) & new_heroes):
        found.append(f"every weapon gone for {hero}")

    def patches(data: dict) -> int:
        return sum(len(hero.get("patches", [])) for hero in data.get("wiki", []))

    if patches(new) < patches(old):
        found.append(f"patch notes went down: {patches(old)} -> {patches(new)}")

    return found


# What the checks must catch and must let through, run on every invocation so a careless
# edit to them shows up as a refusal to run rather than as a dataset published unchecked.
def _self_check() -> None:
    hero = {"name": "Ana", "health": 250}
    base = {
        "heroes": [hero, {"name": "Mei", "health": 300}],
        "weapons": [{"hero": "Ana", "name": "Rifle"}, {"hero": "Mei", "name": "Blaster"}],
        "wiki": [{"patches": [1, 2, 3]}],
    }
    assert problems(base, base) == []
    added = dict(base, heroes=base["heroes"] + [{"name": "Doctrine", "health": 250}])
    assert problems(base, added) == []
    assert problems(base, dict(base, heroes=[hero])) != []
    assert problems(base, dict(base, weapons=base["weapons"][:1])) != []
    assert problems(base, dict(base, wiki=[{"patches": [1]}])) != []
    assert problems(base, dict(base, heroes=[hero, {"name": "Mei", "health": 0}])) != []


def main() -> int:
    _self_check()
    if len(sys.argv) != 3:
        print(__doc__)
        return 2
    found = problems(load(sys.argv[1]), load(sys.argv[2]))
    if not found:
        print("Nothing looks like a broken parser: safe to publish.")
        return 0
    print("Not publishing on its own:")
    for line in found:
        print(f"  - {line}")
    return 1


if __name__ == "__main__":
    sys.exit(main())
