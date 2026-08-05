package com.bellizia.owcompanion.sim

/**
 * Damage amplification and mitigation that can be stacked onto a weapon.
 *
 * These are the Overwatch 1 rules, matching the 2020 dataset. The 2026 dataset changes both
 * the numbers and how armour works, so these values are expected to be revisited alongside
 * it rather than carried over.
 */
data class Modifiers(
    // Mitigation
    val armor: Boolean = false,
    val nanoboostDefence: Boolean = false,
    val takeABreather: Boolean = false,
    val fortify: Boolean = false,
    // Amplification
    val damageBoost: Boolean = false,
    val supercharger: Boolean = false,
    val nanoboostOffence: Boolean = false,
    val amplificationMatrix: Boolean = false,
    val discord: Boolean = false,
    // Rate of fire. Unlike the others this raises damage by compressing the firing cycle
    // rather than by scaling each hit, so it is applied to the weapon's timing.
    val kitsuneRush: Boolean = false,
) {
    /**
     * Multiplier for weapons that fire discrete shots. Amplification Matrix doubles a
     * projectile's damage by letting it pass through twice, which is why it only lands here.
     */
    val factor: Double

    /** Multiplier for beams and melee, which Amplification Matrix does not double. */
    val factorMeleeBeam: Double

    init {
        var percent = 100.0
        if (damageBoost) percent += 30.0
        if (supercharger) percent += 50.0
        if (nanoboostOffence) percent += 50.0

        var percentShots = percent
        if (amplificationMatrix) percentShots *= 2

        if (discord) {
            percent *= 1.3
            percentShots *= 1.3
        }
        if (nanoboostDefence || takeABreather || fortify) {
            percent *= 0.5
            percentShots *= 0.5
        }

        factor = percentShots / 100
        factorMeleeBeam = percent / 100
    }

    /**
     * Armour mitigation, applied per instance of damage: beams lose a flat 20%, everything
     * else loses 3 points, or half the damage when that would be more forgiving. Small,
     * fast-hitting weapons suffer disproportionately, which is the whole point of armour.
     */
    /**
     * How much faster the weapon cycles. Kitsune Rush speeds up attacks, reloads and
     * cooldowns by half, which is why a weapon can climb the ranking on timing alone.
     */
    val attackSpeedFactor: Double get() = if (kitsuneRush) 1.5 else 1.0

    fun applyArmor(damage: Double, isBeam: Boolean): Double = when {
        !armor -> damage
        isBeam -> damage * 0.8
        damage > 6 -> damage - 3
        else -> damage / 2
    }

    companion object {
        val NONE = Modifiers()
    }
}
