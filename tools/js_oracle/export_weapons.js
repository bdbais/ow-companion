// Exports the 2020 weapon set as JSON for the Kotlin engine's test fixture.
//
//   node tools/js_oracle/export_weapons.js
//     -> app/src/test/resources/weapons-2020.json
//
// weapons.js normalises its own data on load (defaulting crit factors, deriving shot_time
// from fire_rate, converting beams from dps to damage-per-tick, ...). That normalisation is
// treated as a *dataset build* concern, not an engine concern, so what gets exported here
// is the already-normalised form — exactly what the Kotlin engine expects to consume, and
// the same shape the 2026 Python pipeline has to produce.

const fs = require('fs');
const path = require('path');
const { loadContext } = require('./oracle');

const OUT = path.join(__dirname, '..', '..', 'app', 'src', 'test', 'resources', 'weapons-2020.json');

const context = loadContext();
const { weapons, heros } = context;

// Infinity has no JSON representation; the engine reads null as "unlimited".
function num(value) {
  if (value == null) return null;
  return Number.isFinite(value) ? value : null;
}

function numArray(value) {
  if (value == null) return null;
  return Array.isArray(value) ? value.map(num) : [num(value)];
}

function exportDamage(d) {
  if (!d) return null;
  return {
    dpshot: numArray(d.dpshot),
    falloff: numArray(d.falloff),
    maxRange: num(d.max_range),
    segments: num(d.segments),
    duration: num(d.duration),
    dps: num(d.dps),
    dpsFactors: d.dps_factors ? d.dps_factors.map(num) : null,
    levelChargingTime: num(d.level_charging_time),
    dpshotBall: num(d.dpshot_ball),
    rangeBall: num(d.range_ball),
  };
}

function exportSpread(s) {
  if (!s) return null;
  return {
    angle: num(s.angle),
    minAngle: num(s.min_angle),
    maxAngle: num(s.max_angle),
    spreadingAmmoRange: s.spreading_ammo_range ? s.spreading_ammo_range.map(num) : null,
    constantAngles: s.constant_angles ? s.constant_angles.map((a) => a.map(num)) : null,
    randomlyRotated: !!s.randomly_rotated,
    fixedAngle: num(s.fixed_angle),
  };
}

// weapons.coffee hangs a handful of bespoke damage/timing functions off individual weapons
// by name. The engine cannot rediscover that from the numbers, so it is made explicit here
// and the 2026 pipeline has to set it too.
function behaviorOf(weapon) {
  switch (weapon.name) {
    case 'Scrap Gun (secondary)':
      return 'scrapGunSecondary';
    case 'Particle Cannon (primary)':
    case 'Particle Cannon (secondary)':
      return 'particleCannon';
    case 'Photon Projector':
      return 'photonProjector';
    case 'Hand Cannon':
      return 'handCannon';
    default:
      return 'standard';
  }
}

const heroList = Object.keys(heros).map((key) => ({
  key,
  name: heros[key].name,
  color: heros[key].color,
  role: heros[key].role,
}));

const weaponList = weapons.map((w) => ({
  name: w.name,
  hero: w.hero.name,
  mousebutton: w.mousebutton || null,
  type: w.type,
  behavior: behaviorOf(w),
  pellets: numArray(w.pellets),
  damage: exportDamage(w.damage),
  spread: exportSpread(w.spread),
  velocity: num(w.velocity),
  fireRate: num(w.fire_rate),
  shotTime: num(w.shot_time),
  tickRate: num(w.tick_rate),
  ammoUsage: num(w.ammo_usage),
  ammo: num(w.ammo),
  reloadTime: num(w.reload_time),
  chargeDelay: num(w.charge_delay),
  critFactor: num(w.crit_factor),
  burst: w.burst ? { ammo: num(w.burst.ammo), delay: num(w.burst.delay) } : null,
  energy: num(w.energy),
  energyFactor: num(w.energy_factor),
  dpsPeriodBase: num(w.dps_period_base),
  dpsPeriodAdd: num(w.dps_period_add),
  filling: num(w.filling),
}));

const out = {
  meta: {
    source: 'yfp/owdmgchart public/weapons.js (MIT)',
    patch: '29 Sep 2020',
    generatedBy: 'tools/js_oracle/export_weapons.js',
    normalized: true,
  },
  heroes: heroList,
  weapons: weaponList,
};

fs.mkdirSync(path.dirname(OUT), { recursive: true });
fs.writeFileSync(OUT, JSON.stringify(out, null, 1));
console.log(`wrote ${OUT}: ${heroList.length} heroes, ${weaponList.length} weapons`);
