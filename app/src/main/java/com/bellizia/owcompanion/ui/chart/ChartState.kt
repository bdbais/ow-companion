package com.bellizia.owcompanion.ui.chart

import com.bellizia.owcompanion.sim.Hero
import com.bellizia.owcompanion.sim.Modifiers
import com.bellizia.owcompanion.sim.ShotTrain
import com.bellizia.owcompanion.sim.WeaponSpec

/** Broad weapon families, used for filtering. A weapon can belong to more than one. */
enum class WeaponCategory(val label: String) {
    Hitscan("Hitscan"),
    Projectile("Projectile"),
    Shotgun("Shotgun"),
    Beam("Beam"),
    Melee("Melee");

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

enum class HeroRole(val label: String, vararg val keys: String) {
    Tank("Tank", "tank"),
    Damage("Damage", "damage"),
    // The reference dataset says "healer"; Blizzard's own roster says "support".
    Support("Support", "support", "healer");

    companion object {
        fun of(role: String): HeroRole? =
            entries.firstOrNull { hero -> hero.keys.any { it.equals(role, ignoreCase = true) } }
    }
}

enum class SortOrder(val label: String) {
    Dps("DPS"),
    DpsWithoutReload("DPS, no reload"),
    Accuracy("Accuracy"),
    CritAccuracy("Crit accuracy"),
    TimeToKill("Time to kill"),
    Hero("Hero"),
}

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
    val sortOrder: SortOrder = SortOrder.Dps,
    val zoom: Float = 1f,
    /** Milliseconds the last full recompute took; surfaced so slow devices are noticeable. */
    val lastComputeMillis: Long = 0,
)
