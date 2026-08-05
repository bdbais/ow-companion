// Produces the golden values the Kotlin simulation engine is tested against.
//
//   node tools/js_oracle/generate_golden.js
//     -> app/src/test/resources/golden-2020.json
//
// The upstream model is partly stochastic: weapons with spread sample a random point
// inside the spread cone for every pellet of every shot. Rather than trying to make two
// languages agree on a random sequence, each case is run `TRIALS` times and the mean is
// recorded together with the standard error, so the Kotlin test can require:
//   - exact agreement for the deterministic quantities (damage per hit, shot timing,
//     spread radius, shot count) — these do not depend on the RNG at all;
//   - agreement within a few standard errors for the sampled ones (dps, accuracy).

const fs = require('fs');
const path = require('path');
const { loadContext, weaponDataClass } = require('./oracle');

const TRIALS = 400;
const OUT = path.join(__dirname, '..', '..', 'app', 'src', 'test', 'resources', 'golden-2020.json');

// distance in metres, crosshair (x, z) in metres relative to the target's feet.
// The Roadhog target's body spans z 0..2.1 and its head is a circle of r=0.3 at z=2.0.
const CONFIGS = [
  { name: 'point_blank', distance: 0.5, x: 0, z: 1.0, mods: [] },
  { name: 'close_body', distance: 5, x: 0, z: 1.0, mods: [] },
  { name: 'mid_body', distance: 12, x: 0, z: 1.0, mods: [] },
  { name: 'mid_head', distance: 20, x: 0, z: 2.0, mods: [] },
  { name: 'far_body', distance: 35, x: 0, z: 1.0, mods: [] },
  { name: 'very_far_body', distance: 50, x: 0, z: 1.0, mods: [] },
  { name: 'offset_aim', distance: 15, x: 0.5, z: 1.5, mods: [] },
  { name: 'armor', distance: 8, x: 0, z: 1.0, mods: ['armor'] },
  { name: 'damage_boost', distance: 8, x: 0, z: 1.0, mods: ['damage_boost'] },
  { name: 'discord_boost_head', distance: 8, x: 0, z: 2.0, mods: ['discord', 'damage_boost'] },
  { name: 'nano_defence', distance: 8, x: 0, z: 1.0, mods: ['nanoboost_def'] },
];

const context = loadContext();
const core = context.__core;
const { weapons, modificator } = context;

function applyMods(names) {
  for (const mod of modificator.mod_list) {
    mod.on = names.includes(mod.name);
  }
  modificator.refresh_factor();
}

// A weapon's outcome sampling is deterministic when no pellet can land anywhere but the
// crosshair: no spread cone and no random rotation of fixed pellet offsets.
function isDeterministic(weapon) {
  const spread = weapon.spread;
  if (!spread) return true;
  if (spread.randomly_rotated) return false;
  return spread.angle == null && spread.max_angle == null;
}

function finite(value) {
  return Number.isFinite(value) ? value : null;
}

function runCase(weapon, config) {
  const crosshair = { x: config.x, z: config.z, distance: config.distance };
  const Klass = weaponDataClass(weapon, core);
  const deterministic = isDeterministic(weapon);
  const trials = deterministic ? 1 : TRIALS;

  const dps = [];
  const accuracy = [];
  const critAccuracy = [];
  const rhkt = [];
  let sample = null;

  for (let i = 0; i < trials; i++) {
    const wdata = new Klass(weapon);
    wdata.refresh_distance(core.enemy, crosshair);
    dps.push(wdata.dps);
    accuracy.push(wdata.accuracy);
    critAccuracy.push(wdata.crit_accuracy);
    rhkt.push(wdata.rhkt);
    if (i === 0) sample = wdata;
  }

  const mean = (xs) => xs.reduce((a, b) => a + b, 0) / xs.length;
  const stdErr = (xs) => {
    if (xs.length < 2) return 0;
    const m = mean(xs);
    const variance = xs.reduce((a, b) => a + (b - m) * (b - m), 0) / (xs.length - 1);
    return Math.sqrt(variance / xs.length);
  };

  const finiteRhkt = rhkt.filter(Number.isFinite);

  return {
    config: config.name,
    deterministic,
    // Deterministic, RNG-independent quantities: these must match exactly.
    basicDmg: sample.basic_dmg,
    hitDmg: sample.hit_dmg,
    critDmg: finite(sample.crit_dmg),
    pellets: finite(sample.pellets),
    shotCount: sample.shots.length,
    shotTimes: sample.shots.slice(0, 8).map((s) => s.t),
    shotRadii: sample.shots.slice(0, 8).map((s) => s.radius),
    shotDurations: sample.shots.slice(0, 8).map((s) => finite(s.duration)),
    // Sampled quantities: mean over `trials` runs.
    trials,
    dps: mean(dps),
    dpsStdErr: stdErr(dps),
    accuracy: mean(accuracy),
    accuracyStdErr: stdErr(accuracy),
    critAccuracy: mean(critAccuracy),
    critAccuracyStdErr: stdErr(critAccuracy),
    // rhkt = time to kill a 600 hp Roadhog; Infinity when the weapon cannot reach it.
    rhkt: finiteRhkt.length ? mean(finiteRhkt) : null,
    rhktInfiniteRatio: (rhkt.length - finiteRhkt.length) / rhkt.length,
  };
}

const out = {
  meta: {
    source: 'yfp/owdmgchart public/weapons.js + public/chart.js (MIT)',
    patch: '29 Sep 2020',
    generatedBy: 'tools/js_oracle/generate_golden.js',
    trials: TRIALS,
    note:
      'Zarya energy is left at its default of 0. Deterministic cases are run once; ' +
      'stochastic ones are averaged over `trials` runs.',
  },
  configs: CONFIGS,
  weapons: [],
};

for (const weapon of weapons) {
  applyMods([]);
  const entry = {
    name: weapon.name,
    hero: weapon.hero.name,
    type: weapon.type,
    mousebutton: weapon.mousebutton || null,
    cases: [],
  };
  for (const config of CONFIGS) {
    applyMods(config.mods);
    entry.cases.push(runCase(weapon, config));
  }
  out.weapons.push(entry);
}

fs.mkdirSync(path.dirname(OUT), { recursive: true });
fs.writeFileSync(OUT, JSON.stringify(out, null, 1));
console.log(
  `wrote ${OUT}: ${out.weapons.length} weapons x ${CONFIGS.length} configs ` +
    `(${(fs.statSync(OUT).size / 1024).toFixed(0)} kB)`,
);
