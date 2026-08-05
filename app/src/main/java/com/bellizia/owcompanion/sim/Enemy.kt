package com.bellizia.owcompanion.sim

import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Where a single pellet or projectile ended up. */
enum class Outcome { Miss, Hit, Crit }

/**
 * Aim point, in metres. [x] and [z] are measured in the target's plane, with z rising from
 * its feet; [distance] is how far away the shooter stands.
 */
data class Crosshair(
    val x: Double = 0.0,
    val z: Double = 1.0,
    val distance: Double = 5.0,
)

sealed interface HitBox {
    fun contains(x: Double, z: Double): Boolean
}

data class RectHitBox(
    val centerX: Double,
    val centerZ: Double,
    val width: Double,
    val height: Double,
) : HitBox {
    private val x1 = centerX - width / 2
    private val x2 = centerX + width / 2
    private val z1 = centerZ - height / 2
    private val z2 = centerZ + height / 2

    override fun contains(x: Double, z: Double): Boolean = x in x1..x2 && z in z1..z2
}

data class CircleHitBox(
    val centerX: Double,
    val centerZ: Double,
    val radius: Double,
) : HitBox {
    private val radiusSquared = radius * radius

    override fun contains(x: Double, z: Double): Boolean {
        val dx = x - centerX
        val dz = z - centerZ
        return dx * dx + dz * dz <= radiusSquared
    }
}

/**
 * The target being shot at. The chart uses a stationary Roadhog: he is the largest and
 * best-known hitbox in the game, which makes "how long to kill one" a legible yardstick.
 */
data class Enemy(
    val body: HitBox,
    val head: HitBox,
    val hp: Double,
) {
    fun registerHit(x: Double, z: Double): Outcome = when {
        head.contains(x, z) -> Outcome.Crit
        body.contains(x, z) -> Outcome.Hit
        else -> Outcome.Miss
    }

    /**
     * Fires one pellet. [radius] is the spread cone's radius at the target's distance; the
     * pellet lands at a uniformly sampled angle and radius within it. [shiftX]/[shiftZ] are
     * a fixed pattern offset applied on top.
     */
    fun shoot(
        crosshair: Crosshair,
        radius: Double,
        shiftX: Double,
        shiftZ: Double,
        random: Random,
    ): Outcome {
        var cx = 0.0
        var cz = 0.0
        if (radius > 0) {
            val phi = 2 * Math.PI * random.nextDouble()
            val r = radius * random.nextDouble()
            cx = r * cos(phi)
            cz = r * sin(phi)
        }
        return registerHit(crosshair.x + cx + shiftX, crosshair.z + cz + shiftZ)
    }

    companion object {
        /** Roadhog: 1.4 x 2.1 m body, a 0.6 m wide head centred 2.0 m up, 600 hp. */
        fun roadhog(): Enemy {
            val bodyWidth = 1.4
            val bodyHeight = 2.1
            val headHeight = 2.0
            val headWidth = 0.6
            return Enemy(
                body = RectHitBox(0.0, bodyHeight / 2, bodyWidth, bodyHeight),
                head = CircleHitBox(0.0, headHeight, headWidth / 2),
                hp = 600.0,
            )
        }
    }
}
