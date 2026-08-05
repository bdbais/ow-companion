"""Fetches the current hero roster and every hero's wikitext.

    python tools/fetch_wiki.py

The roster comes from the OverFast API, which mirrors Blizzard's own hero list and is the
authority on who exists right now. Each hero is then matched to an Overwatch Wiki page,
which is where the numbers and the balance history live. Heroes whose wiki page cannot be
found are reported rather than silently dropped.
"""

from __future__ import annotations

import sys

from common import DATASET, RAW, overfast, wikitext, write_json

# Hero names that differ between Blizzard's roster and the wiki's page titles. Anything not
# listed here is looked up under its own name, with wiki redirects doing the rest.
PAGE_OVERRIDES: dict[str, str] = {}


def main(refresh: bool = False) -> int:
    heroes = overfast("/heroes", refresh=refresh)
    print(f"roster: {len(heroes)} heroes")

    pages_dir = RAW / "wiki"
    pages_dir.mkdir(parents=True, exist_ok=True)

    roster = []
    missing = []

    for hero in heroes:
        name = hero["name"]
        title = PAGE_OVERRIDES.get(name, name)
        text = wikitext(title, refresh=refresh)
        if text is None:
            missing.append(name)
            print(f"  MISSING wiki page for {name!r} (tried {title!r})")
            continue

        slug = hero["key"]
        (pages_dir / f"{slug}.wiki").write_text(text, encoding="utf-8")
        roster.append(
            {
                "key": slug,
                "name": name,
                "role": hero["role"],
                "portrait": hero.get("portrait"),
                "wikiTitle": title,
                "wikiBytes": len(text),
            }
        )
        print(f"  {name:<16} {slug:<16} {len(text):>7} bytes")

    write_json(DATASET / "roster.json", {"heroes": roster, "missing": missing})
    print(f"\nwrote {DATASET / 'roster.json'}: {len(roster)} pages, {len(missing)} missing")
    if missing:
        print("add a PAGE_OVERRIDES entry for each missing hero and re-run")
    return 1 if missing else 0


if __name__ == "__main__":
    sys.exit(main(refresh="--refresh" in sys.argv))
