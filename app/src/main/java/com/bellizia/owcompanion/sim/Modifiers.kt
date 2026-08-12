package com.bellizia.owcompanion.sim

/**
 * Damage amplification and mitigation that can be stacked onto a weapon.
 *
 * The mitigations carry the figure the wiki states for each ability, taken from its
 * `damage_red` field. They used to share one flat 50%, which was the Overwatch 1
 * approximation and is now wrong for every one of them: Fortify is 45, Take a Breather 40.
 *
 * Two rules come with them, both from the wiki:
 *
 * - Damage reduction *buffs* stop stacking at 50% in total. Three of these at once is not
 *   three times the protection.
 * - Abilities the wiki marks "strong reduction" - Doomfist's Power Block, Ramattra's Block,
 *   Hazard's Spike Guard - override that cap. They are also self-applied and mutually
 *   exclusive: no hero can be doing two of them, so the strongest one active is used rather
 *   than a product of all three.
 */
data class Modifiers(
    // Mitigation
    val armor: Boolean = false,
    val nanoboostDefence: Boolean = false,
    val takeABreather: Boolean = false,
    val fortify: Boolean = false,
    /** Mauga's Overrun: 50% while he is charging. */
    val overrun: Boolean = false,
    /** Mauga's Cardiac Overdrive: 40% to himself. */
    val cardiacOverdrive: Boolean = false,
    /** Doomfist's Power Block: 75%, from the front, and it ignores the 50% cap. */
    val powerBlock: Boolean = false,
    /** Ramattra's Block in Nemesis Form: 75%, likewise uncapped. */
    val nemesisBlock: Boolean = false,
    /** Hazard's Spike Guard: 60%, likewise uncapped. */
    val spikeGuard: Boolean = false,
    /**
     * Whether the target is already alight.
     *
     * Not a buff on the shooter but a state of the fight, which is why it belongs here
     * beside the others rather than baked into the two weapons that care. Mauga's Cha-Cha
     * treats every hit on a burning enemy as a critical one, and Anran's Fan the Flames
     * doubles its own damage and the burn it is feeding.
     */
    val burning: Boolean = false,
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
        val kept = 1.0 - mitigation()
        percent *= kept
        percentShots *= kept

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

    /**
     * The share of incoming damage removed by everything active, 0 to 1.
     *
     * Buffs combine multiplicatively - two 40% reductions leave 36% getting through, not
     * 20% - and are then held to the wiki's 50% ceiling. The self-applied blocks are not
     * subject to that ceiling and do not stack with each other, so the strongest one wins
     * and multiplies with whatever the buffs left.
     */
    private fun mitigation(): Double {
        var throughBuffs = 1.0
        if (nanoboostDefence) throughBuffs *= 1 - 0.50
        if (takeABreather) throughBuffs *= 1 - 0.40
        if (fortify) throughBuffs *= 1 - 0.45
        if (overrun) throughBuffs *= 1 - 0.50
        if (cardiacOverdrive) throughBuffs *= 1 - 0.40
        // The cap is on the buffs' combined effect, not on each of them.
        val fromBuffs = (1.0 - throughBuffs).coerceAtMost(BUFF_CAP)

        val strongest = listOfNotNull(
            0.75.takeIf { powerBlock },
            0.75.takeIf { nemesisBlock },
            0.60.takeIf { spikeGuard },
        ).maxOrNull() ?: 0.0

        return 1.0 - (1.0 - fromBuffs) * (1.0 - strongest)
    }

    companion object {
        /** The wiki: "Damage reduction buff stacking is capped at 50%." */
        private const val BUFF_CAP = 0.50

        val NONE = Modifiers()
    }
}
