package com.bellizia.owcompanion.ui.chart

import androidx.annotation.StringRes
import com.bellizia.owcompanion.R
import com.bellizia.owcompanion.sim.Hero
import com.bellizia.owcompanion.sim.Modifiers
import com.bellizia.owcompanion.sim.ShotTrain
import com.bellizia.owcompanion.sim.WeaponSpec
import com.bellizia.owcompanion.sim.isChargeScaled

/** Broad weapon families, used for filtering. A weapon can belong to more than one. */
enum class WeaponCategory(@StringRes val labelRes: Int) {
    Hitscan(R.string.weapon_hitscan),
    Projectile(R.string.weapon_projectile),
    Shotgun(R.string.weapon_shotgun),
    Beam(R.string.weapon_beam),
    Melee(R.string.weapon_melee);

    companion object {
        fun of(spec: WeaponSpec): Set<WeaponCategory> = buildSet {
            val type = spec.type
            if (type.contains("melee")) add(Melee)
            if (type.contains("beam")) add(Beam)
            if (type.contains("shotgun")) add(Shotgun)
            if (type.contains("hitscan")) add(Hitscan)
            if (type.contains("projectile")) add(Projectile)
        }
    }
}

/**
 * Which mouse button fires a weapon.
 *
 * A hero with two guns wants comparing one at a time - Ashe scoped against Ashe hip-firing -
 * and without this the only way to do that is to read past every other row.
 */
enum class FireMode(@StringRes val labelRes: Int) {
    Primary(R.string.fire_primary),
    Secondary(R.string.fire_secondary);

    companion object {
        /**
         * The wiki labels two thirds of the weapons and leaves the rest blank. What it
         * leaves blank is melee and transform weapons, every one of which is swung or fired
         * on the left button, so an unlabelled weapon is a primary one.
         */
        fun of(spec: WeaponSpec): FireMode =
            if (spec.mousebutton == "M2") Secondary else Primary
    }
}

enum class HeroRole(@StringRes val labelRes: Int, vararg val keys: String) {
    Tank(R.string.role_tank, "tank"),
    Damage(R.string.role_damage, "damage"),
    // The reference dataset says "healer"; Blizzard's own roster says "support".
    Support(R.string.role_support, "support", "healer");

    companion object {
        fun of(role: String): HeroRole? =
            entries.firstOrNull { hero -> hero.keys.any { it.equals(role, ignoreCase = true) } }
    }
}

enum class SortOrder(@StringRes val labelRes: Int) {
    Dps(R.string.sort_dps),
    DpsWithoutReload(R.string.sort_dps_no_reload),
    Accuracy(R.string.sort_accuracy),
    CritAccuracy(R.string.sort_crit_accuracy),
    TimeToKill(R.string.sort_time_to_kill),
    Hero(R.string.sort_hero),
}

/** Whether anything on screen actually cares about the charge setting. */
fun List<ChartRow>.anyChargeScaled(): Boolean = any { it.spec.behavior.isChargeScaled }

/** A weapon paired with its simulated result and the hero it belongs to. */
data class ChartRow(
    val spec: WeaponSpec,
    val hero: Hero?,
    val train: ShotTrain,
)

data class ChartUiState(
    val loading: Boolean = true,
    val rows: List<ChartRow> = emptyList(),
    val distance: Float = 5f,
    val aimX: Float = 0f,
    val aimZ: Float = 1f,
    val modifiers: Modifiers = Modifiers.NONE,
    val roles: Set<HeroRole> = HeroRole.entries.toSet(),
    val categories: Set<WeaponCategory> = WeaponCategory.entries.toSet(),
    val fireModes: Set<FireMode> = FireMode.entries.toSet(),
    val sortOrder: SortOrder = SortOrder.Dps,
    /** Zarya's charge, 0-100. Only her Particle Cannon reads it. */
    val energy: Float = 0f,
    val zoom: Float = 1f,
    /** Milliseconds the last full recompute took; surfaced so slow devices are noticeable. */
    val lastComputeMillis: Long = 0,
)
