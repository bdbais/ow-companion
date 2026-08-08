package com.bellizia.owcompanion.ui.common

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * A fixed 100x160 field stepped by elapsed seconds.
 *
 * Nothing here touches Android or Compose, so the result does not depend on the screen or
 * the frame rate and the whole thing can be played out in a test.
 */
const val FIELD_W = 100f
const val FIELD_H = 160f

private const val OWN_SPEED = 58f
private const val OWN_RADIUS = 4.2f
private const val OWN_MAX_HEALTH = 5

private const val PRIMARY_INTERVAL = 0.14f
private const val PRIMARY_SPEED = 105f

private const val SECONDARY_COOLDOWN = 6f
private const val SECONDARY_SPEED = 78f
private const val SECONDARY_DAMAGE = 4

private const val CHARGE_PER_DAMAGE = 0.016f
private const val CHARGE_RADIUS = 62f
private const val CHARGE_DAMAGE = 14

private const val FLASH = 1.1f

internal val HEAVIES = listOf(
    "REINHARDT", "ROADHOG", "WINSTON", "ORISA", "SIGMA",
    "RAMATTRA", "DOOMFIST", "ZARYA", "MAUGA", "JUNKER QUEEN",
)

/** Cycles through the list, so the run keeps going past the end of it. */
internal fun heavyFor(tier: Int): String = HEAVIES[(tier - 1).mod(HEAVIES.size)]

internal enum class Stage { Running, Cleared, Finished }

internal enum class Trace { Primary, Secondary, Incoming }

internal data class Mote(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val damage: Int,
    val kind: Trace,
    val radius: Float,
)

internal data class Marker(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var health: Int,
    val maxHealth: Int,
    val radius: Float,
    val value: Int,
    val heavy: Boolean = false,
    val label: String? = null,
    var fireIn: Float = 1f,
    var flash: Float = 0f,
)

internal data class Ripple(var x: Float, var y: Float, var age: Float, val radius: Float)

internal data class Controls(
    val dx: Float = 0f,
    val dy: Float = 0f,
    val primary: Boolean = false,
    val secondary: Boolean = false,
    val charge: Boolean = false,
)

internal class FieldModel(seed: Long = 0L, startTier: Int = 1) {

    private val random = Random(seed)

    var stage: Stage = Stage.Running
        private set
    var tally: Int = 0
        private set
    var tier: Int = 1
        private set
    var health: Int = OWN_MAX_HEALTH
        private set
    var charge: Float = 0f
        private set
    var secondaryReady: Float = 1f
        private set

    var ownX: Float = FIELD_W / 2f
        private set
    var ownY: Float = FIELD_H - 18f
        private set
    private var ownFlash: Float = 0f

    val markers = mutableListOf<Marker>()
    val motes = mutableListOf<Mote>()
    val ripples = mutableListOf<Ripple>()

    private var toRelease = 0
    private var releaseIn = 0.6f
    private var primaryIn = 0f
    private var heavyOut = false
    private var clearedIn = 0f

    val maxHealth: Int get() = OWN_MAX_HEALTH
    val heavyLabel: String? get() = markers.firstOrNull { it.heavy }?.label

    init {
        begin(startTier)
    }

    private fun begin(next: Int) {
        tier = next
        toRelease = 6 + next * 2
        releaseIn = 0.5f
        heavyOut = false
        markers.clear()
        motes.clear()
    }

    fun step(controls: Controls, dt: Float) {
        if (stage == Stage.Finished) return

        if (stage == Stage.Cleared) {
            clearedIn -= dt
            if (clearedIn <= 0f) {
                begin(tier + 1)
                stage = Stage.Running
            }
            ageRipples(dt)
            return
        }

        moveOwn(controls, dt)
        shoot(controls, dt)
        release(dt)
        moveMarkers(dt)
        moveMotes(dt)
        resolve()
        ageRipples(dt)

        ownFlash = max(0f, ownFlash - dt)
        secondaryReady = min(1f, secondaryReady + dt / SECONDARY_COOLDOWN)

        if (markers.isEmpty() && toRelease == 0 && heavyOut) {
            stage = Stage.Cleared
            clearedIn = 2.2f
            tally += 250 * tier
        }
    }

    private fun moveOwn(controls: Controls, dt: Float) {
        val length = hypot(controls.dx, controls.dy).coerceAtLeast(1f)
        ownX += controls.dx / length * OWN_SPEED * dt
        ownY += controls.dy / length * OWN_SPEED * dt
        ownX = ownX.coerceIn(OWN_RADIUS, FIELD_W - OWN_RADIUS)
        // The top of the field stays out of reach, so the release points cannot be camped.
        ownY = ownY.coerceIn(FIELD_H * 0.42f, FIELD_H - OWN_RADIUS)
    }

    private fun shoot(controls: Controls, dt: Float) {
        primaryIn -= dt
        if (controls.primary && primaryIn <= 0f) {
            primaryIn = PRIMARY_INTERVAL
            for (offset in listOf(-2.4f, 2.4f)) {
                motes += Mote(ownX + offset, ownY - OWN_RADIUS, 0f, -PRIMARY_SPEED, 1, Trace.Primary, 1.1f)
            }
        }

        if (controls.secondary && secondaryReady >= 1f) {
            secondaryReady = 0f
            for (degrees in listOf(-14f, 0f, 14f)) {
                val radians = Math.toRadians(degrees.toDouble())
                motes += Mote(
                    x = ownX,
                    y = ownY - OWN_RADIUS,
                    vx = (SECONDARY_SPEED * sin(radians)).toFloat(),
                    vy = (-SECONDARY_SPEED * cos(radians)).toFloat(),
                    damage = SECONDARY_DAMAGE,
                    kind = Trace.Secondary,
                    radius = 2f,
                )
            }
        }

        if (controls.charge && charge >= 1f) {
            charge = 0f
            val originY = ownY - 14f
            ripples += Ripple(ownX, originY, 0f, CHARGE_RADIUS)
            markers.filter { hypot(it.x - ownX, it.y - originY) <= CHARGE_RADIUS + it.radius }
                .forEach { hit(it, CHARGE_DAMAGE, charges = false) }
            // It sweeps the field clear as well, which is what makes it worth saving.
            motes.removeAll { it.kind == Trace.Incoming }
        }
    }

    private fun release(dt: Float) {
        if (toRelease <= 0) {
            if (!heavyOut) releaseHeavy()
            return
        }
        releaseIn -= dt
        if (releaseIn > 0f) return

        releaseIn = max(0.28f, 1.15f - tier * 0.06f)
        toRelease -= 1

        val health = 2 + tier / 2
        markers += Marker(
            x = 8f + random.nextFloat() * (FIELD_W - 16f),
            y = -6f,
            vx = (random.nextFloat() - 0.5f) * (7f + tier),
            vy = 11f + tier * 1.1f,
            health = health,
            maxHealth = health,
            radius = 4f,
            value = 100,
            fireIn = 0.6f + random.nextFloat(),
        )
    }

    private fun releaseHeavy() {
        heavyOut = true
        val health = 40 + tier * 18
        markers += Marker(
            x = FIELD_W / 2f,
            y = -14f,
            vx = 15f + tier * 1.5f,
            vy = 8f,
            health = health,
            maxHealth = health,
            radius = 11f,
            value = 1000,
            heavy = true,
            label = heavyFor(tier),
            fireIn = 1.2f,
        )
    }

    private fun moveMarkers(dt: Float) {
        val iterator = markers.iterator()
        while (iterator.hasNext()) {
            val marker = iterator.next()
            marker.x += marker.vx * dt
            marker.y += marker.vy * dt
            marker.flash = max(0f, marker.flash - dt)

            if (marker.heavy) {
                if (marker.y > 22f) {
                    marker.y = 22f
                    marker.vy = 0f
                }
                if (marker.x < marker.radius || marker.x > FIELD_W - marker.radius) {
                    marker.vx = -marker.vx
                    marker.x = marker.x.coerceIn(marker.radius, FIELD_W - marker.radius)
                }
            } else if (marker.x < marker.radius || marker.x > FIELD_W - marker.radius) {
                marker.vx = -marker.vx
            }

            marker.fireIn -= dt
            if (marker.fireIn <= 0f && marker.y > 0f) {
                marker.fireIn = if (marker.heavy) {
                    max(0.25f, 0.9f - tier * 0.05f)
                } else {
                    max(0.7f, 2.2f - tier * 0.09f) + random.nextFloat()
                }
                markerShoot(marker)
            }

            if (marker.y - marker.radius > FIELD_H) {
                iterator.remove()
                if (!marker.heavy) wound(1)
            }
        }
    }

    private fun markerShoot(marker: Marker) {
        val speed = 34f + tier * 1.6f
        val spread = if (marker.heavy) listOf(-22f, 0f, 22f) else listOf(0f)
        val dx = ownX - marker.x
        val dy = ownY - marker.y
        val length = hypot(dx, dy).coerceAtLeast(0.001f)
        val nx = dx / length
        val ny = dy / length
        for (degrees in spread) {
            val radians = Math.toRadians(degrees.toDouble())
            val c = cos(radians).toFloat()
            val s = sin(radians).toFloat()
            motes += Mote(
                x = marker.x,
                y = marker.y + marker.radius,
                vx = (nx * c - ny * s) * speed,
                vy = (nx * s + ny * c) * speed,
                damage = 1,
                kind = Trace.Incoming,
                radius = 1.4f,
            )
        }
    }

    private fun moveMotes(dt: Float) {
        val iterator = motes.iterator()
        while (iterator.hasNext()) {
            val mote = iterator.next()
            mote.x += mote.vx * dt
            mote.y += mote.vy * dt
            if (mote.y < -8f || mote.y > FIELD_H + 8f || mote.x < -8f || mote.x > FIELD_W + 8f) {
                iterator.remove()
            }
        }
    }

    private fun resolve() {
        val spent = mutableListOf<Mote>()

        for (mote in motes) {
            if (mote.kind == Trace.Incoming) {
                if (ownFlash > 0f) continue
                if (hypot(mote.x - ownX, mote.y - ownY) <= mote.radius + OWN_RADIUS) {
                    spent += mote
                    wound(mote.damage)
                }
                continue
            }
            val struck = markers.firstOrNull {
                hypot(mote.x - it.x, mote.y - it.y) <= mote.radius + it.radius
            } ?: continue
            spent += mote
            hit(struck, mote.damage)
        }
        motes.removeAll(spent)

        if (ownFlash <= 0f) {
            markers.firstOrNull {
                hypot(it.x - ownX, it.y - ownY) <= it.radius + OWN_RADIUS
            }?.let { marker ->
                wound(1)
                if (!marker.heavy) hit(marker, 2)
            }
        }
    }

    private fun hit(marker: Marker, amount: Int, charges: Boolean = true) {
        marker.health -= amount
        marker.flash = 0.12f
        if (charges) charge = min(1f, charge + amount * CHARGE_PER_DAMAGE)
        if (marker.health <= 0) {
            markers.remove(marker)
            tally += marker.value * tier
            ripples += Ripple(marker.x, marker.y, 0f, if (marker.heavy) 34f else 9f)
        }
    }

    private fun wound(amount: Int) {
        if (ownFlash > 0f) return
        health -= amount
        ownFlash = FLASH
        ripples += Ripple(ownX, ownY, 0f, 12f)
        if (health <= 0) {
            health = 0
            stage = Stage.Finished
        }
    }

    private fun ageRipples(dt: Float) {
        ripples.forEach { it.age += dt }
        ripples.removeAll { it.age > 0.45f }
    }

    fun flickering(): Boolean = ownFlash > 0f && (ownFlash * 12f).toInt() % 2 == 0
}
