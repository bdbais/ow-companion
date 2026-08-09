"""Downloads a picture and the particulars for every map.

    python tools/fetch_maps.py

The maps screen used to be a list of names, because there was nothing to show: the top-down
tactical diagrams people actually want are drawn by StatBanana and are theirs, so they stay
out of this repository. Blizzard publish a screenshot of each map, OverFast serves it, and
that is the same provenance as the hero portraits already bundled - which is why they can
be shipped and the diagrams cannot.

Writes dataset/maps.json and the images into the assets folder beside the heroes.
"""

from __future__ import annotations

import json
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DATASET = ROOT / "dataset"
ASSETS = ROOT / "app" / "src" / "main" / "assets" / "maps"

API = "https://overfast-api.tekrop.fr/maps"
AGENT = "ow-companion (dataset build)"

# Wide enough to fill a phone at any density without carrying a desktop wallpaper each.
MAX_WIDTH = 720


def fetch(url: str) -> bytes:
    request = urllib.request.Request(url, headers={"User-Agent": AGENT})
    with urllib.request.urlopen(request, timeout=30) as response:
        return response.read()


def shrink(data: bytes, name: str) -> bytes:
    """Down to a sensible width, as WebP. Falls back to the original if Pillow is absent."""
    try:
        import io

        from PIL import Image
    except ImportError:
        print(f"  {name}: Pillow missing, keeping the original", file=sys.stderr)
        return data

    image = Image.open(io.BytesIO(data)).convert("RGB")
    if image.width > MAX_WIDTH:
        height = round(image.height * MAX_WIDTH / image.width)
        image = image.resize((MAX_WIDTH, height), Image.LANCZOS)
    buffer = io.BytesIO()
    image.save(buffer, "WEBP", quality=80, method=6)
    return buffer.getvalue()


def main() -> int:
    ASSETS.mkdir(parents=True, exist_ok=True)
    maps = json.loads(fetch(API))

    written = []
    for entry in sorted(maps, key=lambda m: m["name"]):
        key = entry["key"]
        shot = entry.get("screenshot")
        image = None
        if shot:
            try:
                image = f"{key}.webp"
                (ASSETS / image).write_bytes(shrink(fetch(shot), key))
            except Exception as error:  # noqa: BLE001 - a missing picture is not fatal
                print(f"  {key}: {error}", file=sys.stderr)
                image = None

        written.append(
            {
                "key": key,
                "name": entry["name"],
                # The rates page is keyed on the same slug, which is what makes the meta
                # figures on a map's page possible at all.
                "modes": entry.get("gamemodes") or [],
                "location": entry.get("location"),
                "country": entry.get("country_code"),
                "image": image,
            }
        )

    DATASET.mkdir(parents=True, exist_ok=True)
    (DATASET / "maps.json").write_text(
        json.dumps({"maps": written}, ensure_ascii=False, indent=1) + "\n",
        encoding="utf-8",
    )

    total = sum(p.stat().st_size for p in ASSETS.glob("*.webp")) / 1_048_576
    with_image = sum(1 for m in written if m["image"])
    print(f"maps: {len(written)} ({with_image} with a picture, {total:.1f} MB)")
    print(f"wrote {(DATASET / 'maps.json').relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
