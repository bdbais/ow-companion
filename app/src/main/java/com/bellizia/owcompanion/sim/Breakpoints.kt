package com.bellizia.owcompanion.sim

import kotlin.math.ceil
import kotlin.math.min

/**
 * How many shots it takes, which is the question a ranking of damage per second cannot
 * answer.
 *
 * Sustained damage says who wins a long fight. Almost nothing in this game is a long fight:
 * what decides a duel is whether a hero dies to two shots or three, and that is a whole
 * number that does not move with how well anyone aims. A nerf that costs five damage is
 * invisible on a chart and decisive here, if it crosses a threshold.
 *
 * Everything below is arithmetic on one shot landing. Missing is the player's business.
 */
object Breakpoints {

    /** A target's three pools, in the order damage eats them. */
    data class Target(
        val name: String,
        val health: Int,
        val armor: Int = 0,
        val shields: Int = 0,
    ) {
        val total: Int get() = health + armor + shields
    }

    data class Result(
        /** Shots to kill with every pellet on the body. */
        val body: Int,
        /** Shots to kill with every pellet on the head, or null when it cannot crit. */
        val head: Int?,
        /** Damage one body shot actually does to this target, armour included. */
        val perBodyShot: Double,
        val perHeadShot: Double?,
    ) {
        /** Whether aiming at the head removes a shot, which is the only reason it matters. */
        val headSaves: Boolean get() = head != null && head < body
    }

    /**
     * Shots to kill, one weapon against one target.
     *
     * Armour is applied per instance of damage - per pellet, not per trigger pull - which is
     * exactly why a shotgun suffers against it and a sniper does not. Shields take damage
     * plainly, so they are spent before the armour is reached.
     *
     * @param atRange damage per pellet at the range being asked about; the caller decides
     *   whether that is point blank or the far end of the falloff.
     */
    fun shotsToKill(
        weapon: WeaponSpec,
        target: Target,
        modifiers: Modifiers = Modifiers.NONE,
        atRange: Double? = null,
    ): Result {
        val pellets = (weapon.pellets.firstOrNull() ?: 1.0).toInt().coerceAtLeast(1)
        val perPellet = atRange ?: weapon.damage.dpshot.firstOrNull() ?: return Result(0, null, 0.0, null)
        if (perPellet <= 0.0) return Result(0, null, 0.0, null)

        // A beam is mitigated differently, and the buffs use their own multiplier for it.
        val beam = weapon.type.contains("beam", ignoreCase = true)
        val boosted = perPellet * if (beam) modifiers.factorMeleeBeam else modifiers.factor
        val crit = weapon.critFactor?.takeIf { it > 1.0 }

        val body = damagePerShot(boosted, pellets, target, beam)
        val head = crit?.let { damagePerShot(boosted * it, pellets, target, beam) }

        return Result(
            body = shots(target, body),
            head = head?.let { shots(target, it) },
            perBodyShot = body,
            perHeadShot = head,
        )
    }

    /**
     * What one trigger pull removes.
     *
     * The pools are walked in order because armour only mitigates what actually reaches it:
     * a pellet that lands while shields are up is not reduced, and the same pellet a moment
     * later is. Doing this per pellet rather than per shot is what makes the difference
     * visible at all.
     */
    private fun damagePerShot(
        perPellet: Double,
        pellets: Int,
        target: Target,
        isBeam: Boolean,
    ): Double {
        var shields = target.shields.toDouble()
        var armor = target.armor.toDouble()
        var dealt = 0.0

        repeat(pellets) {
            var remaining = perPellet
            if (shields > 0) {
                val taken = min(shields, remaining)
                shields -= taken
                remaining -= taken
                dealt += taken
            }
            if (remaining > 0 && armor > 0) {
                // Mitigation first, then see how much of the pool it covers.
                val mitigated = Modifiers(armor = true).applyArmor(remaining, isBeam)
                val taken = min(armor, mitigated)
                armor -= taken
                // What is left of the pellet carries on at full strength.
                remaining = if (mitigated > taken) remaining * (1 - taken / mitigated) else 0.0
                dealt += taken
            }
            dealt += remaining
        }
        return dealt
    }

    private fun shots(target: Target, perShot: Double): Int =
        if (perShot <= 0.0) Int.MAX_VALUE else ceil(target.total / perShot).toInt()

    /** The distinct pools worth checking a weapon against, commonest first. */
    fun targetsFrom(heroes: List<Target>): List<Target> =
        heroes.filter { it.total > 0 }
            .groupBy { Triple(it.health, it.armor, it.shields) }
            .map { (_, group) -> group.minByOrNull { it.name.length } ?: group.first() }
            .sortedBy { it.total }
}
