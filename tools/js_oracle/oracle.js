// Loads the original owdmgchart implementation outside the browser so it can be used as
// a correctness oracle for the Kotlin port. Nothing here reimplements the model: the
// weapon data and the WeaponData classes are the upstream files, unmodified.

const fs = require('fs');
const path = require('path');
const vm = require('vm');

const VENDOR = path.join(__dirname, 'vendor');

// Constants that live above the extracted slice in public/chart.js.
// timescale/areascale only affect the drawn geometry, but BeamWeaponData#shot_dimensions
// folds them back into shot.damage, so they must match the original exactly.
//   chart.coffee: timescale = 60 px/sec, max_time = 17.5 sec, areascale = 2 px^2/hp
//   and the d3 scale it is recomputed from yields the same 60.
const PREAMBLE = `
  const timescale = 60;
  const max_time = 17.5;
  const areascale = 2;
  const radians = Math.PI / 180;
`;

function loadContext() {
  const context = vm.createContext({ Math, Infinity, console });

  // weapons.js assigns heros/weapons/weapon_dict/modificator onto its `this`.
  const weaponsSrc = fs.readFileSync(path.join(VENDOR, 'weapons.js'), 'utf8');
  vm.runInContext(weaponsSrc, context, { filename: 'weapons.js' });

  const coreBody = fs.readFileSync(path.join(VENDOR, 'chart-core-body.js'), 'utf8');
  vm.runInContext(
    `${PREAMBLE}\n${coreBody}\n` +
      'this.__core = { WeaponData, BeamWeaponData, BioticRifleWeaponData, ' +
      'PhotonProjectorWeaponData, Enemy, RectHitBox, CircleHitBox, enemy, HOG_HP, ' +
      'CRIT, HIT, MISS };',
    context,
    { filename: 'chart-core-body.js' },
  );

  return context;
}

// Mirrors the `state_data` switch in chart.coffee: the WeaponData subclass is chosen from
// the weapon's name and type, not from a field on the weapon.
function weaponDataClass(weapon, core) {
  if (weapon.name === 'Photon Projector') return core.PhotonProjectorWeaponData;
  if (weapon.type === 'beam') return core.BeamWeaponData;
  if (/EOT/.test(weapon.type)) return core.BioticRifleWeaponData;
  return core.WeaponData;
}

module.exports = { loadContext, weaponDataClass };
