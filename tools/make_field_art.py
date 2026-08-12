"""Builds the art blob for the field panel.

    python tools/make_field_art.py

Reads the reference sheets from `dataset/field/`, cuts the pieces out, packs them into one
atlas and writes it to `app/src/main/assets/field.bin` with a manifest beside it.

Three things are worth knowing about the output:

**It is one file, not a folder of drawables.** Fifteen separate images under `res/drawable`
would be listed in the resource table and previewed by any tool that opens an APK. Packed,
they are one entry.

**It is not stored as a PNG.** The bytes are masked with a repeating key, so a scanner
looking for image headers walks past it. This is obfuscation, not protection: anyone who
reads the loader can undo it in a minute. It only has to survive idle curiosity.

**The pieces are trimmed and small.** Nothing is stored larger than it is drawn, which keeps
the blob well under a megabyte.
"""

from __future__ import annotations

import json
import sys
import zlib
from io import BytesIO
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "dataset" / "field"
ASSETS = ROOT / "app" / "src" / "main" / "assets"

# The byte mask. Not a secret - it is in the loader too - just enough that the file does not
# announce itself as a picture.
KEY = b"\x5a\xa5\x3c\xc3\x69\x96"

SHEETS = {
    "figure": "figure.png",
    "scene": "scene.png",
    "blast": "blast.png",
    "eject": "eject.png",
}

# Each sheet was drawn with a checkerboard standing in for transparency. These are the two
# tones it was painted in, per sheet.
TONES = {
    "figure": [(255, 255, 255), (233, 233, 233)],
    "scene": [(170, 176, 190), (116, 121, 140)],
    "blast": [(163, 169, 183), (108, 111, 130)],
    "eject": [(163, 169, 183), (108, 111, 130)],
}

# name -> (sheet, box, height in pixels once packed)
#
# Automatic component finding was tried and abandoned: the drawings touch, a faint skyline
# runs behind them and one sheet has a drawn border, so it all comes back as a single blob.
# There are only sixteen pieces. Each crop is trimmed to its own opaque bounds afterwards,
# so a box only has to be roughly right.
CUTS: dict[str, tuple[str, tuple[int, int, int, int], int]] = {
    "ship": ("figure", (144, 48, 1904, 1884), 72),
    "drone": ("scene", (1748, 880, 2048, 1184), 34),
    "fighter": ("scene", (1352, 1016, 1640, 1272), 38),
    "boss": ("scene", (900, 72, 1745, 820), 150),
    "burst": ("scene", (520, 1096, 888, 1464), 48),
    "blast": ("blast", (1055, 229, 1636, 749), 96),
    "debris": ("blast", (1865, 321, 2355, 749), 64),
    "eject_0": ("eject", (61, 306, 535, 581), 72),
    "eject_1": ("eject", (581, 306, 1009, 581), 72),
    "eject_2": ("eject", (1040, 229, 1407, 765), 96),
    "eject_3": ("eject", (1498, 199, 1835, 765), 96),
    "eject_4": ("eject", (1957, 199, 2232, 765), 96),
    "pilot": ("eject", (2430, 300, 2620, 500), 40),
    "pilot_fall": ("eject", (428, 1085, 795, 1284), 40),
    "wing": ("eject", (1131, 1009, 1529, 1391), 52),
    "flier": ("eject", (1957, 994, 2385, 1391), 52),
}


def keyed(path: Path, tones: list[tuple[int, int, int]]) -> Image.Image:
    """The sheet with its painted checkerboard turned into real transparency."""
    image = Image.open(path).convert("RGBA")
    pixels = image.load()
    for y in range(image.height):
        for x in range(image.width):
            r, g, b, _ = pixels[x, y]
            if any(abs(r - tr) + abs(g - tg) + abs(b - tb) < 45 for tr, tg, tb in tones):
                pixels[x, y] = (0, 0, 0, 0)
    return image


def main() -> int:
    missing = [name for name, file in SHEETS.items() if not (SOURCE / file).exists()]
    if missing:
        print(f"Nothing to do: {SOURCE} has no {', '.join(missing)}.")
        return 1

    sheets = {name: keyed(SOURCE / file, TONES[name]) for name, file in SHEETS.items()}

    pieces: list[tuple[str, Image.Image]] = []
    for name, (sheet, box, height) in CUTS.items():
        crop = sheets[sheet].crop(box)
        bounds = crop.getbbox()
        if bounds:
            crop = crop.crop(bounds)
        if not crop.width or not crop.height:
            print(f"  {name}: box caught nothing")
            continue
        width = max(1, round(crop.width * height / crop.height))
        pieces.append((name, crop.resize((width, height), Image.NEAREST)))

    # Packed in one row. Sixteen pieces at these sizes come to well under 2048 across, which
    # every device can hold as a single texture, and a row needs no packing cleverness.
    pad = 2
    atlas_w = sum(image.width + pad for _, image in pieces) + pad
    atlas_h = max(image.height for _, image in pieces) + pad * 2
    atlas = Image.new("RGBA", (atlas_w, atlas_h), (0, 0, 0, 0))

    manifest: dict[str, list[int]] = {}
    x = pad
    for name, image in pieces:
        atlas.alpha_composite(image, (x, pad))
        manifest[name] = [x, pad, image.width, image.height]
        x += image.width + pad

    buffer = BytesIO()
    atlas.save(buffer, format="PNG", optimize=True)
    raw = buffer.getvalue()

    header = json.dumps(manifest, separators=(",", ":")).encode("utf-8")
    body = len(header).to_bytes(4, "big") + header + raw
    packed = zlib.compress(body, 9)
    masked = bytes(byte ^ KEY[index % len(KEY)] for index, byte in enumerate(packed))

    ASSETS.mkdir(parents=True, exist_ok=True)
    (ASSETS / "field.bin").write_bytes(masked)

    print(f"{len(pieces)} pieces, atlas {atlas_w}x{atlas_h}, {len(masked) / 1024:.0f} KiB")
    for name, box in manifest.items():
        print(f"  {name:<12} {box[2]:>3}x{box[3]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
