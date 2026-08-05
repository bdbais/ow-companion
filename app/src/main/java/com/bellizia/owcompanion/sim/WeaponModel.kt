package com.bellizia.owcompanion.sim

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/** Linear interpolation between two values over a range, flat outside it. */
internal class Ramp(values: List<Double>, args: List<Double>) {
    private val r1: Double
    private val r2: Double
    private val v1: Double
    private val v2: Double

    init {
        // The range may be given high-to-low; normalise it, carrying the values along.
        if (args[0] > args[1]) {
            r1 = args[1]; r2 = args[0]; v1 = values[1]; v2 = values[0]
        } else {
            r1 = args[0]; r2 = args[1]; v1 = values[0]; v2 = values[1]
        }
    }

    fun at(r: Double): Double = when {
        r <= r1 -> v1
        r >= r2 -> v2
        else -> v1 + (r - r1) / (r2 - r1) * (v2 - v1)
    }
}

private fun tanDeg(angle: Double) = tan(angle / 180 * PI)
private fun cosDeg(angle: Double) = cos(angle / 180 * PI)
private fun sinDeg(angle: Double) = sin(angle / 180 * PI)

/** How a weapon's shots are turned into damage; see [Simulator]. */
internal enum class DamageAccounting { Standard, Beam, DamageOverTime, PhotonProjector }

private const val GRAVITY = 9.0 // m/s^2

/**
 * A [WeaponSpec] with its derived firing behaviour resolved: falloff curves, spread cones,
 * travel time and the ammo/reload cycle.
 *
 * Construct once per weapon and reuse across distances — nothing here depends on the shot
 * being simulated, so a model is safe to share and cheap to keep around.
 */
class WeaponModel(
    val spec: WeaponSpec,
    /**
     * Multiplier on how fast the weapon cycles, from effects like Kitsune Rush. Everything
     * on the attack clock scales - shots, bursts, reloads and windup - while a shot's own
     * effect duration does not, since speeding up the shooter does not make a burning
     * grenade burn faster.
     */
    val speedFactor: Double = 1.0,
) {

    val isBeam: Boolean = spec.type == "beam"
    val isMelee: Boolean = spec.type == "melee"
    val isBeamOrMelee: Boolean = isBeam || isMelee
    internal val isDamageOverTime: Boolean = spec.type.contains("EOT")

    /** Magazine size; infinite for weapons that never reload. */
    val magazine: Double = spec.ammo ?: Double.POSITIVE_INFINITY

    // Unscaled values first, so each derived timing is divided by the speed factor exactly
    // once rather than inheriting a division from whatever it was derived from.
    private val baseShotTime: Double = spec.shotTime ?: (1.0 / (spec.fireRate ?: 1.0))
    private val baseReloadTime: Double = spec.reloadTime ?: 0.0

    val fireRate: Double = (spec.fireRate ?: 0.0) * speedFactor
    val shotTime: Double = baseShotTime / speedFactor
    val reloadTime: Double = baseReloadTime / speedFactor
    val chargeDelay: Double = (spec.chargeDelay ?: 0.0) / speedFactor
    val critFactor: Double = spec.critFactor ?: if (isBeamOrMelee) 1.0 else 2.0

    /** Seconds between rounds inside a burst. */
    internal val burstDelay: Double = (spec.burst?.delay ?: 0.0) / speedFactor

    /** Damage-over-time weapons apply their per-shot damage this many times. */
    val segmentsFactor: Double = spec.damage.segments ?: 1.0

    /** Seconds of the firing cycle one shot accounts for, ignoring reload. */
    val dpsPeriodBase: Double = (
        spec.dpsPeriodBase
            ?: (if (spec.burst != null) baseShotTime / spec.burst.ammo else baseShotTime)
        ) / speedFactor

    /** Reload time amortised over the magazine. */
    val dpsPeriodAdd: Double = (
        spec.dpsPeriodAdd
            ?: (if (magazine.isInfinite()) 0.0 else baseReloadTime / magazine)
        ) / speedFactor

    internal val accounting: DamageAccounting = when {
        spec.behavior == WeaponBehavior.PhotonProjector -> DamageAccounting.PhotonProjector
        isBeam -> DamageAccounting.Beam
        isDamageOverTime -> DamageAccounting.DamageOverTime
        else -> DamageAccounting.Standard
    }

    // --- drawing geometry -------------------------------------------------------------
    // A shot is drawn as a rectangle whose *area* is its damage, so that a burst of small
    // hits and one big hit occupy the same ink. Width is capped at the gap to the next shot
    // so neighbouring shots never overlap.

    private val filling: Double = spec.filling ?: 0.5
    private val shotSpacing: Double =
        Simulator.TIMESCALE * (if (spec.burst != null) burstDelay else shotTime)
    internal val maxShotWidth: Double = if (isBeam) 0.0 else shotSpacing * filling
    internal val maxSquareDamage: Double =
        if (isBeam) 0.0 else maxShotWidth * maxShotWidth / Simulator.AREASCALE

    // --- falloff ----------------------------------------------------------------------

    private val falloffRamp: Ramp? = spec.damage.falloff?.let { falloff ->
        // A falloff range without a near/far damage pair is a dataset error; fall back to
        // flat damage rather than crashing on it.
        val damage = spec.damage.dpshot
        if (damage.size < 2 || falloff.size < 2) null else Ramp(damage, falloff)
    }

    /** Damage a single pellet deals at [distance], before modifiers. */
    fun basicDamage(distance: Double, energy: Double = spec.energy ?: 0.0): Double =
        when (spec.behavior) {
            WeaponBehavior.ScrapGunSecondary -> {
                val rangeBall = spec.damage.rangeBall ?: 0.0
                if (distance < rangeBall) {
                    spec.damage.dpshotBall ?: 0.0
                } else {
                    falloffRamp?.at(distance - rangeBall) ?: spec.damage.dpshot.first()
                }
            }

            WeaponBehavior.ParticleCannon -> {
                val maxRange = spec.damage.maxRange
                if (maxRange != null && distance > maxRange) {
                    0.0
                } else {
                    spec.damage.dpshot.first() * (1 + (spec.energyFactor ?: 0.0) * energy / 100)
                }
            }

            else -> when {
                falloffRamp != null -> falloffRamp.at(distance)
                spec.damage.maxRange != null ->
                    if (distance <= spec.damage.maxRange) spec.damage.dpshot.first() else 0.0

                else -> spec.damage.dpshot.first()
            }
        }

    /** Pellets fired per shot; shotguns that fire a slug up close vary this with range. */
    fun pelletsAt(distance: Double): Double = when (spec.behavior) {
        WeaponBehavior.ScrapGunSecondary ->
            if (distance < (spec.damage.rangeBall ?: 0.0)) spec.pellets.first() else spec.pellets.last()

        else -> spec.pellets.first()
    }

    // --- spread -----------------------------------------------------------------------

    private val bloomRamp: Ramp? = spec.spread?.let { spread ->
        val maxAngle = spread.maxAngle ?: return@let null
        val range = spread.spreadingAmmoRange ?: return@let null
        val minAngleRad = (spread.minAngle ?: 0.0) / 180 * PI
        Ramp(
            listOf(maxAngle / 180 * PI, minAngleRad),
            listOf(magazine - range[1], magazine - range[0]),
        )
    }

    /**
     * Radius in metres of the spread cone where it meets the target. [ammo] matters for
     * weapons that bloom as the magazine drains.
     */
    fun spreadRadius(distance: Double, ammo: Double): Double {
        if (spec.behavior == WeaponBehavior.ScrapGunSecondary) {
            val rangeBall = spec.damage.rangeBall ?: 0.0
            if (distance < rangeBall) return 0.0
            val angle = spec.spread?.angle ?: return 0.0
            return tanDeg(angle / 2) * (distance - rangeBall)
        }
        val spread = spec.spread ?: return 0.0
        spread.angle?.let { return distance * tanDeg(it / 2) }
        bloomRamp?.let { return distance * tan(it.at(ammo) / 2) }
        return 0.0
    }

    /**
     * Fixed per-pellet offsets, in metres at the target's distance, for weapons whose
     * pellets follow a set pattern rather than scattering randomly.
     */
    private val unitShifts: List<Pair<Double, Double>> =
        spec.spread?.constantAngles.orEmpty().map { angles ->
            val uy = cosDeg(angles[0]) * cosDeg(angles[1])
            (sinDeg(angles[0]) * cosDeg(angles[1]) / uy) to (sinDeg(angles[1]) / uy)
        }

    /** True when pellets leave the barrel on the crosshair rather than on a set pattern. */
    val hasPelletPattern: Boolean = unitShifts.isNotEmpty()

    /** [pellet] is 1-based, matching how the pattern is authored. */
    fun pelletShift(distance: Double, pellet: Int): Pair<Double, Double> {
        if (unitShifts.isEmpty()) return NO_SHIFT
        val (ux, uz) = unitShifts[pellet - 1]
        return (ux * distance) to (uz * distance)
    }

    private companion object {
        /** Shared so the common no-pattern case does not allocate per pellet. */
        val NO_SHIFT = 0.0 to 0.0
    }

    // --- timing -----------------------------------------------------------------------

    /** Seconds between pulling the trigger and the shot arriving at [distance]. */
    fun timeDelay(distance: Double): Double {
        val velocity = spec.velocity ?: return chargeDelay
        if (spec.type == "arc projectile") {
            // Launch angle for a ballistic shot that lands at `distance`; the projectile
            // takes the flatter of the two arcs that reach it.
            val sinValue = (GRAVITY * distance / (velocity * velocity)).coerceAtMost(1.0)
            val phi = asin(sinValue) / 2
            return chargeDelay + distance / (velocity * cos(phi))
        }
        return chargeDelay + distance / velocity
    }

    /** Ammo left, seconds until the next shot, and rounds this shot consumed. */
    fun shotStep(ammo: Double, time: Double): ShotStep {
        when (spec.behavior) {
            WeaponBehavior.PhotonProjector -> {
                val chargingTime = spec.damage.levelChargingTime ?: shotTime
                val levels = spec.damage.dpsFactors.orEmpty().size
                return if (time < (levels - 1) * chargingTime) {
                    val consumed = chargingTime * fireRate
                    ShotStep(ammo - consumed, chargingTime, consumed)
                } else {
                    ShotStep(magazine, ammo * shotTime + reloadTime, ammo)
                }
            }

            // Doomfist's cannon trickles one round back at a time; it never reloads in full.
            WeaponBehavior.HandCannon ->
                return if (ammo <= 1) {
                    ShotStep(1.0, shotTime + reloadTime, 1.0)
                } else {
                    ShotStep(ammo - 1, shotTime, 1.0)
                }

            else -> Unit
        }

        if (isBeam) {
            // A beam is modelled as one long "shot" that drains the whole magazine.
            return ShotStep(magazine, magazine * shotTime + reloadTime, magazine)
        }

        val burst = spec.burst
        if (burst != null) {
            var remaining = ammo - 1
            // The gap after the last round of a burst absorbs the whole burst's recovery.
            var delay = if ((magazine - remaining) % burst.ammo == 0.0) {
                shotTime - burstDelay * (burst.ammo - 1)
            } else {
                burstDelay
            }
            if (remaining == 0.0) {
                delay += reloadTime
                remaining = magazine
            }
            return ShotStep(remaining, delay, 1.0)
        }

        return if (ammo <= 1) {
            ShotStep(magazine, shotTime + reloadTime, 1.0)
        } else {
            ShotStep(ammo - 1, shotTime, 1.0)
        }
    }
}

/** Result of advancing the firing cycle by one shot. */
data class ShotStep(
    val ammoLeft: Double,
    val delay: Double,
    val ammoConsumed: Double,
)
