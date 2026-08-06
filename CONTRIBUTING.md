# Contributing

Ideas, corrections and pull requests are all welcome. The most useful contributions are
probably not code.

## The fastest way to help: tell us a number is wrong

The app's whole claim is that its damage figures are real. Every weapon stat is parsed from
the [Overwatch Wiki](https://overwatch.fandom.com) and a few dozen were corrected by hand,
so there are certainly mistakes left.

If a hero's damage looks wrong, open an issue with the hero, the weapon, what the app says
and what it should say. A link to the wiki page or an in-game screenshot settles it
immediately.

## Where the numbers come from

Nothing in `dataset/` is written by hand except `overrides.json`. The rest is generated:

```bash
python tools/fetch_wiki.py       # roster + every hero's wikitext
python tools/parse_weapons.py    # weapon stats, ultimates, healing, perks
python tools/parse_patchlog.py   # ten years of balance changes
python tools/parse_stadium.py    # the Stadium item catalogue
python tools/fetch_media.py      # portraits and ability icons
python tools/build_dataset.py    # merges it all, writes dataset/review.md
```

Anything the parser could not read with confidence is held out of the app and listed in
`dataset/review.md`. Corrections go in `dataset/overrides.json`, keyed by `"Hero|Weapon"`,
and survive re-running the pipeline. **Do not edit the generated files** — the next build
overwrites them.

## Building

Requires JDK 17+ and the Android SDK (platform 35).

```bash
./gradlew assembleDebug
./gradlew test
```

The tests are worth understanding before changing the simulation. `GoldenValueTest` runs the
original [owdmgchart](https://github.com/yfp/owdmgchart) JavaScript headlessly and asserts
the Kotlin engine reproduces it across 46 weapons and 11 configurations. The two
implementations share no code, so agreement is real evidence. If you change the engine and
that suite fails, the engine is wrong, not the test.

Regenerate the golden values only if you have a reason to:

```bash
node tools/js_oracle/generate_golden.js
```

## Things we know are weak

Listed honestly, because these are where help is worth most:

- **Hero colours.** Twenty heroes have no recorded colour, so theirs is averaged from their
  portrait. Portraits are face shots, so several came out skin-toned — Sojourn, Venture,
  Vendetta and Wuyang are all wrong-ish browns. Hand-picked hex values would fix this in
  minutes.
- **Stadium.** Only 17 of the 98 items change anything the damage model computes. Ability
  power, cooldowns and survivability are catalogued but cannot be optimised for, so a build
  that leans on them is undersold.
- **5v5 versus 6v6.** The patch data already separates them — 75 patches are tagged `ow6v6`
  — but the app does not yet model the difference.
- **Perks.** All 208 are shown; none are simulated. A few genuinely change a weapon (Ana's
  Headhunter lets her rifle crit) and could be wired into the engine.
- **Damage over time.** The hero timeline plots the values the patch notes state. It is not
  a reconstruction, so a stat the notes never restated simply does not appear.

## What this project will not do

It is a non-commercial fan project and stays that way: free, no advertising, nothing sold.
That is both the intent and what Blizzard's Fan Content Policy asks for.

Contributions are accepted under the MIT licence of the repository.
