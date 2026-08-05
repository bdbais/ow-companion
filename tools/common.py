"""Shared helpers for the dataset pipeline.

Every fetch goes through an on-disk cache. The wiki and the API are the slow, rate-limited
part of building the dataset, and the parsers need to be run over and over while they are
being tuned - so a page is fetched once and then re-read from disk.
"""

from __future__ import annotations

import hashlib
import json
import time
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DATASET = ROOT / "dataset"
RAW = DATASET / "raw"
CACHE = RAW / "cache"

WIKI_API = "https://overwatch.fandom.com/api.php"
OVERFAST_API = "https://overfast-api.tekrop.fr"

# Fandom rejects the default Python user agent outright, and an anonymous scraper deserves
# to say who it is anyway.
USER_AGENT = (
    "OwCompanion-dataset-builder/0.1 "
    "(+https://github.com/bdbais/ow-companion; non-commercial fan project)"
)

REQUEST_DELAY_SECONDS = 0.34

_last_request = 0.0


def _throttle() -> None:
    global _last_request
    elapsed = time.time() - _last_request
    if elapsed < REQUEST_DELAY_SECONDS:
        time.sleep(REQUEST_DELAY_SECONDS - elapsed)
    _last_request = time.time()


def fetch(url: str, *, binary: bool = False, refresh: bool = False) -> bytes | str:
    """GET a URL, caching the response under dataset/raw/cache."""
    CACHE.mkdir(parents=True, exist_ok=True)
    key = hashlib.sha256(url.encode("utf-8")).hexdigest()[:32]
    path = CACHE / (key + (".bin" if binary else ".txt"))

    if path.exists() and not refresh:
        return path.read_bytes() if binary else path.read_text(encoding="utf-8")

    _throttle()
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=60) as response:
        payload = response.read()

    if binary:
        path.write_bytes(payload)
        return payload

    text = payload.decode("utf-8")
    path.write_text(text, encoding="utf-8")
    return text


def wiki_api(refresh: bool = False, **params) -> dict:
    params.setdefault("format", "json")
    url = f"{WIKI_API}?{urllib.parse.urlencode(params)}"
    return json.loads(fetch(url, refresh=refresh))


def wikitext(title: str, refresh: bool = False) -> str | None:
    """Full wikitext of a page, following redirects. None when the page does not exist."""
    data = wiki_api(
        action="parse",
        page=title,
        prop="wikitext",
        redirects="1",
        refresh=refresh,
    )
    if "error" in data:
        return None
    return data["parse"]["wikitext"]["*"]


def overfast(path: str, refresh: bool = False) -> dict | list:
    return json.loads(fetch(f"{OVERFAST_API}{path}", refresh=refresh))


def write_json(path: Path, payload) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(payload, indent=1, ensure_ascii=False),
        encoding="utf-8",
    )


def read_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))
