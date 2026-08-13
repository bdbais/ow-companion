"""Fetches the current hero roster and every hero's wikitext.

    python tools/fetch_wiki.py

The roster comes from the OverFast API, which mirrors Blizzard's own hero list, plus anyone
the wiki has a hero page for that OverFast has not caught up with yet. That second source
matters: OverFast is a cache in front of Blizzard's site and took days to list a hero the
wiki had already documented in full, which meant the app could not show a new hero even
though every number it needed was already fetchable.

Each hero is then matched to an Overwatch Wiki page, which is where the numbers and the
balance history live. Heroes whose wiki page cannot be found are reported rather than
silently dropped.
"""

from __future__ import annotations

import re
import sys

from common import DATASET, RAW, overfast, wiki_api, wikitext, write_json

# How a hero page names the season it arrived in, when it does not name a date.
SEASON_LINK = re.compile(r"\[\[Season/(\d{4})#")

# Hero names that differ between Blizzard's roster and the wiki's page titles. Anything not
# listed here is looked up under its own name, with wiki redirects doing the rest.
PAGE_OVERRIDES: dict[str, str] = {}

# Subcategories of Category:Heroes that are not heroes.
NOT_HEROES = {
    "Example Videos",
    "Hero Selection Screens",
    "Hero Story pages",
    "Hero abilities",
    "Hero images",
    "Hero sound files",
    "Unreleased heroes",
}


def wiki_roster(refresh: bool = False) -> list[str]:
    """Every hero the wiki keeps a category for.

    The wiki files each hero under `Category:Heroes` as a subcategory of their own, so the
    subcategory names are the roster. Released heroes only: anything still to come lives
    under `Category:Unreleased heroes` instead, which is skipped along with the handful of
    housekeeping categories.
    """
    data = wiki_api(
        refresh=refresh,
        action="query",
        list="categorymembers",
        cmtitle="Category:Heroes",
        cmlimit="500",
        format="json",
    )
    members = data.get("query", {}).get("categorymembers", [])
    names = [m["title"].removeprefix("Category:") for m in members if m["title"].startswith("Category:")]
    return sorted(name for name in names if name not in NOT_HEROES)


def season_pages(pages_dir, texts: dict[str, str], refresh: bool = False) -> list[str]:
    """The season articles any hero points at, fetched so a date can be read off them.

    A hero page written the week the hero shipped says "released in Season 4: Heroes of
    Busan" and nothing more - the sentence with a date in it gets written later, once
    somebody gets round to it. The season article has the date from the start, because it
    is a date about the season rather than about the hero.

    So this follows the link. Only years actually referenced are fetched, which is one or
    two pages, and a year that cannot be had is skipped rather than fatal: a missing season
    page costs one release date, not the build.
    """
    years = sorted({m for text in texts.values() for m in SEASON_LINK.findall(text)})
    fetched = []
    for year in years:
        text = wikitext(f"Season/{year}", refresh=refresh)
        if text is None:
            print(f"  no Season/{year} page; heroes released then will have no date")
            continue
        (pages_dir / f"season-{year}.wiki").write_text(text, encoding="utf-8")
        fetched.append(year)
    return fetched


def main(refresh: bool = False) -> int:
    heroes = list(overfast("/heroes", refresh=refresh))
    print(f"roster: {len(heroes)} heroes from Blizzard")

    # Anyone the wiki documents and Blizzard's list has not reached yet. Their role is read
    # from the wiki page later; "damage" here is only a placeholder for the roster file, and
    # build_dataset overwrites it from the page's own infobox.
    known = {hero["name"] for hero in heroes}
    for name in wiki_roster(refresh=refresh):
        if name in known:
            continue
        print(f"  {name} is on the wiki but not yet in Blizzard's list; including it")
        heroes.append(
            {
                "name": name,
                "key": name.lower().replace(" ", "-").replace(".", "").replace(":", ""),
                "role": "damage",
                "portrait": None,
            }
        )

    pages_dir = RAW / "wiki"
    pages_dir.mkdir(parents=True, exist_ok=True)

    roster = []
    missing = []
    texts: dict[str, str] = {}

    for hero in heroes:
        name = hero["name"]
        title = PAGE_OVERRIDES.get(name, name)
        text = wikitext(title, refresh=refresh)
        if text is None:
            missing.append(name)
            print(f"  MISSING wiki page for {name!r} (tried {title!r})")
            continue

        slug = hero["key"]
        texts[slug] = text
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

    seasons = season_pages(pages_dir, texts, refresh=refresh)
    if seasons:
        print(f"\nseason pages: {', '.join(seasons)}")

    write_json(DATASET / "roster.json", {"heroes": roster, "missing": missing})
    print(f"\nwrote {DATASET / 'roster.json'}: {len(roster)} pages, {len(missing)} missing")
    if missing:
        print("add a PAGE_OVERRIDES entry for each missing hero and re-run")
    return 1 if missing else 0


if __name__ == "__main__":
    sys.exit(main(refresh="--refresh" in sys.argv))
