# OW Companion

Android app that pairs an interactive **damage chart** — a port of
[owdmgchart](https://yfp.github.io/owdmgchart/public/index.html) rebuilt natively for touch —
with a **hero wiki** covering portraits, abilities, release dates and the full balance
history of every hero.

Status: **early development.** The app shell builds and runs; the simulation engine is
being ported and validated against the original implementation.

## What the damage chart does

It answers "how much damage does this weapon *actually* do?" rather than what the tooltip
claims. For every weapon it simulates a real firing sequence against a target at a given
distance and crosshair placement, accounting for:

- damage falloff over range, and hard range cutoffs
- spread cones and per-pellet scatter for shotguns
- projectile travel time, including ballistic arcs
- ammo, reload and burst timing
- critical hits, decided by whether a sampled pellet lands on the head hitbox
- damage modifiers (armour, damage boost, discord, and friends)

The result is a mean DPS you can sort and compare across the whole roster.

## Layout

| Path | What lives there |
|---|---|
| `app/src/main/java/com/bellizia/owcompanion/sim/` | Simulation engine — pure Kotlin, no Android dependencies, unit tested on the JVM |
| `app/src/main/java/com/bellizia/owcompanion/data/` | Dataset loading, versioning, optional online refresh |
| `app/src/main/java/com/bellizia/owcompanion/ui/` | Compose UI: chart screen and hero wiki |
| `tools/js_oracle/` | Node harness that runs the original JavaScript implementation to generate golden test values |
| `tools/` | Python data pipeline that builds the hero/weapon/patch dataset |
| `dataset/` | Pipeline output plus the manual-review report |

## Building

Requires JDK 17+ and the Android SDK (platform 35). Android Studio's bundled JBR works:

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug
```

Run the engine tests:

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew test
```

Regenerate the golden values the engine is tested against (requires Node):

```bash
node tools/js_oracle/generate_golden.js
```

## Attribution and licensing

This is a non-commercial fan project.

- The simulation model and the 2020 reference dataset derive from
  [yfp/owdmgchart](https://github.com/yfp/owdmgchart), MIT licensed. The vendored copies
  used as a test oracle live in `tools/js_oracle/vendor/`.
- Hero statistics and balance history are sourced from the
  [Overwatch Wiki](https://overwatch.fandom.com), licensed CC BY-SA.
- Hero portraits, ability icons, names and artwork are the property of Blizzard
  Entertainment, used under Blizzard's Fan Content Policy. This project is not affiliated
  with or endorsed by Blizzard.

Application code in this repository is MIT licensed — see [LICENSE](LICENSE).
