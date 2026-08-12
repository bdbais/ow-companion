# Reported by players, not yet fixed

Eight reports from someone who plays the game, triaged against the data. Kept here rather
than in a message so none of it is lost, and so each one records what was actually checked
rather than what was assumed.

---

## 1. Damage reduction is missing for several heroes

The modifiers already carry Orisa's Fortify and Roadhog's Take a Breather, but not
Doomfist's Power Block, Hazard's and Ramattra's blocks, or Mauga's Overrun.

**Assessment: correct, and the fix is cheap.** These are the same shape as the two already
there - a flat reduction while active - so each is a field and a percentage. The work is in
sourcing the percentage per hero from the wiki rather than in the code.

## 2. A burning target as a modifier

Would matter for exactly two heroes: Mauga's Volatile Chaingun and Anran's Fan the Flames
both do more to a target already alight.

**Assessment: correct and worth doing precisely because it is narrow.** A modifier that
changes two weapons and nothing else is easy to verify. It belongs beside the others rather
than hidden in those weapons' numbers, because whether the target is burning is a state of
the fight, not a property of the gun.

## 3. Mauga's two guns firing together are missing from the chart

The chart shows each barrel on its own; it never shows what both do at once, which is how
he is actually played.

**Assessment: correct.** The simulator models one weapon at a time. Two guns firing
together is a genuinely new shape - two independent cycles overlaid - and is the largest
piece of work on this list.

## 4. Incendiary weapons do not set the target alight

Mauga's Incendiary Chaingun and Anran's Fan the Flames behave as if a run of hits never
ignites anything.

**Assessment: correct, and it is the same gap as 2.** The burn is a state applied after a
threshold of hits, so it needs the shot sequence rather than a per-shot multiplier. Fixing
2 and 4 together is the sensible order.

## 5. Perks

Some are trivial to model - Ashe's Viper's Sting, an extra 25 every second hit; Ana's
headshot perk. Others are situational to the point of being meaningless on a chart: Echo's
beam range, Sion's execution damage under half health, Vendetta's attack speed scaling with
hits, Lifeweaver's damage perk, Roadhog's falloff, Ramattra's range.

**Assessment: correct that it would get complex, and the reporter has already drawn the
right line.** The ones that change a weapon's numbers unconditionally are worth modelling.
The conditional ones are not, and shipping half of them silently would make the chart less
trustworthy rather than more. If this is done, the perks modelled must be named on screen
and the rest listed as excluded - the same treatment ultimates already get.

## 6. Sojourn's charged shot fires far too often — fixed in 1.7.9

It behaves as though her ultimate were permanently active.

**Assessment: this looks like a real defect and is the most likely to be wrong in the data
rather than in the model.** Her railgun charges from primary fire hits; if the spec carries
the ultimate's rate as the base rate, the chart will show exactly this.

**What it turned out to be.** The 0.64 seconds in the dataset is the *recovery* after the
shot, recorded as though it were a rate. Nothing gated it, so the chart fired a 120 damage
railgun round six times in four seconds, forever.

The energy has to come from somewhere, and the wiki says where: 5 per body hit from the
primary, 100 for a full charge. Twenty hits from a 16 shot/s railgun is 1.25 s, and the
0.64 s recovery follows, so 1.89 s is the fastest a charged shot can be repeated - and that
assumes every single primary round lands on a body. The damage now scales with the charge
the way the wiki describes it, 1 per point of energy over a base of 20, so 21 at one point
and 120 at a hundred. Falloff still applies on top: a charged weapon that also falls off
was not something the simulator could express before.

The reporter's fuller suggestion - a combined view that fires primary until the charge
fills, spends it, and repeats - is still the right end state, and is the same shape as 3.

## 7. Symmetra fires her secondary at zero charge — fixed in 1.7.9

**Assessment: likely the same class of defect as 6** - a charge-dependent weapon evaluated
at the wrong charge level. Worth checking alongside it.

**What it turned out to be.** Worse than reported, and in three more places. The orb was
recorded at full charge only - 0.8 shots per second and a magazine of ten - while the wiki
gives both ends: 3.9 shots/s uncharged with 0.256 s of recovery, 0.8 charged with a second
of charging and 0.25 of recovery, and 100 ammo draining 1 to 10 a shot. Its projectile speed
was recorded as 25 m/s where the wiki says 50.

The charge setting now drives the rate of fire and the ammo as well as the damage, which
needed a new idea in the simulator: a weapon whose firing cycle depends on how wound up it
is. Uncharged she now spams cheap orbs quickly, charged she throws one heavy one - which is
what the two lines on the wiki have always said.

## 8. Weapons wrongly classified as shotguns — verified

The category is assigned to anything firing more than one projectile, which is not the same
thing. Twenty weapons carry it today:

| Hero | Weapon | Pellets |
|---|---|---|
| Sierra | Helix Rifle | 2 |
| Echo | Tri-Shot | 3 |
| Baptiste | Biotic Launcher | 3 |
| Emre | Synthetic Burst Rifle (and ADS) | 3 |
| Juno | Mediblaster | 12 |
| Genji | Shuriken (and Alt Fire) | 3 |
| Tracer | Pulse Pistols | 2 |
| Lúcio | Sonic Amplifier | 4 |
| Sigma | Hyperspheres | 2 |
| Lifeweaver | Thorn Volley | 2 |

Every one named in the report is in that list, plus five more that are the same mistake. A
shotgun fires a spread of pellets in one instant; a burst rifle fires them in sequence, and
Tracer's two pistols are two guns. The distinction is not cosmetic - it decides how armour
taxes the weapon, which the shot counts now depend on.

**This is the one to fix first:** it is a data error rather than a missing feature, it makes
a filter lie, and it is already affecting a number the app publishes.

## 9. Anran's Fan the Flames is a beam, not a projectile — verified

It carries `type = "projectile shotgun"` with six pellets and a hard 10 metre cutoff.

**Assessment: correct, and it compounds 8.** A flamethrower is a beam: continuous, ticking,
and mitigated by armour at a flat 20% rather than losing 3 points per pellet. Modelled as
six projectiles it is taxed six times over by armour, so the shot counts against any
armoured target are wrong for her specifically.

Fixing 8 and 9 together is the right order: both are the same parser assumption, that a
weapon listing several projectiles is a shotgun.

## 10. Damage still appears at unreachable range — verified

**37 of 129 weapons carry neither a falloff nor a maximum range**, so nothing ever reduces
them: push the distance slider to its end and they keep dealing full damage.

Some are honest - a hitscan sniper really does not lose damage - but the list includes
projectile weapons that certainly do not reach across a map:

| Hero | Weapon | Type |
|---|---|---|
| Anran | Zhuque Fans | linear projectile |
| Echo | Tri-Shot | projectile shotgun |
| Genji | Shuriken, Shuriken Alt Fire | projectile shotgun |
| Bastion | Configuration: Assault | linear projectile |
| D.Va | Light Gun | linear projectile |
| Brigitte | Rocket Flail | melee |

A melee weapon with no maximum range is plainly wrong - it is the same class of defect that
quick melee had before it was given its 2.5 metres.

**Assessment: correct, and it undermines the chart's central claim.** The whole point of
the distance slider is that damage answers to it. Where the wiki states no falloff the app
should say the range is unknown rather than draw full damage at fifty metres.

---

## Suggested order

1. **8 and 9** together - one wrong assumption in the parser, and it already affects the
   published shot counts.
2. **10** - decide what an absent falloff means, and say so on screen.
3. **6 and 7** - charge-dependent weapons evaluated at the wrong charge.
4. **1** - the missing damage reductions, which are cheap.
5. **2 and 4** - burning, which needs the shot sequence.
6. **3** - two weapons at once.
7. **5** - perks, and only the unconditional ones.

## 11. Heroes who fight with melee are misrepresented

Reinhardt, Vendetta and Ramattra's Nemesis form all have melee as their actual weapon, not
as the universal quick melee every hero shares.

**Assessment: correct, and it is the same defect as 10 seen from another side.** A melee
weapon with no maximum range is drawn as dealing full damage across the map. For heroes
whose whole game is melee that is not a rounding error - it is their entire line on the
chart being wrong, and it will have flattered them in the rankings.

## 12. Some heroes have more than one melee

Junker Queen's melee differs depending on whether she is holding her knife. Jetpack Cat's
melee causes bleeding when the scratch perk is taken.

**Assessment: correct, and it is where 5 and 11 meet.** One is a state of the weapon and
the other is a perk, but both mean a hero has two melee attacks where the dataset records
one. Whichever is stored is being presented as the only one.

The honest treatment is the one already used elsewhere: record both, name the condition,
and let the reader pick - rather than silently shipping whichever the wiki listed first.
