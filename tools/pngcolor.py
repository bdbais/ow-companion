"""Reads a hero's signature colour straight out of their portrait.

The chart colours every weapon by its hero, and the 20 heroes added since the reference
dataset have no colour recorded anywhere machine-readable. Rather than inventing one, this
takes the average of the portrait's most saturated pixels - which lands on Mauga's red,
Juno's teal and so on.

Pillow is broken in this environment, so the PNG is decoded here: these portraits are all
8-bit RGBA, which is the one case that needs handling.
"""

from __future__ import annotations

import colorsys
import struct
import zlib

PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"


class UnsupportedPng(Exception):
    pass


def _chunks(data: bytes):
    offset = 8
    while offset < len(data):
        (length,) = struct.unpack(">I", data[offset : offset + 4])
        kind = data[offset + 4 : offset + 8]
        payload = data[offset + 8 : offset + 8 + length]
        yield kind, payload
        offset += 12 + length


def _paeth(a: int, b: int, c: int) -> int:
    p = a + b - c
    pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
    if pa <= pb and pa <= pc:
        return a
    return b if pb <= pc else c


def decode_rgba(data: bytes) -> tuple[int, int, bytearray, int]:
    """(width, height, pixel bytes, bytes per pixel). 8-bit RGB or RGBA, non-interlaced."""
    if not data.startswith(PNG_SIGNATURE):
        raise UnsupportedPng("not a PNG")

    width = height = 0
    channels = 4
    idat = bytearray()
    for kind, payload in _chunks(data):
        if kind == b"IHDR":
            width, height, depth, colour_type, _, _, interlace = struct.unpack(
                ">IIBBBBB", payload
            )
            # Portraits come as either RGBA or, for a few heroes, flat RGB.
            channels = {2: 3, 6: 4}.get(colour_type, 0)
            if depth != 8 or channels == 0 or interlace != 0:
                raise UnsupportedPng(
                    f"depth={depth} colourType={colour_type} interlace={interlace}"
                )
        elif kind == b"IDAT":
            idat += payload
        elif kind == b"IEND":
            break

    raw = zlib.decompress(bytes(idat))
    stride = width * channels
    out = bytearray(height * stride)

    position = 0
    for row in range(height):
        filter_type = raw[position]
        position += 1
        line = raw[position : position + stride]
        position += stride
        start = row * stride
        previous = start - stride

        for i in range(stride):
            value = line[i]
            left = out[start + i - channels] if i >= channels else 0
            up = out[previous + i] if row > 0 else 0
            up_left = out[previous + i - channels] if (row > 0 and i >= channels) else 0

            if filter_type == 1:
                value += left
            elif filter_type == 2:
                value += up
            elif filter_type == 3:
                value += (left + up) // 2
            elif filter_type == 4:
                value += _paeth(left, up, up_left)
            out[start + i] = value & 0xFF

    return width, height, out, channels


def signature_colour(data: bytes) -> str | None:
    """Hex colour of the portrait's saturated core, or None if it cannot be read."""
    try:
        width, height, pixels, channels = decode_rgba(data)
    except (UnsupportedPng, zlib.error):
        return None

    total_r = total_g = total_b = 0.0
    weight = 0.0

    for index in range(0, width * height):
        offset = index * channels
        alpha = pixels[offset + 3] if channels == 4 else 255
        if alpha < 200:
            continue
        r, g, b = pixels[offset] / 255, pixels[offset + 1] / 255, pixels[offset + 2] / 255
        h, l, s = colorsys.rgb_to_hls(r, g, b)
        # Skip the near-black and near-white pixels that dominate portrait backgrounds and
        # would wash any hero's colour out towards grey.
        if s < 0.25 or l < 0.15 or l > 0.9:
            continue
        # Weight by how colourful the pixel is, so the hue that defines the hero wins.
        w = s * s
        total_r += r * w
        total_g += g * w
        total_b += b * w
        weight += w

    if weight == 0:
        return None

    r, g, b = total_r / weight, total_g / weight, total_b / weight
    # Portraits are dark; lift the result to something readable on a dark chart.
    h, l, s = colorsys.rgb_to_hls(r, g, b)
    l = min(0.72, max(0.55, l))
    s = min(0.75, max(0.35, s))
    r, g, b = colorsys.hls_to_rgb(h, l, s)
    return "#{:02x}{:02x}{:02x}".format(round(r * 255), round(g * 255), round(b * 255))
