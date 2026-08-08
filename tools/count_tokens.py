"""How many tokens went into building this app.

The app was written by Claude Code, which keeps a transcript of every session under
~/.claude/projects/<slug>/*.jsonl, one JSON object per line, and records the token usage of
each assistant turn. This adds those up.

Two things it is careful about, because both would otherwise inflate the figure:

  - That transcript directory holds sessions for more than one project. Only turns whose
    working directory is inside this repository are counted.
  - Usage is reported per turn as four separate counts. Cache reads dominate by two orders
    of magnitude, so a total that omitted them would be wrong by 30x, and one that hid the
    breakdown would be hard to believe. Both are recorded.

Writes dataset/development.properties, which the build reads into BuildConfig. Run it before
building a release:

    python tools/count_tokens.py
"""
from __future__ import annotations

import json
import sys
from collections import Counter
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TRANSCRIPTS = Path.home() / ".claude" / "projects"
# Turns are attributed by working directory, which is how sessions for different projects
# are told apart inside a shared transcript folder.
MARKER = "ow_companion"
COUNTS = (
    "input_tokens",
    "output_tokens",
    "cache_creation_input_tokens",
    "cache_read_input_tokens",
)


def tally() -> tuple[Counter, set[str], str | None]:
    totals: Counter = Counter()
    sessions: set[str] = set()
    latest: str | None = None

    if not TRANSCRIPTS.is_dir():
        return totals, sessions, latest

    for transcript in TRANSCRIPTS.glob("*/*.jsonl"):
        for line in transcript.read_text(encoding="utf-8", errors="replace").splitlines():
            try:
                record = json.loads(line)
            except json.JSONDecodeError:
                continue

            cwd = (record.get("cwd") or "").replace("\\", "/").lower()
            if MARKER not in cwd:
                continue

            usage = (record.get("message") or {}).get("usage")
            if not isinstance(usage, dict):
                continue

            sessions.add(transcript.stem)
            stamp = record.get("timestamp")
            if isinstance(stamp, str) and (latest is None or stamp > latest):
                latest = stamp
            for key in COUNTS:
                value = usage.get(key)
                if isinstance(value, int):
                    totals[key] += value

    return totals, sessions, latest


def main() -> int:
    totals, sessions, latest = tally()
    total = sum(totals.values())

    if total == 0:
        print(
            "No transcripts found for this project. Leaving the existing figure alone: an\n"
            "app built on another machine should not claim the work cost nothing.",
            file=sys.stderr,
        )
        return 1

    out = ROOT / "dataset" / "development.properties"
    out.write_text(
        "# Written by tools/count_tokens.py. Read by app/build.gradle.kts.\n"
        f"tokens={total}\n"
        f"tokensInput={totals['input_tokens']}\n"
        f"tokensOutput={totals['output_tokens']}\n"
        f"tokensCacheWrite={totals['cache_creation_input_tokens']}\n"
        f"tokensCacheRead={totals['cache_read_input_tokens']}\n"
        f"sessions={len(sessions)}\n"
        f"measured={date.today().isoformat()}\n",
        encoding="utf-8",
    )

    print(f"tokens:   {total:,} across {len(sessions)} sessions")
    for key in COUNTS:
        print(f"  {key:28} {totals[key]:>15,}")
    if latest:
        print(f"last turn: {latest}")
    print(f"written:  {out.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
