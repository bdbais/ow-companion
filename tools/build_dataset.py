"""Merges every pipeline output into the dataset the app ships, plus a review report.

    python tools/build_dataset.py

Order of authority, lowest to highest:
  1. what the parsers extracted from the wiki
  2. dataset/overrides.json - hand corrections, which always win

Overrides exist because a chunk of the wiki's numbers cannot be read mechanically: beams
state a rate rather than a per-tick damage, blooming spread never says over how many rounds
it blooms, and some heroes' damage is conditional prose. Rather than let a regex guess and
quietly ship a wrong number, those land in review.md and are corrected by hand once.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

from common import DATASET, RAW, ROOT, read_json, write_json
from fetch_media import KNOWN_COLOURS

APP_ASSETS = ROOT / "app" / "src" / "main" / "assets"
APP_IMAGES = ROOT / "app" / "src" / "main" / "assets" / "heroes"

DATE = r"(\d{1,2}\s+[A-Z][a-z]+\s+\d{4}|[A-Z][a-z]+\s+\d{1,2},?\s+\d{4})"
# The sentence runs from "released" to the end of the paragraph. Citations and wiki links
# are stripped first: they are full of full stops and dates of their own, and either will
# derail the search.
# The release date always lives in the article's opening paragraph, but the wording does
# not: heroes are variously "released on", "included in the game's release on" or
# "initially playable during ... on". Anchoring on the paragraph rather than on a verb is
# the only thing that covers all of them.
INTRO = re.compile(r"\{\{HeroTabs\}\}(.*?)(?:\n\s*\n|\n==)", re.DOTALL)
DATE_RE = re.compile(DATE)
HERO_ORDER = re.compile(r"(\d+)(?:st|nd|rd|th)\s+hero")

# Heroes who arrived in a beta before the game they belong to shipped. The date that counts
# is when they became permanently playable, which is always the later one.
BETA_FIRST = re.compile(r"permanently available|full game launched", re.IGNORECASE)

MONTHS = {
    m: i
    for i, m in enumerate(
        "January February March April May June July August September October "
        "November December".split(),
        start=1,
    )
}


def iso_date(text: str) -> str | None:
    text = text.strip().rstrip(",")
    match = re.match(r"(\d{1,2})\s+([A-Z][a-z]+)\s+(\d{4})", text)
    if match:
        day, month, year = match.groups()
    else:
        match = re.match(r"([A-Z][a-z]+)\s+(\d{1,2}),?\s+(\d{4})", text)
        if not match:
            return None
        month, day, year = match.groups()
    number = MONTHS.get(month)
    return f"{year}-{number:02d}-{int(day):02d}" if number else None


# A hero page that names a season instead of a date, and the heading that answers it.
SEASON_REF = re.compile(r"\[\[Season/(\d{4})#Season (\d+)")
# Matched as a whole line rather than with one expression that also reaches the date: the
# heading carries an anchor span, and `class="anchor"` puts equals signs inside a heading
# whose own delimiter is equals signs. Anything clever enough to span both stops there.
SEASON_HEADING = re.compile(r"^=+\s*Season (\d+)\b[^\n]*$", re.MULTILINE)


def season_start(year: str, number: str) -> str | None:
    """When a numbered season began, off the wiki's own season article.

    Heroes shipped in the last week or two have a page that says which season they arrived
    in and no more; the date sentence is written later. The season article carries the date
    from the day it is created, so following the link is the difference between a hero
    having a release date and not having one for a month.

    Reads only what fetch_wiki cached. No page, no date, no complaint - the caller already
    reports heroes whose date could not be found.
    """
    page = RAW / "wiki" / f"season-{year}.wiki"
    if not page.exists():
        return None

    text = page.read_text(encoding="utf-8")
    for heading in SEASON_HEADING.finditer(text):
        if heading.group(1) != number:
            continue
        # The range follows the heading, sometimes past a roadmap image: "(11 August 2026 -
        # October 2026)". The first date in it is the start, and that is the one wanted.
        window = text[heading.end() : heading.end() + 400]
        opening = window.find("(")
        if opening < 0:
            return None
        found = DATE_RE.search(window[opening : opening + 60])
        return iso_date(found.group(0)) if found else None
    return None


def release_info(wiki_text: str) -> tuple[str | None, int | None]:
    head = wiki_text[:9000]
    # Self-closing refs first: `<ref[^>]*>` also matches `<ref name="x"/>`, so stripping
    # paired refs first would treat one as an opening tag and swallow everything up to the
    # next `</ref>` - including the sentence with the release date in it.
    head = re.sub(r"<ref[^>]*/>", "", head)
    head = re.sub(r"<ref[^>]*>.*?</ref>", "", head, flags=re.DOTALL)
    head = re.sub(r"\[\[[^\]|]*\|([^\]]*)\]\]", r"\1", head)
    head = re.sub(r"\[\[([^\]]*)\]\]", r"\1", head)

    intro_match = INTRO.search(head)
    intro = intro_match.group(1) if intro_match else head[:1500]
    dates = DATE_RE.findall(intro)
    date = iso_date(dates[-1] if BETA_FIRST.search(intro) else dates[0]) if dates else None

    if date is None:
        # No date in the paragraph, but perhaps a season. Searched in the original text
        # rather than in `head`, whose links have already been flattened to their labels.
        reference = SEASON_REF.search(wiki_text[:9000])
        if reference:
            date = season_start(*reference.groups())

    order_match = HERO_ORDER.search(head)
    order = int(order_match.group(1)) if order_match else None
    return date, order


# Every hero in the game has the same quick melee, and none of their pages repeats it -
# it lives on the wiki's Melee page instead. Without generating it, filtering the chart by
# "melee" turns up only the three heroes who swing something as a primary weapon.
QUICK_MELEE = {
    "damage": 40.0,
    "range": 2.5,
    "cooldown": 1.0,
}


def quick_melee_for(hero_name: str) -> dict:
    return {
        "name": "Quick Melee",
        "hero": hero_name,
        "mousebutton": None,
        "type": "melee",
        "behavior": "standard",
        "pellets": [1.0],
        "damage": {
            "dpshot": [QUICK_MELEE["damage"]],
            "falloff": None,
            "maxRange": QUICK_MELEE["range"],
        },
        "spread": None,
        "velocity": None,
        "fireRate": 1.0 / QUICK_MELEE["cooldown"],
        "shotTime": QUICK_MELEE["cooldown"],
        "ammo": None,
        "reloadTime": 0.0,
        # Melee cannot land a critical hit, on any hero.
        "critFactor": 1.0,
        "dpsPeriodBase": QUICK_MELEE["cooldown"],
        "dpsPeriodAdd": 0.0,
        "generated": True,
        "complete": True,
    }


def numbers_for(abilities: dict, name: str | None) -> dict:
    """The wiki's damage and cooldown for an ability Blizzard listed, matched by name."""
    entry = abilities.get(name or "")
    if entry is None:
        return {}
    return {
        "damage": entry["damage"],
        "cooldown": entry["cooldown"],
        "castTime": entry["castTime"],
        "damageLines": entry["lines"],
        "damageUncertain": entry["uncertain"],
    }


def apply_overrides(weapons: list[dict], overrides: dict) -> tuple[list[dict], list[str]]:
    """Merge hand corrections in, and report any that no longer match a weapon."""
    by_id = {f"{w['hero']}|{w['name']}": w for w in weapons}
    stale = []

    for key, patch in overrides.get("weapons", {}).items():
        target = by_id.get(key)
        if target is None:
            stale.append(key)
            continue
        for field, value in patch.items():
            if field.startswith("_"):  # notes explaining the correction, not data
                continue
            if isinstance(value, dict) and isinstance(target.get(field), dict):
                target[field].update(value)
            else:
                target[field] = value
        target["complete"] = True
        target["reviewed"] = True

        # `dpsPeriodAdd` is the reload amortised over the magazine, derived by the parser
        # from two figures an override is free to replace. Correcting ammo and reload
        # without redoing it leaves the stale derivation in place and the engine keeps
        # reporting the while-firing rate: Bastion's Assault turret read 360 dps after
        # being given a magazine, because its amortised reload was still the parser's zero.
        if {"ammo", "reloadTime"} & patch.keys() and "dpsPeriodAdd" not in patch:
            ammo, reload_time = target.get("ammo"), target.get("reloadTime")
            target["dpsPeriodAdd"] = reload_time / ammo if ammo and reload_time else 0.0

    for key in overrides.get("drop", []):
        by_id.pop(key, None)

    kept = [w for w in weapons if f"{w['hero']}|{w['name']}" in by_id]

    # Weapons written out by hand, for the handful the wiki describes only in prose. Torbjörn's
    # overloaded turret states its missile rate in a sentence - "a burst of 3 missiles every
    # 1.5 seconds" - which no field carries, so the parser cannot see it at all.
    for key, spec in overrides.get("add", {}).items():
        hero, _, name = key.partition("|")
        entry = {k: v for k, v in spec.items() if not k.startswith("_")}
        entry.update({"hero": hero, "name": name, "complete": True, "reviewed": True})
        kept.append(entry)

    return kept, stale


def write_review(path: Path, sections: dict[str, list[str]]) -> None:
    lines = [
        "# Dataset review",
        "",
        "Everything the pipeline could not read with confidence. Fix these by adding an",
        "entry to `dataset/overrides.json` - corrections there survive re-running the",
        "pipeline, edits to the generated dataset do not.",
        "",
        "An override is keyed by `\"Hero|Weapon name\"` and merges over the parsed weapon:",
        "",
        "```json",
        '{ "weapons": { "Ana|Biotic Rifle": { "fireRate": 1.25, "reviewed": true } } }',
        "```",
        "",
    ]
    for title, items in sections.items():
        if not items:
            continue
        lines.append(f"## {title} ({len(items)})")
        lines.append("")
        lines.extend(f"- {item}" for item in items)
        lines.append("")
    path.write_text("\n".join(lines), encoding="utf-8")


def main() -> int:
    roster = read_json(DATASET / "roster.json")["heroes"]
    media = {h["name"]: h for h in read_json(DATASET / "heroes-media.json")["heroes"]}
    parsed = read_json(DATASET / "weapons-parsed.json")["weapons"]
    ultimates = read_json(DATASET / "ultimates-parsed.json")["ultimates"]
    healing = read_json(DATASET / "healing-parsed.json")["healing"]
    perks = read_json(DATASET / "perks-parsed.json")["perks"]
    stadium_path = DATASET / "stadium-parsed.json"
    stadium = read_json(stadium_path)["items"] if stadium_path.exists() else []
    perks_by_hero: dict[str, list[dict]] = {}
    for perk in perks:
        perks_by_hero.setdefault(perk["hero"], []).append(
            {k: v for k, v in perk.items() if k != "hero"}
        )
    history = {h["hero"]: h for h in read_json(DATASET / "patch-history.json")["heroes"]}
    abilities_path = DATASET / "abilities-parsed.json"
    damaging = {}
    if abilities_path.exists():
        for entry in read_json(abilities_path)["abilities"]:
            damaging.setdefault(entry["hero"], {})[entry["name"]] = entry

    matchups_path = DATASET / "matchups.json"
    matchups = (
        {h["hero"]: h for h in read_json(matchups_path)["heroes"]}
        if matchups_path.exists()
        else {}
    )
    review_notes = read_json(DATASET / "review-weapons.json")["items"]

    overrides_path = DATASET / "overrides.json"
    overrides = read_json(overrides_path) if overrides_path.exists() else {}

    weapons, stale_overrides = apply_overrides(parsed, overrides)
    weapons += [quick_melee_for(hero["name"]) for hero in roster]

    heroes = []
    missing_release = []
    derived_colours = []

    for entry in roster:
        name = entry["name"]
        detail = media.get(name, {})
        wiki_text = (RAW / "wiki" / f"{entry['key']}.wiki").read_text(encoding="utf-8")
        released, order = release_info(wiki_text)
        if released is None:
            missing_release.append(name)
        if name not in KNOWN_COLOURS and detail.get("color"):
            derived_colours.append(name)

        heroes.append(
            {
                "key": entry["key"],
                "name": name,
                "color": detail.get("color", "#9aa4b2"),
                "role": detail.get("role", entry["role"]),
                "subrole": detail.get("subrole"),
                "description": detail.get("description"),
                "location": detail.get("location"),
                "age": detail.get("age"),
                "birthday": detail.get("birthday"),
                "health": detail.get("health"),
                "shields": detail.get("shields"),
                "armor": detail.get("armor"),
                "totalHitpoints": detail.get("totalHitpoints"),
                "portrait": detail.get("portrait"),
                # Blizzard's description says what an ability does; the wiki says how much.
                # Joined by name so the hero page can show both.
                "abilities": [
                    {**a, **numbers_for(damaging.get(name, {}), a.get("name"))}
                    for a in detail.get("abilities", [])
                ],
                # Damaging abilities the roster never mentions - perks, and anything
                # Blizzard's own list leaves out.
                "extraAbilities": [
                    entry
                    for ability_name, entry in damaging.get(name, {}).items()
                    if not any(
                        (a.get("name") or "").lower() == ability_name.lower()
                        for a in detail.get("abilities", [])
                    )
                ],
                "releaseDate": released,
                "heroNumber": order,
                "perks": perks_by_hero.get(name, []),
                "patches": history.get(name, {}).get("patches", []),
                "matchups": matchups.get(name, {}).get("matchups", []),
            }
        )

    playable = [w for w in weapons if w.get("complete")]
    incomplete = [w for w in weapons if not w.get("complete")]

    dataset = {
        "meta": {
            "source": "Overwatch Wiki (CC BY-SA) and the OverFast API",
            "patch": "live as of build",
            "generatedBy": "tools/build_dataset.py",
            "normalized": True,
            "version": 1,
        },
        "heroes": [
            {
                "key": h["key"],
                "name": h["name"],
                "color": h["color"],
                "role": h["role"],
                # The chart labels each row with the hero's face, and loading the whole
                # wiki file - twenty times the size - to find one portrait would be silly.
                "portrait": h["portrait"],
                # Carried here as well as in the wiki file: a Stadium build changes these,
                # and the chart data should not need the whole patch history to show them.
                "health": h["health"],
                "shields": h["shields"],
                "armor": h["armor"],
                "abilities": [a["name"] for a in h.get("abilities", []) if a.get("name")],
            }
            for h in heroes
        ],
        "weapons": [
            {k: v for k, v in w.items() if k not in ("complete", "reviewed", "generated")}
            for w in playable
        ],
        "ultimates": ultimates,
        "healing": healing,
        "stadiumItems": stadium,
        "wiki": heroes,
    }

    write_json(DATASET / "dataset-v1.json", dataset)

    # The app reads these as two files: the chart only needs the weapons, and making it
    # parse the whole patch history at startup would cost a second for nothing.
    write_json(
        APP_ASSETS / "weapons.json",
        {k: v for k, v in dataset.items() if k != "wiki"},
    )
    write_json(APP_ASSETS / "wiki.json", {"heroes": dataset["wiki"]})

    write_review(
        DATASET / "review.md",
        {
            "Weapons excluded from the chart until corrected": [
                f"**{w['hero']} — {w['name']}** "
                + ", ".join(
                    note["reason"]
                    for note in review_notes
                    if note["hero"] == w["hero"] and note["weapon"] == w["name"]
                )
                for w in incomplete
            ],
            "Field-level warnings on weapons that are otherwise usable": [
                f"**{n['hero']} — {n['weapon']}** `{n['field']}`: {n['reason']}"
                + (f" — `{n['raw']}`" if n["raw"] else "")
                for n in review_notes
                if not any(
                    w["hero"] == n["hero"] and w["name"] == n["weapon"] for w in incomplete
                )
            ],
            "Release date not found on the wiki page": missing_release,
            "Hero colours derived from a portrait (faces skew these towards skin tone)": (
                derived_colours
            ),
            "Overrides that no longer match any weapon": stale_overrides,
        },
    )

    # Ship the images alongside the dataset.
    APP_IMAGES.mkdir(parents=True, exist_ok=True)
    for existing in APP_IMAGES.glob("*.png"):
        existing.unlink()
    copied = 0
    for image in (RAW / "images").glob("*.png"):
        (APP_IMAGES / image.name).write_bytes(image.read_bytes())
        copied += 1

    size_mb = sum(p.stat().st_size for p in APP_IMAGES.glob("*.png")) / 1_048_576
    dataset_mb = (DATASET / "dataset-v1.json").stat().st_size / 1_048_576

    generated = sum(1 for w in playable if w.get("generated"))
    print(f"heroes:  {len(heroes)}")
    print(
        f"weapons: {len(playable)} playable ({generated} generated quick melee), "
        f"{len(incomplete)} held back for review"
    )
    print(f"patches: {sum(len(h['patches']) for h in heroes)}")
    rated = sum(1 for h in heroes for m in h["matchups"] if m.get("stance"))
    print(f"matchups: {sum(len(h['matchups']) for h in heroes)} ({rated} rated by the wiki)")
    print(f"images:  {copied} ({size_mb:.1f} MB)")
    print(f"dataset: {dataset_mb:.1f} MB -> {DATASET / 'dataset-v1.json'}")
    print(f"review:  {DATASET / 'review.md'}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
