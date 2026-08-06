# Dataset review

Everything the pipeline could not read with confidence. Fix these by adding an
entry to `dataset/overrides.json` - corrections there survive re-running the
pipeline, edits to the generated dataset do not.

An override is keyed by `"Hero|Weapon name"` and merges over the parsed weapon:

```json
{ "weapons": { "Ana|Biotic Rifle": { "fireRate": 1.25, "reviewed": true } } }
```

## Field-level warnings on weapons that are otherwise usable (44)

- **Anran — Fan the Flames** `beam`: beam weapons need their tick rate filled in by hand
- **Baptiste — Biotic Launcher Alt Fire** `required`: missing damage
- **Domina — Photon Magnum** `damage`: damage is stated as a rate, not per shot — `60 (over time)`
- **Domina — Photon Magnum** `beam`: beam weapons need their tick rate filled in by hand
- **Domina — Photon Magnum** `required`: missing damage, fireRate
- **Freja — Take Aim** `required`: missing fireRate
- **Hazard — Bonespur** `spread`: several spread values, took the first — `0 degrees (center shot) / 1.8 degrees (5 central shots) / 3.5 degrees (4 corner shots)`
- **Illari — Solar Rifle Alt Fire** `beam`: beam weapons need their tick rate filled in by hand
- **Illari — Solar Rifle Alt Fire** `required`: missing damage, fireRate
- **Jetpack Cat — Biotic Pawjectiles** `spread`: several spread values, took the first — `0 degrees (center shot) / 0.3 degrees (close paw shots) / 0.9 degrees (far paw shows)`
- **Kiriko — Healing Ofuda** `required`: missing damage
- **Lifeweaver — Healing Blossom** `required`: missing damage
- **Mauga — Incendiary Chaingun** `falloff`: falloff differs by condition, took the first — `30 - 40 meters / 15 - 20 meters (simultaneous fire)`
- **Mauga — Incendiary Chaingun** `spread`: blooming spread needs a spreadingAmmoRange — `1 degree (base) / 1.5 degrees (max) / 4 degrees (simultaneous fire)`
- **Mauga — Volatile Chaingun** `falloff`: falloff differs by condition, took the first — `30 - 40 meters / 15 - 20 meters (simultaneous fire)`
- **Mauga — Volatile Chaingun** `spread`: blooming spread needs a spreadingAmmoRange — `1 degree (base) / 1.5 degrees (max) / 4 degrees (simultaneous fire)`
- **Mei — Endothermic Blaster** `beam`: beam weapons need their tick rate filled in by hand
- **Mercy — Caduceus Staff** `beam`: beam weapons need their tick rate filled in by hand
- **Mercy — Caduceus Staff** `required`: missing damage, fireRate
- **Mercy — Caduceus Staff Alt Fire** `beam`: beam weapons need their tick rate filled in by hand
- **Mercy — Caduceus Staff Alt Fire** `required`: missing damage, fireRate
- **Moira — Biotic Grasp** `beam`: beam weapons need their tick rate filled in by hand
- **Moira — Biotic Grasp** `required`: missing damage, fireRate
- **Moira — Biotic Grasp Alt Fire** `damage`: damage is stated as a rate, not per shot — `65 per second`
- **Moira — Biotic Grasp Alt Fire** `beam`: beam weapons need their tick rate filled in by hand
- **Moira — Biotic Grasp Alt Fire** `required`: missing damage, fireRate
- **Roadhog — Scrap Gun Alt Fire** `falloff`: falloff differs by condition, took the first — `None (pre-detonation) / 24 - 39 meters (post-detonation)`
- **Roadhog — Scrap Gun Alt Fire** `falloff`: damage is a near/far pair but no falloff range was found — `None (pre-detonation)
24 - 39 meters (post-detonation)`
- **Sierra — Helix Rifle** `spread`: blooming spread needs a spreadingAmmoRange — `3 degrees (max) / 0.5 degrees (min)`
- **Sojourn — Railgun** `spread`: several spread values, took the first — `1.6 degrees (vertical, max) / 0.64 degrees (horizontal, max)`
- **Sojourn — Charged Shot** `required`: missing fireRate
- **Symmetra — Photon Projector** `damage`: damage is stated as a rate, not per shot — `60/120/180 per second (level 1/2/3)`
- **Symmetra — Photon Projector** `beam`: beam weapons need their tick rate filled in by hand
- **Symmetra — Photon Projector** `required`: missing damage, fireRate
- **Symmetra — Photon Projector Alt Fire** `falloff`: damage is a near/far pair but no falloff range was found
- **Winston — Tesla Cannon** `damage`: damage is stated as a rate, not per shot — `70 per second`
- **Winston — Tesla Cannon** `beam`: beam weapons need their tick rate filled in by hand
- **Winston — Tesla Cannon** `required`: missing damage, fireRate
- **Winston — Tesla Cannon Alt Fire** `required`: missing fireRate
- **Wuyang — Xuanwu Staff** `falloff`: falloff differs by condition, took the first — `0.8 - 1.3 meters (splash, uncharged) / 0.8 - 2.7 meters (splash, charged)`
- **Zarya — Particle Cannon** `damage`: damage is stated as a rate, not per shot — `95 per second (at 0%)`
- **Zarya — Particle Cannon** `beam`: beam weapons need their tick rate filled in by hand
- **Zarya — Particle Cannon** `required`: missing damage, fireRate
- **Zarya — Particle Cannon Alt Fire** `falloff`: damage is a near/far pair but no falloff range was found

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
