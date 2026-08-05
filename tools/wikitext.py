"""Just enough MediaWiki template parsing for the Overwatch Wiki's ability boxes.

This is not a general wikitext parser and does not try to be. It handles the constructs the
hero pages actually use: balanced templates, the `#vardefineecho` / `#var` pair the wiki
uses to state a number once and reuse it, tooltips, links and HTML comments.
"""

from __future__ import annotations

import re

DASHES = "–—−"  # en dash, em dash, minus sign - all used interchangeably


def strip_comments(text: str) -> str:
    return re.sub(r"<!--.*?-->", "", text, flags=re.DOTALL)


def _matching_brace(text: str, start: int) -> int:
    """Index just past the `}}` closing the `{{` at `start`."""
    depth = 0
    i = start
    while i < len(text) - 1:
        pair = text[i : i + 2]
        if pair == "{{":
            depth += 1
            i += 2
            continue
        if pair == "}}":
            depth -= 1
            i += 2
            if depth == 0:
                return i
            continue
        i += 1
    return -1


def split_params(body: str) -> list[str]:
    """Split a template body on top-level pipes, ignoring nested templates and links."""
    parts = []
    depth_brace = 0
    depth_bracket = 0
    current = []
    i = 0
    while i < len(body):
        pair = body[i : i + 2]
        if pair == "{{":
            depth_brace += 1
            current.append(pair)
            i += 2
            continue
        if pair == "}}":
            depth_brace -= 1
            current.append(pair)
            i += 2
            continue
        if pair == "[[":
            depth_bracket += 1
            current.append(pair)
            i += 2
            continue
        if pair == "]]":
            depth_bracket -= 1
            current.append(pair)
            i += 2
            continue
        if body[i] == "|" and depth_brace == 0 and depth_bracket == 0:
            parts.append("".join(current))
            current = []
            i += 1
            continue
        current.append(body[i])
        i += 1
    parts.append("".join(current))
    return parts


def find_templates(text: str, names: set[str]) -> list[dict]:
    """Every top-level template in `text` whose name is in `names`, as a param dict."""
    found = []
    for match in re.finditer(r"\{\{\s*([A-Za-z_ ][A-Za-z0-9_ ]*)", text):
        name = match.group(1).strip().lower().replace(" ", "_")
        if name not in names:
            continue
        end = _matching_brace(text, match.start())
        if end < 0:
            continue
        body = text[match.start() + 2 : end - 2]
        params = {}
        for part in split_params(body)[1:]:
            if "=" not in part:
                continue
            key, _, value = part.partition("=")
            params[key.strip().lower()] = value.strip()
        found.append({"name": name, "params": params, "raw": text[match.start() : end]})
    return found


def resolve_vars(text: str) -> str:
    """Inline the wiki's `#vardefineecho` definitions into their `#var` references.

    A page states a number once - `{{#vardefineecho: rifle damage|75}}` - and refers back to
    it from every other ability box. Without resolving these, half the weapon stats read as
    a variable name instead of a value.
    """
    definitions: dict[str, str] = {}

    def collect(match: re.Match) -> str:
        end = _matching_brace(text, match.start())
        return text[match.start() : end] if end > 0 else match.group(0)

    # Collect first, so a definition can be referenced before it appears.
    for match in re.finditer(r"\{\{\s*#vardefineecho\s*:", text):
        end = _matching_brace(text, match.start())
        if end < 0:
            continue
        body = text[match.start() + 2 : end - 2]
        head, _, rest = body.partition("|")
        key = head.split(":", 1)[1].strip().lower()
        definitions[key] = rest.strip()

    # Replace the definitions with the value they echo.
    out = []
    i = 0
    while i < len(text):
        if text.startswith("{{", i):
            end = _matching_brace(text, i)
            if end > 0:
                inner = text[i + 2 : end - 2]
                stripped = inner.lstrip()
                if stripped.lower().startswith("#vardefineecho"):
                    _, _, rest = inner.partition("|")
                    out.append(rest.strip())
                    i = end
                    continue
                if stripped.lower().startswith("#var:") or stripped.lower().startswith("#var "):
                    head, _, default = inner.partition("|")
                    key = head.split(":", 1)[1].strip().lower()
                    out.append(definitions.get(key, default.strip()))
                    i = end
                    continue
        out.append(text[i])
        i += 1
    return "".join(out)


def clean_value(text: str) -> str:
    """Reduce a template parameter to the plain text a reader would see."""
    text = strip_comments(text)
    text = re.sub(r"<ref[^>]*>.*?</ref>", "", text, flags=re.DOTALL)
    text = re.sub(r"<ref[^>]*/>", "", text)

    # {{tt|shown|tooltip}} shows the first argument; {{proj|hitscan}} names a shot type.
    def take_first_arg(match: re.Match) -> str:
        body = match.group(1)
        parts = split_params(body)
        return parts[1].strip() if len(parts) > 1 else ""

    for _ in range(4):  # tooltips nest a couple of levels deep in places
        new = re.sub(
            r"\{\{\s*(?:tt|proj|abbr)\s*(\|(?:[^{}]|\{\{[^{}]*\}\})*)\}\}",
            lambda m: take_first_arg(m),
            text,
            flags=re.IGNORECASE,
        )
        if new == text:
            break
        text = new

    text = re.sub(r"\[\[[^\]|]*\|([^\]]*)\]\]", r"\1", text)
    text = re.sub(r"\[\[([^\]]*)\]\]", r"\1", text)
    text = re.sub(r"'''?", "", text)
    text = re.sub(r"<br\s*/?>", "\n", text, flags=re.IGNORECASE)
    text = re.sub(r"</?(small|sup|sub|span|div)[^>]*>", "", text, flags=re.IGNORECASE)
    return text.strip()


def normalise_dashes(text: str) -> str:
    for dash in DASHES:
        text = text.replace(dash, "-")
    return text


NUMBER = r"[-+]?\d+(?:\.\d+)?"


def first_number(text: str) -> float | None:
    match = re.search(NUMBER, normalise_dashes(text).replace(",", ""))
    return float(match.group(0)) if match else None


def number_pair(text: str) -> list[float] | None:
    """`70 - 21` or `25 - 35 meters` as two numbers; None when it is not a pair."""
    cleaned = normalise_dashes(text).replace(",", "")
    match = re.search(rf"({NUMBER})\s*-\s*({NUMBER})", cleaned)
    if not match:
        return None
    return [float(match.group(1)), float(match.group(2))]
