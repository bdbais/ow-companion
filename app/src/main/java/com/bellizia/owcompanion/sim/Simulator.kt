package com.bellizia.owcompanion.sim

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** One shot in a firing sequence, with where its pellets landed and what it drew. */
data class Shot(
    /** Seconds from opening fire until this shot lands. */
    val time: Double,
    /** Spread cone radius in metres at the target. */
    val radius: Double,
    /** How long the shot keeps dealing damage; null for instantaneous shots. */
    val duration: Double?,
    /** Rounds consumed, when a shot spends more than one. */
    val ammoMultiplier: Double?,
    val misses: Int,
    val hits: Int,
    val crits: Int,
    val damage: Double,
    /** Damage per second while this shot is landing; only meaningful when [duration] is set. */
    val dps: Double,
    val width: Double,
    val height: Double,
)

/** Everything the chart needs to draw and rank one weapon at one distance. */
data class ShotTrain(
    val weapon: WeaponSpec,
    val shots: List<Shot>,
    /** Damage of one pellet at this distance, before modifiers. */
    val basicDamage: Double,
    /** Damage of one landed pellet after modifiers and armour. */
    val hitDamage: Double,
    val critDamage: Double,
    val pellets: Double,
    /** Mean damage per second, reload included. The headline number. */
    val dps: Double,
    /** Mean damage per second while actually firing, ignoring reload. */
    val dpsWithoutReload: Double,
    /** Damage actually dealt in this simulated sequence, divided by its length. */
    val dpsRaw: Double,
    /** Fraction of pellets that landed anywhere. */
    val accuracy: Double,
    /** Fraction of pellets that landed on the head. */
    val critAccuracy: Double,
    /**
     * Seconds to kill the target. Infinite when the weapon cannot reach it at all — out of
     * range, or unable to out-damage nothing.
     */
    val timeToKill: Double,
    /** Tallest drawn shot, in pixels, used to size the row. */
    val height: Double,
)

/**
 * Simulates a weapon firing at a target and reports what it actually achieves.
 *
 * The model is the one from [owdmgchart](https://github.com/yfp/owdmgchart): fire for
 * [TOTAL_TIME] seconds, place every pellet of every shot against the target's hitboxes,
 * and account the damage that lands. Weapons with spread are stochastic, so a fixed
 * [Random] seed is used by default — otherwise the chart would flicker on every redraw.
 */
class Simulator(
    val enemy: Enemy = Enemy.roadhog(),
    private val totalTime: Double = TOTAL_TIME,
) {
    fun simulate(
        model: WeaponModel,
        crosshair: Crosshair,
        modifiers: Modifiers = Modifiers.NONE,
        random: Random = Random(DEFAULT_SEED),
        charge: Double = 0.0,
    ): ShotTrain {
        val distance = crosshair.distance
        val pellets = model.pelletsAt(distance)
        val basicDamage = model.basicDamage(distance, charge)
        val shots = initShots(model, distance)
        val outcomes = simulateOutcomes(model, shots, crosshair, distance, pellets.toInt(), random)
        return accountDamage(model, shots, outcomes, basicDamage, pellets, modifiers)
    }

    /**
     * Averages repeated runs to settle the sampling noise of weapons with spread.
     *
     * [maxTrials] is a ceiling, not a target. What actually stabilises the mean is the
     * number of *pellets* sampled, and a minigun that fires six hundred times already
     * averages six hundred samples in a single run — repeating it thirty more times buys
     * nothing and costs everything. So the trial count is scaled down for weapons that
     * sample heavily on their own, which is the difference between a chart that redraws in
     * a blink and one that takes eight seconds.
     */
    fun simulateMean(
        model: WeaponModel,
        crosshair: Crosshair,
        modifiers: Modifiers = Modifiers.NONE,
        maxTrials: Int = 64,
        seed: Int = DEFAULT_SEED,
        charge: Double = 0.0,
        targetSamples: Int = TARGET_SAMPLES,
    ): ShotTrain {
        val random = Random(seed)
        val first = simulate(model, crosshair, modifiers, random, charge)
        if (isDeterministic(model)) return first

        val samplesPerTrial = (first.shots.size * first.pellets).toInt().coerceAtLeast(1)
        val trials = (targetSamples / samplesPerTrial).coerceIn(1, maxTrials)
        if (trials == 1) return first

        var dps = first.dps
        var dpsWithoutReload = first.dpsWithoutReload
        var dpsRaw = first.dpsRaw
        var accuracy = first.accuracy
        var critAccuracy = first.critAccuracy
        var timeToKill = if (first.timeToKill.isFinite()) first.timeToKill else 0.0
        var finiteKills = if (first.timeToKill.isFinite()) 1 else 0

        repeat(trials - 1) {
            val train = simulate(model, crosshair, modifiers, random, charge)
            dps += train.dps
            dpsWithoutReload += train.dpsWithoutReload
            dpsRaw += train.dpsRaw
            accuracy += train.accuracy
            critAccuracy += train.critAccuracy
            if (train.timeToKill.isFinite()) {
                timeToKill += train.timeToKill
                finiteKills++
            }
        }

        val n = trials.toDouble()
        return first.copy(
            dps = dps / n,
            dpsWithoutReload = dpsWithoutReload / n,
            dpsRaw = dpsRaw / n,
            accuracy = accuracy / n,
            critAccuracy = critAccuracy / n,
            timeToKill = if (finiteKills > 0) timeToKill / finiteKills else Double.POSITIVE_INFINITY,
        )
    }

    /** True when no randomness can affect where a pellet lands. */
    fun isDeterministic(model: WeaponModel): Boolean {
        val spread = model.spec.spread ?: return true
        if (spread.randomlyRotated) return false
        return spread.angle == null && spread.maxAngle == null
    }

    // --- firing sequence ---------------------------------------------------------------

    private class MutableShot(
        val time: Double,
        val radius: Double,
        var duration: Double? = null,
        var ammoMultiplier: Double? = null,
    ) {
        var misses = 0
        var hits = 0
        var crits = 0
        var damage = 0.0
        var dps = 0.0
        var width = 0.0
        var height = 0.0
    }

    private fun initShots(model: WeaponModel, distance: Double): List<MutableShot> {
        val timeDelay = model.timeDelay(distance)
        val shots = mutableListOf<MutableShot>()
        var time = 0.0
        var ammo = model.magazine

        while (time < totalTime && shots.size < MAX_SHOTS) {
            val radius = model.spreadRadius(distance, ammo)
            val step = model.shotStep(ammo, time)
            val shot = MutableShot(time = time + timeDelay, radius = radius)
            if (step.ammoConsumed > 1) shot.ammoMultiplier = step.ammoConsumed
            if (model.isBeam) shot.duration = model.shotTime * step.ammoConsumed
            // Damage-over-time shots keep ticking for a fixed window after they land.
            if (model.isDamageOverTime) shot.duration = model.spec.damage.duration
            shots += shot

            ammo = step.ammoLeft
            // A zero-length step would spin forever; treat it as a broken spec and stop.
            if (step.delay <= 0.0) break
            time += step.delay
        }
        return shots
    }

    /** Aggregate hit/crit/miss fractions across the whole sequence. */
    private class Outcomes(val miss: Double, val hit: Double, val crit: Double)

    private fun simulateOutcomes(
        model: WeaponModel,
        shots: List<MutableShot>,
        crosshair: Crosshair,
        distance: Double,
        pellets: Int,
        random: Random,
    ): Outcomes {
        var totalMiss = 0
        var totalHit = 0
        var totalCrit = 0

        // Without a fixed pellet pattern every pellet starts on the crosshair, so there is
        // no offset to rotate and none to look up per pellet.
        val hasPattern = model.hasPelletPattern

        for (shot in shots) {
            var rotationSin = 0.0
            var rotationCos = 1.0
            if (hasPattern && model.spec.spread?.randomlyRotated == true) {
                val angle = 2 * PI * random.nextDouble()
                rotationSin = sin(angle)
                rotationCos = cos(angle)
            }

            for (pellet in 1..pellets) {
                var shiftX = 0.0
                var shiftZ = 0.0
                if (hasPattern) {
                    val (patternX, patternZ) = model.pelletShift(distance, pellet)
                    shiftX = rotationCos * patternX - rotationSin * patternZ
                    shiftZ = rotationCos * patternZ + rotationSin * patternX
                }
                val outcome = enemy.shoot(
                    crosshair = crosshair,
                    radius = shot.radius,
                    shiftX = shiftX,
                    shiftZ = shiftZ,
                    random = random,
                )
                when (outcome) {
                    Outcome.Miss -> shot.misses++
                    Outcome.Hit -> shot.hits++
                    Outcome.Crit -> shot.crits++
                }
            }

            // Weapons that cannot crit still hit; fold those pellets back into plain hits.
            if (model.critFactor == 1.0) {
                shot.hits += shot.crits
                shot.crits = 0
            }

            totalMiss += shot.misses
            totalHit += shot.hits
            totalCrit += shot.crits
        }

        val total = (totalMiss + totalHit + totalCrit).toDouble()
        if (total == 0.0) return Outcomes(0.0, 0.0, 0.0)
        return Outcomes(totalMiss / total, totalHit / total, totalCrit / total)
    }

    // --- damage accounting -------------------------------------------------------------

    private fun accountDamage(
        model: WeaponModel,
        shots: List<MutableShot>,
        outcomes: Outcomes,
        basicDamage: Double,
        pellets: Double,
        modifiers: Modifiers,
    ): ShotTrain {
        if (shots.isEmpty()) {
            return emptyTrain(model, basicDamage, pellets)
        }
        if (model.accounting == DamageAccounting.PhotonProjector) {
            return accountPhotonProjector(model, shots, outcomes, basicDamage, pellets, modifiers)
        }

        val amplification =
            if (model.isBeamOrMelee) modifiers.factorMeleeBeam else modifiers.factor
        val hitDamage = modifiers.applyArmor(basicDamage * amplification, model.isBeam)
        val critDamage = modifiers.applyArmor(basicDamage * amplification * model.critFactor, model.isBeam)

        var totalDamage = 0.0
        var height = 0.0
        var timeToKill: Double? = null

        for (shot in shots) {
            shot.damage = (shot.hits * hitDamage + shot.crits * critDamage) * model.segmentsFactor
            val shotHeight = shotDimensions(model, shot)
            totalDamage += shot.damage
            if (totalDamage >= enemy.hp && timeToKill == null) {
                // Within a shot that spans time, interpolate to the moment the target drops.
                val duration = shot.duration
                timeToKill = shot.time + if (duration != null && shot.damage > 0) {
                    duration * (enemy.hp + shot.damage - totalDamage) / shot.damage
                } else {
                    0.0
                }
            }
            if (shotHeight > height) height = shotHeight
        }

        val last = shots.last()
        val elapsed = last.time + (last.duration ?: 0.0)
        val meanDamage =
            (hitDamage * outcomes.hit + critDamage * outcomes.crit) * pellets * model.segmentsFactor
        val dps = meanDamage / (model.dpsPeriodBase + model.dpsPeriodAdd)

        return ShotTrain(
            weapon = model.spec,
            shots = shots.map { it.toShot() },
            basicDamage = basicDamage,
            hitDamage = hitDamage,
            critDamage = critDamage,
            pellets = pellets,
            dps = dps,
            dpsWithoutReload = meanDamage / model.dpsPeriodBase,
            dpsRaw = totalDamage / elapsed,
            accuracy = if (totalDamage > 0) outcomes.hit + outcomes.crit else 0.0,
            critAccuracy = if (totalDamage > 0) outcomes.crit else 0.0,
            timeToKill = timeToKill
                ?: if (dps > 0) enemy.hp / dps else Double.POSITIVE_INFINITY,
            height = 2 * ceil(height / 2),
        )
    }

    /**
     * Symmetra's beam charges through fixed damage levels the longer it stays on a target,
     * so its damage comes from the charge level rather than from falloff, and it cannot crit.
     */
    private fun accountPhotonProjector(
        model: WeaponModel,
        shots: List<MutableShot>,
        outcomes: Outcomes,
        basicDamage: Double,
        pellets: Double,
        modifiers: Modifiers,
    ): ShotTrain {
        val hitDamage = basicDamage * modifiers.factorMeleeBeam
        // Charge levels are armour-checked as discrete hits, not as a beam.
        val levels = model.spec.damage.dpsFactors.orEmpty()
            .map { modifiers.applyArmor(hitDamage * it, isBeam = false) }
        if (levels.isEmpty()) return emptyTrain(model, basicDamage, pellets)

        var totalDamage = 0.0
        var height = 30.0
        var timeToKill: Double? = null

        shots.forEachIndexed { index, shot ->
            shot.damage = shot.hits * levels[minOf(index, levels.size - 1)]
            val shotHeight = shotDimensions(model, shot)
            totalDamage += shot.damage
            if (totalDamage >= enemy.hp && timeToKill == null && shot.damage > 0) {
                timeToKill =
                    shot.time + (totalDamage - enemy.hp) / shot.damage * (shot.duration ?: 0.0)
            }
            if (shotHeight > height) height = shotHeight
        }

        val last = shots.last()
        val elapsed = last.time + (last.duration ?: 0.0)
        val meanDamage = outcomes.hit * levels.last()

        return ShotTrain(
            weapon = model.spec,
            shots = shots.map { it.toShot() },
            basicDamage = basicDamage,
            hitDamage = hitDamage,
            critDamage = hitDamage,
            pellets = pellets,
            dps = meanDamage / (model.dpsPeriodBase + model.dpsPeriodAdd),
            dpsWithoutReload = meanDamage / model.dpsPeriodBase,
            dpsRaw = totalDamage / elapsed,
            accuracy = if (totalDamage > 0) 1.0 else 0.0,
            critAccuracy = 0.0,
            // Unlike other weapons this has no analytic fallback: if the simulated beam
            // never dropped the target, it does not get a time to kill.
            timeToKill = timeToKill ?: Double.POSITIVE_INFINITY,
            height = 2 * ceil(height / 2),
        )
    }

    /**
     * Sizes a shot for drawing and returns its height. Beams fold their duration back into
     * [MutableShot.damage] here, so this must run before the damage is accumulated.
     */
    private fun shotDimensions(model: WeaponModel, shot: MutableShot): Double =
        when (model.accounting) {
            DamageAccounting.Beam, DamageAccounting.PhotonProjector -> {
                val duration = shot.duration ?: 0.0
                shot.damage *= duration * model.fireRate
                shot.dps = if (duration > 0) shot.damage / duration else 0.0
                shot.height = shot.dps * AREASCALE / TIMESCALE
                shot.height
            }

            DamageAccounting.DamageOverTime -> {
                val duration = shot.duration ?: 0.0
                shot.dps = if (duration > 0) shot.damage / duration else 0.0
                shot.height = shot.dps * AREASCALE / TIMESCALE
                shot.height
            }

            DamageAccounting.Standard -> {
                if (shot.damage > model.maxSquareDamage) {
                    shot.width = model.maxShotWidth
                    shot.height = AREASCALE * shot.damage / shot.width
                } else {
                    shot.width = sqrt(AREASCALE * shot.damage)
                    shot.height = shot.width
                }
                shot.height
            }
        }

    private fun emptyTrain(model: WeaponModel, basicDamage: Double, pellets: Double) = ShotTrain(
        weapon = model.spec,
        shots = emptyList(),
        basicDamage = basicDamage,
        hitDamage = 0.0,
        critDamage = 0.0,
        pellets = pellets,
        dps = 0.0,
        dpsWithoutReload = 0.0,
        dpsRaw = 0.0,
        accuracy = 0.0,
        critAccuracy = 0.0,
        timeToKill = Double.POSITIVE_INFINITY,
        height = 0.0,
    )

    private fun MutableShot.toShot() = Shot(
        time = time,
        radius = radius,
        duration = duration,
        ammoMultiplier = ammoMultiplier,
        misses = misses,
        hits = hits,
        crits = crits,
        damage = damage,
        dps = dps,
        width = width,
        height = height,
    )

    companion object {
        /** Seconds of the timeline the chart draws. */
        const val MAX_TIME = 17.5

        /** Seconds simulated: a little past the drawn window, so the last shot is complete. */
        const val TOTAL_TIME = 1.2 * MAX_TIME

        /** Pixels per second on the time axis. */
        const val TIMESCALE = 60.0

        /** Square pixels per point of damage. */
        const val AREASCALE = 2.0

        /** Guards against a malformed spec producing an unbounded firing sequence. */
        const val MAX_SHOTS = 4096

        /**
         * Pellet samples aimed for per weapon when averaging trials. At this many, the
         * standard error of a hit fraction is under 0.3% - far below what is visible in a
         * bar or a dps figure rounded to one decimal.
         */
        const val TARGET_SAMPLES = 12_000

        const val DEFAULT_SEED = 20200929
    }
}
