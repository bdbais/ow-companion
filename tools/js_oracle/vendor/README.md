# Vendored reference implementation

These files come from [yfp/owdmgchart](https://github.com/yfp/owdmgchart) (MIT License,
Copyright (c) yfp) and are used **only** as a correctness oracle for the Kotlin port in
`app/src/main/java/com/bellizia/owcompanion/sim/`. They are not shipped in the APK.

| File | Origin |
|---|---|
| `weapons.js` | `public/weapons.js`, verbatim |
| `chart-core-body.js` | `public/chart.js` lines 65-457, verbatim — the hitbox, enemy and `WeaponData` classes, sliced out of the CoffeeScript IIFE so they can be loaded without a DOM |

`chart-core-body.js` is a *literal* slice, deliberately not edited: any change would weaken
its value as an independent oracle. The globals it expects (`timescale`, `areascale`,
`max_time`, `modificator`) are supplied by `../oracle.js`.
