
  //### Enemy shooting

  //constants
  CRIT = 2;

  HIT = 1;

  MISS = 0;

  basicCursorHeight = 1; //m

  
  //global
  multiplier = 1;

  crosshair = {
    x: 0,
    z: 1.0,
    distance: 5.0
  };

  HitBox = class HitBox {
    registerHit() {
      return false;
    }

    appendElement(g) {
      return g;
    }

  };

  RectHitBox = class RectHitBox extends HitBox {
    constructor(cx, cz, width, height) {
      super();
      this.x1 = cx - width / 2;
      this.x2 = cx + width / 2;
      this.z1 = cz - height / 2;
      this.z2 = cz + height / 2;
    }

    registerHit(p) {
      return this.x1 <= p.x && p.x <= this.x2 && this.z1 <= p.z && p.z <= this.z2;
    }

    appendElement(g) {
      return g.append('rect').attrs({
        x: this.x1,
        y: basicCursorHeight - this.z2,
        width: this.x2 - this.x1,
        height: this.z2 - this.z1
      });
    }

  };

  CircleHitBox = class CircleHitBox extends HitBox {
    constructor(x1, z1, radius1) {
      super();
      this.x = x1;
      this.z = z1;
      this.radius = radius1;
      this.radius_squared = this.radius * this.radius;
    }

    registerHit(p) {
      var dx, dz;
      dx = p.x - this.x;
      dz = p.z - this.z;
      return dx * dx + dz * dz <= this.radius_squared;
    }

    appendElement(g) {
      return g.append('circle').attrs({
        cx: this.x,
        cy: basicCursorHeight - this.z,
        r: this.radius
      });
    }

  };

  Enemy = class Enemy {
    constructor(image, body, head) {
      this.image = image;
      this.body = body;
      this.head = head;
    }

    registerHit(point) {
      switch (false) {
        case !this.head.registerHit(point):
          return CRIT;
        case !this.body.registerHit(point):
          return HIT;
        default:
          return MISS;
      }
    }

    shoot(crosshair, radius = 0, shift_x = 0, shift_z = 0) {
      var cx, cz, phi, r, x, z;
      cx = cz = 0;
      if (radius > 0) {
        phi = 2 * Math.PI * Math.random();
        r = radius * Math.random();
        cx = r * Math.cos(phi);
        cz = r * Math.sin(phi);
      }
      x = crosshair.x + cx + shift_x;
      z = crosshair.z + cz + shift_z;
      return this.registerHit({
        x: x,
        z: z
      });
    }

  };

  enemy_Roadhog = (function() {
    var body_height, body_width, head_height, head_width;
    body_width = 1.4;
    body_height = 2.1;
    head_height = 2.0;
    head_width = 0.6;
    return new Enemy({
      url: "images/roadhog_figure_lighter.png",
      dims: {
        width: 858,
        height: 873
      },
      x: 1.55,
      y: 1.6,
      height: 3.3
    }, new RectHitBox(0, body_height / 2, body_width, body_height), new CircleHitBox(0, head_height, head_width / 2));
  })();

  
  // ht = 2.21
  // basicRectangle = new RectHitBox(0,ht/2,2,ht)
  enemy = enemy_Roadhog;

  HOG_HP = 600;

  WeaponData = (function() {
    var total_time;

    class WeaponData {
      // draw_time = 1.2*max_time
      constructor(weapon1) {
        var ref, shot_spacing;
        this.weapon = weapon1;
        this.order = this.weapon.index + 1;
        shot_spacing = timescale * (((ref = this.weapon.burst) != null ? ref.delay : void 0) || this.weapon.shot_time);
        this.filling = this.weapon.filling || 0.5;
        this.is_beam = this.weapon.type === "beam";
        this.is_beam_or_melee = this.weapon.type === "beam" || this.weapon.type === "melee";
        this.color = this.weapon.hero.color;
        this.segments_factor = this.weapon.damage.segments || 1;
        if (!this.is_beam) {
          this.max_shot_width = shot_spacing * this.filling;
          this.max_square_dmg = this.max_shot_width * this.max_shot_width / areascale;
        }
      }

      refresh_distance(enemy, crosshair) {
        this.set_distance(crosshair.distance);
        this.init_shots();
        this.simulate_shot_outcomes(enemy, crosshair);
        return this.calculate_shots_damage();
      }

      refresh_crosshair(enemy, crosshair) {
        this.simulate_shot_outcomes(enemy, crosshair);
        return this.calculate_shots_damage();
      }

      set_distance(distance) {
        this.distance = distance;
        this.pellets = this.weapon.pellets_func != null ? this.weapon.pellets_func(distance) : this.weapon.pellets;
        this.basic_dmg = this.weapon.basic_damage_func(distance);
        this.radius_func = this.weapon.make_radius_func(distance);
        this.time_delay = this.weapon.time_delay_func(distance);
        return this.shift_func = this.weapon.make_shift_func(distance);
      }

      init_shots() {
        var ammo, ammo_consumed, dt, results, shot, t, total_dmg;
        t = 0;
        this.shots = [];
        ammo = this.weapon.ammo;
        total_dmg = 0;
        results = [];
        while (t < total_time) {
          shot = {
            radius: this.radius_func(ammo, t)
          };
          [ammo, dt, ammo_consumed] = this.weapon.shot_time_func(ammo, t);
          shot.t = t + this.time_delay;
          shot.wdata = this;
          if (ammo_consumed > 1) {
            shot.ammo_mult = ammo_consumed;
          }
          if (this.weapon.type === "beam") {
            shot.duration = this.weapon.shot_time * ammo_consumed;
          }
          if (this.weapon.hero.name === 'Ana') {
            shot.duration = this.weapon.damage.duration;
          }
          this.shots.push(shot);
          results.push(t += dt);
        }
        return results;
      }

      simulate_shot_outcomes(enemy, crosshair) {
        var COS, SIN, hit_outcome, i, j, k, len, o, outcomes, random_angle, ref, ref1, ref2, shift, shot, total, total_outcomes;
        total_outcomes = [0, 0, 0];
        ref = this.shots;
        for (j = 0, len = ref.length; j < len; j++) {
          shot = ref[j];
          outcomes = [0, 0, 0];
          if ((ref1 = this.weapon.spread) != null ? ref1.randomly_rotated : void 0) {
            random_angle = 2 * Math.PI * Math.random();
            SIN = Math.sin(random_angle);
            COS = Math.cos(random_angle);
          } else {
            SIN = 0;
            COS = 1;
          }
          for (i = k = 1, ref2 = this.pellets; (1 <= ref2 ? k <= ref2 : k >= ref2); i = 1 <= ref2 ? ++k : --k) {
            shift = this.shift_func(i);
            hit_outcome = enemy.shoot(crosshair, shot.radius, COS * shift[0] - SIN * shift[1], COS * shift[1] + SIN * shift[0]);
            outcomes[hit_outcome] += 1;
          }
          if (this.weapon.crit_factor === 1) {
            outcomes[HIT] += outcomes[CRIT];
            outcomes[CRIT] = 0;
          }
          shot.outcomes = outcomes;
          total_outcomes[MISS] += outcomes[MISS];
          total_outcomes[HIT] += outcomes[HIT];
          total_outcomes[CRIT] += outcomes[CRIT];
        }
        total = total_outcomes[MISS] + total_outcomes[HIT] + total_outcomes[CRIT];
        return this.outcomes = (function() {
          var l, len1, results;
          results = [];
          for (l = 0, len1 = total_outcomes.length; l < len1; l++) {
            o = total_outcomes[l];
            results.push(o / total);
          }
          return results;
        })();
      }

      calculate_shots_damage() {
        var h, index, j, k, key, last_shot, len, len1, mean_damage, ref, ref1, shot, time, total_dmg;
        this.height = 0;
        this.hit_dmg = this.basic_dmg * (this.is_beam_or_melee ? modificator.factor_mb : modificator.factor);
        this.crit_dmg = this.hit_dmg * this.weapon.crit_factor;
        if (modificator.mods.armor.on) {
          ref = ['hit_dmg', 'crit_dmg'];
          for (j = 0, len = ref.length; j < len; j++) {
            key = ref[j];
            this[key] = modificator.mods.armor.func(this[key], this.is_beam);
          }
        }
        total_dmg = 0;
        this.rhkt = void 0;
        ref1 = this.shots;
        for (index = k = 0, len1 = ref1.length; k < len1; index = ++k) {
          shot = ref1[index];
          shot.damage = (shot.outcomes[HIT] * this.hit_dmg + shot.outcomes[CRIT] * this.crit_dmg) * this.segments_factor;
          h = this.shot_dimensions(shot);
          total_dmg += shot.damage;
          if (total_dmg >= HOG_HP) {
            if (this.rhkt == null) {
              this.rhkt = shot.t + (shot.duration != null ? shot.duration * (HOG_HP + shot.damage - total_dmg) / shot.damage : 0);
            }
          }
          if (h > this.height) {
            this.height = h;
          }
        }
        last_shot = this.shots[this.shots.length - 1];
        time = last_shot.t;
        if (last_shot.duration != null) {
          time += last_shot.duration;
        }
        this.dps_raw = total_dmg / time;
        mean_damage = (this.hit_dmg * this.outcomes[HIT] + this.crit_dmg * this.outcomes[CRIT]) * this.pellets * this.segments_factor;
        this.dps_wort = mean_damage / this.weapon.dps_period_base;
        this.dps = mean_damage / (this.weapon.dps_period_base + this.weapon.dps_period_add);
        this.accuracy = total_dmg > 0 ? this.outcomes[HIT] + this.outcomes[CRIT] : 0;
        this.crit_accuracy = total_dmg > 0 ? this.outcomes[CRIT] : 0;
        if (this.rhkt == null) {
          this.rhkt = this.dps > 0 ? HOG_HP / this.dps : 2e308;
        }
        return this.height = 2 * Math.ceil(this.height / 2);
      }

      shot_dimensions(shot) {
        if (shot.damage > this.max_square_dmg) {
          shot.width = this.max_shot_width;
          shot.height = areascale * shot.damage / shot.width;
        } else {
          shot.width = Math.sqrt(areascale * shot.damage);
          shot.height = shot.width;
        }
        return shot.height;
      }

    };

    total_time = 1.2 * max_time;

    return WeaponData;

  }).call(this);

  BeamWeaponData = class BeamWeaponData extends WeaponData {
    shot_dimensions(shot) {
      shot.damage *= shot.duration * this.weapon.fire_rate;
      shot.dps = shot.damage / shot.duration;
      return shot.height = shot.dps * areascale / timescale;
    }

  };

  //beam mech
  BioticRifleWeaponData = class BioticRifleWeaponData extends WeaponData {
    shot_dimensions(shot) {
      var dps;
      dps = shot.damage / shot.duration;
      return shot.height = dps * areascale / timescale;
    }

  };

  //beam mech
  PhotonProjectorWeaponData = class PhotonProjectorWeaponData extends BeamWeaponData {
    calculate_shots_damage() {
      var basic_dmg, dmg, factor, h, index, j, k, last_shot, len, len1, mean_damage, ref, ref1, shot, time, total_dmg;
      this.height = 30;
      this.hit_dmg = this.basic_dmg * modificator.factor_mb;
      this.dmg_levels = [];
      ref = this.weapon.damage.dps_factors;
      for (j = 0, len = ref.length; j < len; j++) {
        factor = ref[j];
        this.dmg_levels.push(this.hit_dmg * factor);
      }
      if (modificator.mods.armor.on) {
        this.dmg_levels = (function() {
          var k, len1, ref1, results;
          ref1 = this.dmg_levels;
          results = [];
          for (k = 0, len1 = ref1.length; k < len1; k++) {
            dmg = ref1[k];
            results.push(modificator.mods.armor.func(dmg, false));
          }
          return results;
        }).call(this);
      }
      total_dmg = 0;
      this.rhkt = void 0;
      ref1 = this.shots;
      for (index = k = 0, len1 = ref1.length; k < len1; index = ++k) {
        shot = ref1[index];
        basic_dmg = index >= this.dmg_levels.length ? this.dmg_levels[this.dmg_levels.length - 1] : this.dmg_levels[index];
        shot.damage = shot.outcomes[HIT] * basic_dmg;
        h = this.shot_dimensions(shot);
        total_dmg += shot.damage;
        if (this.rhkt == null) {
          this.rhkt = total_dmg >= HOG_HP ? shot.t + (total_dmg - HOG_HP) / shot.damage * shot.duration : void 0;
        }
        if (h > this.height) {
          this.height = h;
        }
      }
      last_shot = this.shots[this.shots.length - 1];
      time = last_shot.t + last_shot.duration;
      this.dps_raw = total_dmg / time;
      mean_damage = this.outcomes[HIT] * this.dmg_levels[this.dmg_levels.length - 1];
      this.dps_wort = mean_damage / this.weapon.dps_period_base;
      this.dps = mean_damage / (this.weapon.dps_period_base + this.weapon.dps_period_add);
      this.accuracy = total_dmg > 0 ? 1 : 0;
      this.crit_accuracy = 0;
      return this.height = 2 * Math.ceil(this.height / 2);
    }

  };
