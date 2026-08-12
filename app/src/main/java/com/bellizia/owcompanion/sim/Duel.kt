package com.bellizia.owcompanion.sim

import kotlin.math.ceil

/**
 * One hero shooting another, with everything both of them have switched on.
 *
 * The chart answers "how much damage does this weapon do". Nobody asks that. What people
 * ask is "can I kill this", and the honest answer has two halves that the chart cannot put
 * together on its own:
 *
 * - **The breakpoint.** How many shots, if every one lands and nothing heals. A whole
 *   number, and the thing that actually decides a duel.
 * - **The race.** Whether the damage out-paces what is coming back. A tank being healed is
 *   not a health pool, it is a rate against a rate, and a weapon that cannot beat the rate
 *   will never kill it however long you fire.
 *
 * Reporting only the first is how a chart tells you a comfortable lie. Reporting only the
 * second hides the fact that most fights are over in two shots.
 */
object Duel {

    /** What a fight can end as. */
    enum class Verdict {
        /** The damage beats the healing, and the target dies. */
        Kills,

        /** Damage and healing are within a hair of each other; nothing decides. */
        Stalemate,

        /** The healing wins. Firing longer will not change it. */
        Outhealed,
    }

    data class Outcome(
        /** Shots to kill with nothing healing, from [Breakpoints]. */
        val shotsToKill: Int?,
        val headshotsToKill: Int?,
        /** Damage a second the attacker actually lands, everything applied. */
        val damagePerSecond: Double,
        /** Healing a second reaching the target. */
        val healingPerSecond: Double,
        /** Seconds to kill against that healing; null when it never happens. */
        val secondsToKill: Double?,
        val verdict: Verdict,
    ) {
        val netPerSecond: Double get() = damagePerSecond - healingPerSecond

        /**
         * How much more damage a second is needed to win, for a fight that is being lost.
         *
         * The number an answer to "who could help me" is built from: any weapon adding at
         * least this much turns the fight around.
         */
        val shortfall: Double get() = if (verdict == Verdict.Kills) 0.0 else -netPerSecond
    }

    /**
     * Everything a duel needs from the defending side.
     *
     * Mitigation lives here rather than on the attacker even though the simulator applies
     * both through one [Modifiers], because that is where a reader looks for it: Fortify is
     * something the target did, not something the shooter did.
     */
    data class Defender(
        val target: Breakpoints.Target,
        val mitigation: Modifiers = Modifiers.NONE,
        /**
         * Healing a second arriving from anywhere - a support, a self-heal, lifesteal.
         *
         * Taken as given rather than derived: whether a Mercy is actually pocketing this
         * target is a fact about the fight, not about either hero.
         */
        val healingPerSecond: Double = 0.0,
    )

    /**
     * The margin inside which neither side is winning.
     *
     * Damage and healing figures both carry rounding, and a fight decided by a fifth of a
     * point a second is not decided at all - saying "kills in 340 seconds" would be
     * arithmetically true and completely useless.
     */
    private const val STALEMATE = 1.0

    fun resolve(
        weapon: WeaponSpec,
        model: WeaponModel,
        defender: Defender,
        attackerBuffs: Modifiers = Modifiers.NONE,
        crosshair: Crosshair = Crosshair(x = 0.0, z = 1.0, distance = 1.0),
        simulator: Simulator = Simulator(),
    ): Outcome {
        // One bag: the simulator multiplies the attacker's amplification and the defender's
        // mitigation together, which is what actually happens to a bullet.
        val combined = attackerBuffs.mergedWith(defender.mitigation)
        val train = simulator.simulateMean(model, crosshair, combined)

        val breakpoint = Breakpoints.shotsToKill(
            weapon = weapon,
            target = defender.target,
            modifiers = combined,
            atRange = model.basicDamage(crosshair.distance),
        )

        val damage = train.dps
        val healing = defender.healingPerSecond
        val net = damage - healing

        val verdict = when {
            net > STALEMATE -> Verdict.Kills
            net < -STALEMATE -> Verdict.Outhealed
            else -> Verdict.Stalemate
        }

        return Outcome(
            shotsToKill = breakpoint.body.takeIf { it in 1..MAX_SHOTS },
            headshotsToKill = breakpoint.head?.takeIf { it in 1..MAX_SHOTS },
            damagePerSecond = damage,
            healingPerSecond = healing,
            secondsToKill = if (verdict == Verdict.Kills) defender.target.total / net else null,
            verdict = verdict,
        )
    }

    /** A weapon offered as help, with what it brings to a fight already going badly. */
    data class Contributor(
        val weapon: WeaponSpec,
        val damagePerSecond: Double,
        /** Whether this one alone closes the gap. */
        val enough: Boolean,
    )

    /**
     * Who could turn this fight around, best first.
     *
     * Answering "which damage hero would help" by ranking damage per second against the
     * same target, not in the abstract: a weapon that shreds a squishy can be the wrong
     * answer against armour, and the ordering says so.
     */
    fun helpFor(
        outcome: Outcome,
        candidates: List<Pair<WeaponSpec, WeaponModel>>,
        defender: Defender,
        crosshair: Crosshair = Crosshair(x = 0.0, z = 1.0, distance = 1.0),
        simulator: Simulator = Simulator(),
        limit: Int = 8,
    ): List<Contributor> {
        val needed = outcome.shortfall
        return candidates
            .map { (spec, model) ->
                val dps = simulator.simulateMean(model, crosshair, defender.mitigation).dps
                Contributor(weapon = spec, damagePerSecond = dps, enough = dps >= needed)
            }
            .filter { it.damagePerSecond > 0 }
            .sortedByDescending { it.damagePerSecond }
            .take(limit)
    }

    /**
     * The least healing a second that would save this target.
     *
     * The mirror of [helpFor], for the reader asking which support to bring rather than
     * which damage hero: any healer beating this figure holds the target up.
     */
    fun healingNeededToSurvive(outcome: Outcome): Double =
        (outcome.damagePerSecond - outcome.healingPerSecond).coerceAtLeast(0.0)

    /** Beyond this the answer is "it does not", and a count would only look precise. */
    private const val MAX_SHOTS = 60

    /** Both sides' switches in one bag, since a bullet meets all of them at once. */
    private fun Modifiers.mergedWith(other: Modifiers): Modifiers = copy(
        armor = armor || other.armor,
        nanoboostDefence = nanoboostDefence || other.nanoboostDefence,
        takeABreather = takeABreather || other.takeABreather,
        fortify = fortify || other.fortify,
        overrun = overrun || other.overrun,
        cardiacOverdrive = cardiacOverdrive || other.cardiacOverdrive,
        powerBlock = powerBlock || other.powerBlock,
        nemesisBlock = nemesisBlock || other.nemesisBlock,
        spikeGuard = spikeGuard || other.spikeGuard,
        burning = burning || other.burning,
        damageBoost = damageBoost || other.damageBoost,
        supercharger = supercharger || other.supercharger,
        nanoboostOffence = nanoboostOffence || other.nanoboostOffence,
        amplificationMatrix = amplificationMatrix || other.amplificationMatrix,
        discord = discord || other.discord,
        kitsuneRush = kitsuneRush || other.kitsuneRush,
    )
}
