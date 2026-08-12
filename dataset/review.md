# Dataset review

Everything the pipeline could not read with confidence. Fix these by adding an
entry to `dataset/overrides.json` - corrections there survive re-running the
pipeline, edits to the generated dataset do not.

An override is keyed by `"Hero|Weapon name"` and merges over the parsed weapon:

```json
{ "weapons": { "Ana|Biotic Rifle": { "fireRate": 1.25, "reviewed": true } } }
```

## Weapons excluded from the chart until corrected (2)

- **D.Mon — Plasma Saber** missing fireRate
- **D.Mon — Portable Fusion Repeater** missing fireRate

## Field-level warnings on weapons that are otherwise usable (67)

- **Anran — Zhuque Fans** `damage`: derived from the wiki's stated 73 dps: 22.0 -> 44.0 per pellet — `22 (per shot)`
- **Anran — Fan the Flames** `beam`: beam weapons need their tick rate filled in by hand
- **Ashe — Take Aim (ADS)** `reload`: reload of 2.90 s implies 83 dps overall, but the wiki states 41 — `0.5 seconds (initial animation)`
- **Baptiste — Biotic Launcher Alt Fire** `required`: missing damage
- **Bastion — Configuration: Assault (Lindholm Explosives)** `damage`: derived from the wiki's stated 186 dps: 50.0 -> 140.0 per pellet — `50 (direct hit bonus)`
- **Brigitte — Rocket Flail** `damage`: derived from the wiki's stated 75 dps: 45.0 -> 75.0 per pellet — `45`
- **Domina — Photon Magnum** `damage`: damage is stated as a rate, not per shot — `60 (over time)`
- **Domina — Photon Magnum** `beam`: beam weapons need their tick rate filled in by hand
- **Domina — Photon Magnum** `required`: missing damage, fireRate
- **Doomfist — Hand Cannon** `damage`: derived from the wiki's stated 73 dps: 5.0 -> 2.2 per pellet — `5 – 1.5 (per shot)`
- **Freja — Take Aim** `required`: missing fireRate
- **Hazard — Bonespur** `spread`: several spread values, took the first — `0 degrees (center shot) / 1.8 degrees (5 central shots) / 3.5 degrees (4 corner shots)`
- **Illari — Solar Rifle Alt Fire** `beam`: beam weapons need their tick rate filled in by hand
- **Illari — Solar Rifle Alt Fire** `required`: missing damage, fireRate
- **Jetpack Cat — Biotic Pawjectiles** `spread`: several spread values, took the first — `0 degrees (center shot) / 0.3 degrees (close paw shots) / 0.9 degrees (far paw shows)`
- **Junkrat — Frag Launcher** `damage`: derived from the wiki's stated 180 dps: 45.0 -> 120.0 per pellet — `45 (direct hit)`
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
- **Mizuki — Spirit Glaive** `damage`: derived from the wiki's stated 86 dps: 30.0 -> 59.9 per pellet — `30 (base)`
- **Moira — Biotic Grasp** `beam`: beam weapons need their tick rate filled in by hand
- **Moira — Biotic Grasp** `required`: missing damage, fireRate
- **Moira — Biotic Grasp Alt Fire** `damage`: damage is stated as a rate, not per shot — `65 per second`
- **Moira — Biotic Grasp Alt Fire** `beam`: beam weapons need their tick rate filled in by hand
- **Moira — Biotic Grasp Alt Fire** `required`: missing damage, fireRate
- **Orisa — Augmented Fusion Driver** `reload`: several reload values, took the first — `3 seconds (cooldown after overheat) / 1.9 seconds (recharge from full heat)`
- **Pharah — Rocket Launcher** `damage`: derived from the wiki's stated 150 dps: 40.0 -> 120.0 per pellet — `40 (direct hit)`
- **Roadhog — Scrap Gun Alt Fire** `falloff`: falloff differs by condition, took the first — `None (pre-detonation) / 24 - 39 meters (post-detonation)`
- **Roadhog — Scrap Gun Alt Fire** `falloff`: damage is a near/far pair but no falloff range was found — `None (pre-detonation)
24 - 39 meters (post-detonation)`
- **Roadhog — Scrap Gun Alt Fire** `damage`: derived from the wiki's stated 203 dps: 7.0 -> 162.5 per pellet — `50 (pre-detonation)`
- **Sierra — Helix Rifle** `spread`: blooming spread needs a spreadingAmmoRange — `3 degrees (max) / 0.5 degrees (min)`
- **Sigma — Hyperspheres** `damage`: derived from the wiki's stated 73 dps: 20.0 -> 54.7 per pellet — `20 (direct hit bonus)`
- **Sojourn — Railgun** `spread`: several spread values, took the first — `1.6 degrees (vertical, max) / 0.64 degrees (horizontal, max)`
- **Sojourn — Charged Shot** `required`: missing fireRate
- **Symmetra — Photon Projector** `damage`: damage is stated as a rate, not per shot — `60/120/180 per second (level 1/2/3)`
- **Symmetra — Photon Projector** `beam`: beam weapons need their tick rate filled in by hand
- **Symmetra — Photon Projector** `required`: missing damage, fireRate
- **Symmetra — Photon Projector Alt Fire** `falloff`: damage is a near/far pair but no falloff range was found
- **Symmetra — Photon Projector Alt Fire** `damage`: derived from the wiki's stated 91 dps: 5.0 -> 23.3 per pellet — `5 - 100 (direct hit, min - max charge)`
- **Symmetra — Sentry Turret** `damage`: damage is stated as a rate, not per shot — `30 per second`
- **Symmetra — Sentry Turret** `beam`: beam weapons need their tick rate filled in by hand
- **Symmetra — Sentry Turret** `required`: missing fireRate
- **Torbjörn — Forge Hammer** `damage`: derived from the wiki's stated 100 dps: 70.0 -> 100.0 per pellet — `70`
- **Venture — Smart Excavator** `damage`: derived from the wiki's stated 125 dps: 35.0 -> 74.9 per pellet — `35 (direct hit bonus)`
- **Widowmaker — Widow's Kiss (ADS)** `damage`: derived from the wiki's stated 80 dps: 12.0 -> 160.0 per pellet — `12 - 6 (at 0% power)`
- **Widowmaker — Venom Mine** `damage`: damage is stated as a rate, not per shot — `25 per second`
- **Widowmaker — Venom Mine** `required`: missing fireRate
- **Winston — Tesla Cannon** `damage`: damage is stated as a rate, not per shot — `70 per second`
- **Winston — Tesla Cannon** `beam`: beam weapons need their tick rate filled in by hand
- **Winston — Tesla Cannon** `required`: missing damage, fireRate
- **Winston — Tesla Cannon Alt Fire** `required`: missing fireRate
- **Wuyang — Xuanwu Staff** `falloff`: falloff differs by condition, took the first — `0.8 - 1.3 meters (splash, uncharged) / 0.8 - 2.7 meters (splash, charged)`
- **Wuyang — Xuanwu Staff** `damage`: derived from the wiki's stated 91 dps: 10.0 -> 30.3 per pellet — `10 (direct hit bonus, uncharged)`
- **Zarya — Particle Cannon** `damage`: damage is stated as a rate, not per shot — `95 per second (at 0%)`
- **Zarya — Particle Cannon** `beam`: beam weapons need their tick rate filled in by hand
- **Zarya — Particle Cannon** `required`: missing damage, fireRate
- **Zarya — Particle Cannon Alt Fire** `falloff`: damage is a near/far pair but no falloff range was found
- **Zarya — Particle Cannon Alt Fire** `reload`: reload of 1.50 s implies 54 dps overall, but the wiki states 40 — `1.5 seconds`
- **Zenyatta — Orb of Destruction Alt Fire** `damage`: derived from the wiki's stated 78 dps: 50.0 -> 77.6 per pellet — `50 per orb`

## Release date not found on the wiki page (1)

- D.Mon

## Hero colours derived from a portrait (faces skew these towards skin tone) (21)

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
- D.Mon
