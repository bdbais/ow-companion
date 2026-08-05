# Dataset review

Everything the pipeline could not read with confidence. Fix these by adding an
entry to `dataset/overrides.json` - corrections there survive re-running the
pipeline, edits to the generated dataset do not.

An override is keyed by `"Hero|Weapon name"` and merges over the parsed weapon:

```json
{ "weapons": { "Ana|Biotic Rifle": { "fireRate": 1.25, "reviewed": true } } }
```

## Weapons excluded from the chart until corrected (17)

- **Anran — Fan the Flames** beam weapons need their tick rate filled in by hand
- **Baptiste — Biotic Launcher Alt Fire** missing damage
- **Domina — Photon Magnum** damage is stated as a rate, not per shot, beam weapons need their tick rate filled in by hand, missing damage, fireRate
- **Freja — Take Aim** missing fireRate
- **Illari — Solar Rifle Alt Fire** beam weapons need their tick rate filled in by hand, missing damage, fireRate
- **Kiriko — Healing Ofuda** missing damage
- **Lifeweaver — Healing Blossom** missing damage
- **Mei — Endothermic Blaster** beam weapons need their tick rate filled in by hand
- **Mercy — Caduceus Staff** beam weapons need their tick rate filled in by hand, missing damage, fireRate
- **Mercy — Caduceus Staff Alt Fire** beam weapons need their tick rate filled in by hand, missing damage, fireRate
- **Moira — Biotic Grasp** beam weapons need their tick rate filled in by hand, missing damage, fireRate
- **Moira — Biotic Grasp Alt Fire** damage is stated as a rate, not per shot, beam weapons need their tick rate filled in by hand, missing damage, fireRate
- **Sojourn — Charged Shot** missing fireRate
- **Symmetra — Photon Projector** damage is stated as a rate, not per shot, beam weapons need their tick rate filled in by hand, missing damage, fireRate
- **Winston — Tesla Cannon** damage is stated as a rate, not per shot, beam weapons need their tick rate filled in by hand, missing damage, fireRate
- **Winston — Tesla Cannon Alt Fire** missing fireRate
- **Zarya — Particle Cannon** damage is stated as a rate, not per shot, beam weapons need their tick rate filled in by hand, missing damage, fireRate

## Field-level warnings on weapons that are otherwise usable (13)

- **Hazard — Bonespur** `spread`: several spread values, took the first — `0 degrees (center shot) / 1.8 degrees (5 central shots) / 3.5 degrees (4 corner shots)`
- **Jetpack Cat — Biotic Pawjectiles** `spread`: several spread values, took the first — `0 degrees (center shot) / 0.3 degrees (close paw shots) / 0.9 degrees (far paw shows)`
- **Mauga — Incendiary Chaingun** `falloff`: falloff differs by condition, took the first — `30 - 40 meters / 15 - 20 meters (simultaneous fire)`
- **Mauga — Incendiary Chaingun** `spread`: blooming spread needs a spreadingAmmoRange — `1 degree (base) / 1.5 degrees (max) / 4 degrees (simultaneous fire)`
- **Mauga — Volatile Chaingun** `falloff`: falloff differs by condition, took the first — `30 - 40 meters / 15 - 20 meters (simultaneous fire)`
- **Mauga — Volatile Chaingun** `spread`: blooming spread needs a spreadingAmmoRange — `1 degree (base) / 1.5 degrees (max) / 4 degrees (simultaneous fire)`
- **Roadhog — Scrap Gun Alt Fire** `falloff`: falloff differs by condition, took the first — `None (pre-detonation) / 24 - 39 meters (post-detonation)`
- **Roadhog — Scrap Gun Alt Fire** `falloff`: damage is a near/far pair but no falloff range was found — `None (pre-detonation)
24 - 39 meters (post-detonation)`
- **Sierra — Helix Rifle** `spread`: blooming spread needs a spreadingAmmoRange — `3 degrees (max) / 0.5 degrees (min)`
- **Sojourn — Railgun** `spread`: several spread values, took the first — `1.6 degrees (vertical, max) / 0.64 degrees (horizontal, max)`
- **Symmetra — Photon Projector Alt Fire** `falloff`: damage is a near/far pair but no falloff range was found
- **Wuyang — Xuanwu Staff** `falloff`: falloff differs by condition, took the first — `0.8 - 1.3 meters (splash, uncharged) / 0.8 - 2.7 meters (splash, charged)`
- **Zarya — Particle Cannon Alt Fire** `falloff`: damage is a near/far pair but no falloff range was found

## Release date not found on the wiki page (3)

- Junker Queen
- Juno
- Sojourn

## Hero colours derived from a portrait (faces skew these towards skin tone) (20)

- Anran
- Domina
- Emre
- Freja
- Hazard
- Illari
- Jetpack Cat
- Junker Queen
- Juno
- Kiriko
- Lifeweaver
- Mauga
- Mizuki
- Ramattra
- Shion
- Sierra
- Sojourn
- Vendetta
- Venture
- Wuyang
