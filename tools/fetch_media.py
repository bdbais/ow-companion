"""Downloads hero portraits and ability icons, and derives each hero's colour.

    python tools/fetch_media.py

Images come from Blizzard's CDN via the OverFast API. They are Blizzard's property and are
used here under the Fan Content Policy; the app ships them for offline use and credits them
on its attributions screen.
"""

from __future__ import annotations

import sys

from common import DATASET, RAW, fetch, overfast, read_json, write_json
from pngcolor import signature_colour

# Hero colours from owdmgchart (MIT), which picked them to match the game's own palette.
# Heroes added since then get a colour derived from their portrait instead.
KNOWN_COLOURS = {
    "Ana": "#718ab3", "Ashe": "#982020", "Bastion": "#7c8f7b", "Baptiste": "#5f9985",
    "Brigitte": "#be736e", "D.Va": "#ed93c7", "Doomfist": "#815049", "Echo": "#4cc4e8",
    "Genji": "#97ef43", "Hanzo": "#b9b48a", "Junkrat": "#ecbd53", "Lúcio": "#85c952",
    "Cassidy": "#ae595c", "Mei": "#6faced", "Mercy": "#ebe8bb", "Moira": "#803c51",
    "Orisa": "#468c43", "Pharah": "#3e7dca", "Reaper": "#7d3e51", "Reinhardt": "#929da3",
    "Roadhog": "#b68c52", "Sigma": "#929a9d", "Soldier: 76": "#697794",
    "Sombra": "#7359ba", "Symmetra": "#8ebccc", "Torbjörn": "#c0726e", "Tracer": "#d79342",
    "Widowmaker": "#9e6aa8", "Wrecking Ball": "#e6a027", "Winston": "#a2a6bf",
    "Zarya": "#e77eb6", "Zenyatta": "#ede582",
}


def image_name(url: str, prefix: str) -> str:
    tail = url.rsplit("/", 1)[-1].split("?")[0]
    stem = tail.rsplit(".", 1)[0][:24]
    return f"{prefix}_{stem}.png"


def main(refresh: bool = False) -> int:
    roster = read_json(DATASET / "roster.json")["heroes"]
    images_dir = RAW / "images"
    images_dir.mkdir(parents=True, exist_ok=True)

    heroes = []
    derived_colours = 0
    failures = []

    for entry in roster:
        key = entry["key"]
        detail = overfast(f"/heroes/{key}", refresh=refresh)
        name = entry["name"]

        portrait_file = None
        colour = KNOWN_COLOURS.get(name)
        portrait_url = detail.get("portrait") or entry.get("portrait")
        if portrait_url:
            data = fetch(portrait_url, binary=True, refresh=refresh)
            portrait_file = f"portrait_{key}.png"
            (images_dir / portrait_file).write_bytes(data)
            if colour is None:
                colour = signature_colour(data)
                if colour:
                    derived_colours += 1

        if colour is None:
            failures.append(f"{name}: no colour")
            colour = "#9aa4b2"

        abilities = []
        for ability in detail.get("abilities", []):
            icon_file = None
            icon_url = ability.get("icon")
            if icon_url:
                try:
                    icon_data = fetch(icon_url, binary=True, refresh=refresh)
                    icon_file = f"ability_{key}_{len(abilities)}.png"
                    (images_dir / icon_file).write_bytes(icon_data)
                except Exception as error:  # noqa: BLE001 - a missing icon is not fatal
                    failures.append(f"{name}/{ability.get('name')}: icon {error}")
            abilities.append(
                {
                    "name": ability.get("name"),
                    "description": (ability.get("description") or "").strip(),
                    "icon": icon_file,
                }
            )

        hitpoints = detail.get("hitpoints") or {}
        heroes.append(
            {
                "key": key,
                "name": name,
                "role": detail.get("role") or entry["role"],
                "subrole": detail.get("subrole"),
                "color": colour,
                "description": (detail.get("description") or "").strip(),
                "location": detail.get("location"),
                "age": detail.get("age"),
                "birthday": detail.get("birthday"),
                "health": hitpoints.get("health"),
                "shields": hitpoints.get("shields"),
                "armor": hitpoints.get("armor"),
                "totalHitpoints": hitpoints.get("total"),
                "portrait": portrait_file,
                "abilities": abilities,
            }
        )
        print(f"  {name:<16} {colour}  {len(abilities)} abilities")

    write_json(DATASET / "heroes-media.json", {"heroes": heroes})
    total_images = len(list(images_dir.glob("*.png")))
    size_mb = sum(p.stat().st_size for p in images_dir.glob("*.png")) / 1_048_576

    print(f"\n{len(heroes)} heroes, {total_images} images, {size_mb:.1f} MB")
    print(f"  {derived_colours} colours derived from portraits")
    if failures:
        print(f"  {len(failures)} problems:")
        for failure in failures:
            print(f"    {failure}")
    return 0


if __name__ == "__main__":
    sys.exit(main(refresh="--refresh" in sys.argv))
