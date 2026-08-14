"""Collects the game's own translations of every name the app shows.

    python tools/fetch_names.py

The interface has been translated into fifteen languages for a while; the *content* has
not. A Korean player got a Korean app wrapped around "Biotic Rifle", because the dataset is
built from the English wiki and there is only one of those.

Blizzard publish the roster in every language they support, and OverFast passes a locale
straight through, so the translations exist and are official - the same provenance as the
portraits the app already ships. What comes back is not only names: ability, perk and
Stadium power descriptions are translated too, which is most of a hero page.

Five of the app's languages are not among the ones Blizzard publish. Those are left to
`dataset/names-contributed.json`, which is merged over the top of this and is where a
volunteer's work goes. Nothing here overwrites a contributed string.

Writes dataset/names.json and the copy the app ships.
"""

from __future__ import annotations

import json
import sys

from common import DATASET, ROOT, overfast, read_json, write_json

APP_ASSETS = ROOT / "app" / "src" / "main" / "assets"
CONTRIBUTED = DATASET / "names-contributed.json"

# The app's language tag -> the locale Blizzard publish under.
#
# Chinese (Simplified), Ukrainian, Swedish, Arabic and Turkish are absent on purpose: the
# API rejects them outright, which means Blizzard do not publish the game in them. Those
# five are what the contributed file is for.
LOCALES = {
    "it": "it-it",
    "es": "es-es",
    "pt": "pt-br",
    "fr": "fr-fr",
    "de": "de-de",
    "ja": "ja-jp",
    "ko": "ko-kr",
    "zhTW": "zh-tw",
    "ru": "ru-ru",
    "pl": "pl-pl",
}


def pairs(english: dict, other: dict) -> dict[str, str]:
    """Every name and description in one hero, English on the left, translated on the right.

    Matched by position rather than by key, because the lists come back in the same order
    in every language and the English name is the only identifier either side has. A hero
    whose lists have gone out of step is skipped rather than mismatched: a wrong translation
    is worse than none, and this is the one place the mistake would be silent.
    """
    found: dict[str, str] = {}

    def add(left: str | None, right: str | None) -> None:
        if left and right and left != right:
            found[left] = right

    # The hero's name is kept even when it does not change, which the rest are not.
    #
    # It is the one string other sources shout back at the app: Blizzard's rates table
    # writes "D.MON" where everything else writes "D.Mon", and the lookup is what puts that
    # right - but only if the name is in the map to be found. Fifty-three identities per
    # language cost nothing against eighteen thousand real translations.
    name = english.get("name")
    if name:
        found[name] = other.get("name") or name

    # The sentence under the portrait and where they are from. Role and sub-role are machine
    # keys that come back in English whatever locale is asked for, and the app has its own
    # translations for those already.
    for field in ("description", "location"):
        add(english.get(field), other.get(field))

    for field in ("abilities", "stadium_powers"):
        mine, theirs = english.get(field) or [], other.get(field) or []
        if len(mine) != len(theirs):
            continue
        for a, b in zip(mine, theirs):
            add(a.get("name"), b.get("name"))
            add(a.get("description"), b.get("description"))

    mine, theirs = english.get("perks") or {}, other.get("perks") or {}
    for tier in ("minor", "major"):
        left, right = mine.get(tier) or [], theirs.get(tier) or []
        if len(left) != len(right):
            continue
        for a, b in zip(left, right):
            add(a.get("name"), b.get("name"))
            add(a.get("description"), b.get("description"))

    return found


def main(refresh: bool = False) -> int:
    roster = read_json(DATASET / "roster.json")["heroes"]
    keys = [hero["key"] for hero in roster]
    print(f"{len(keys)} heroes x {len(LOCALES)} languages")

    english = {}
    for key in keys:
        try:
            english[key] = overfast(f"/heroes/{key}?locale=en-us", refresh=refresh)
        except Exception as error:  # noqa: BLE001 - one absent hero is not fatal
            print(f"  {key}: no English page ({error})")

    names: dict[str, dict[str, str]] = {}
    for language, locale in LOCALES.items():
        found: dict[str, str] = {}
        misses = 0
        for key, base in english.items():
            try:
                other = overfast(f"/heroes/{key}?locale={locale}", refresh=refresh)
            except Exception:  # noqa: BLE001 - a missing hero costs that hero, not the run
                misses += 1
                continue
            found.update(pairs(base, other))
        names[language] = found
        note = f", {misses} heroes unavailable" if misses else ""
        print(f"  {language:5} {locale:6} {len(found):5} strings{note}")

    contributed = read_json(CONTRIBUTED) if CONTRIBUTED.exists() else {}
    for language, entries in contributed.items():
        if not isinstance(entries, dict):
            continue
        # Contributed wins: somebody who speaks the language chose these deliberately.
        merged = dict(names.get(language, {}))
        merged.update({k: v for k, v in entries.items() if isinstance(v, str) and v})
        names[language] = merged
        print(f"  {language:5} +{len(entries):5} contributed")

    write_json(DATASET / "names.json", names)
    APP_ASSETS.mkdir(parents=True, exist_ok=True)
    # Written compactly: this ships inside the APK and the indentation is a third of it.
    (APP_ASSETS / "names.json").write_text(
        json.dumps(names, ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8",
    )

    size = (APP_ASSETS / "names.json").stat().st_size / 1024
    total = sum(len(v) for v in names.values())
    print(f"\nnames: {total} strings across {len(names)} languages, {size:.0f} KB")
    return 0


if __name__ == "__main__":
    sys.exit(main(refresh="--refresh" in sys.argv))
