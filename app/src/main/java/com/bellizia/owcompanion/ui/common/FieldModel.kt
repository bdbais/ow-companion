package com.bellizia.owcompanion.ui.common

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * A field of a given shape, stepped by elapsed seconds.
 *
 * Nothing here touches Android or Compose, so the result does not depend on the screen or
 * the frame rate and the whole thing can be played out in a test.
 */
/** The shape used when none is given: a tall screen. */
const val FIELD_W = 100f
const val FIELD_H = 160f

private const val SUIT_SPEED = 58f
private const val SUIT_RADIUS = 4.2f
private const val SUIT_INTEGRITY = 5

private const val PILOT_SPEED = 74f
private const val PILOT_RADIUS = 2.6f

private const val PRIMARY_INTERVAL = 0.14f
private const val PRIMARY_SPEED = 105f
private const val PILOT_INTERVAL = 0.3f
private const val PILOT_SHOT_SPEED = 120f

private const val SECONDARY_COOLDOWN = 6f
private const val SECONDARY_SPEED = 78f
private const val SECONDARY_DAMAGE = 4

private const val CHARGE_PER_DAMAGE = 0.016f

/**
 * On foot the meter fills far faster. The pistol does one damage a third of a second, so at
 * the suit's rate a new one would take a minute of clean hits while she dies to a touch.
 * Eight hits is about three seconds, which is long enough to hurt and short enough to want.
 */
private const val PILOT_CHARGE_PER_DAMAGE = 0.125f

private const val BLAST_DAMAGE = 40

/** How long the prompt stays up once the suit is spent. */
private const val EJECT_WINDOW = 1f

private const val FLASH = 1.1f
private const val STARTING_LIVES = 3

internal val HEAVIES = listOf(
    "REINHARDT", "ROADHOG", "WINSTON", "ORISA", "SIGMA",
    "RAMATTRA", "DOOMFIST", "ZARYA", "MAUGA", "JUNKER QUEEN",
)

/** Cycles through the list, so the run keeps going past the end of it. */
internal fun heavyFor(tier: Int): String = HEAVIES[(tier - 1).mod(HEAVIES.size)]

internal enum class Stage { Running, Cleared, Finished }

/**
 * Suited, the one second in between, and on foot.
 *
 * Ejecting is a state of its own rather than a flag, because it is the only moment when the
 * charge button costs nothing and when nothing can be hurt.
 */
internal enum class Form { Suit, Ejecting, Pilot }

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

internal class FieldModel(
    seed: Long = 0L,
    startTier: Int = 1,
    width: Float = FIELD_W,
    height: Float = FIELD_H,
) {

    private val random = Random(seed)

    /**
     * The field takes the shape of the space it is played in, so a tall screen is played
     * tall and a wide one wide. Height is the fixed dimension because it sets how long a
     * descent lasts, which is the pacing.
     */
    var width: Float = width
        private set
    var height: Float = height
        private set

    var stage: Stage = Stage.Running
        private set
    var form: Form = Form.Suit
        private set
    var tally: Int = 0
        private set
    var tier: Int = 1
        private set
    var lives: Int = STARTING_LIVES
        private set
    var integrity: Int = SUIT_INTEGRITY
        private set
    var charge: Float = 0f
        private set
    var secondaryReady: Float = 1f
        private set
    var ejectIn: Float = 0f
        private set

    var ownX: Float = width / 2f
        private set
    var ownY: Float = height - 18f
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

    val maxIntegrity: Int get() = SUIT_INTEGRITY
    val heavyLabel: String? get() = markers.firstOrNull { it.heavy }?.label
    val radius: Float get() = if (form == Form.Pilot) PILOT_RADIUS else SUIT_RADIUS

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

        if (form == Form.Ejecting) {
            if (controls.charge) {
                detonate()
            } else {
                ejectIn -= dt
                if (ejectIn <= 0f) {
                    spendLife()
                    if (stage != Stage.Finished) toPilot()
                }
            }
        }

        moveOwn(controls, dt)
        if (form != Form.Ejecting) shoot(controls, dt)
        release(dt)
        moveMarkers(dt)
        moveMotes(dt)
        resolve()
        ageRipples(dt)

        ownFlash = max(0f, ownFlash - dt)
        if (form == Form.Suit) {
            secondaryReady = min(1f, secondaryReady + dt / SECONDARY_COOLDOWN)
        }

        if (markers.isEmpty() && toRelease == 0 && heavyOut) {
            stage = Stage.Cleared
            clearedIn = 2.2f
            tally += 250 * tier
        }
    }

    private fun moveOwn(controls: Controls, dt: Float) {
        val speed = if (form == Form.Pilot) PILOT_SPEED else SUIT_SPEED
        val length = hypot(controls.dx, controls.dy).coerceAtLeast(1f)
        ownX += controls.dx / length * speed * dt
        ownY += controls.dy / length * speed * dt
        ownX = ownX.coerceIn(radius, width - radius)
        // The top of the field stays out of reach, so the release points cannot be camped.
        ownY = ownY.coerceIn(height * 0.42f, height - radius)
    }

    private fun shoot(controls: Controls, dt: Float) {
        primaryIn -= dt
        val interval = if (form == Form.Pilot) PILOT_INTERVAL else PRIMARY_INTERVAL
        if (controls.primary && primaryIn <= 0f) {
            primaryIn = interval
            if (form == Form.Pilot) {
                // One barrel on foot, and it stings rather than hurts.
                motes += Mote(ownX, ownY - radius, 0f, -PILOT_SHOT_SPEED, 1, Trace.Primary, 1f)
            } else {
                for (offset in listOf(-2.4f, 2.4f)) {
                    motes += Mote(
                        ownX + offset, ownY - radius, 0f, -PRIMARY_SPEED, 1, Trace.Primary, 1.1f,
                    )
                }
            }
        }

        if (form == Form.Suit && controls.secondary && secondaryReady >= 1f) {
            secondaryReady = 0f
            for (degrees in listOf(-14f, 0f, 14f)) {
                val radians = Math.toRadians(degrees.toDouble())
                motes += Mote(
                    x = ownX,
                    y = ownY - radius,
                    vx = (SECONDARY_SPEED * sin(radians)).toFloat(),
                    vy = (-SECONDARY_SPEED * cos(radians)).toFloat(),
                    damage = SECONDARY_DAMAGE,
                    kind = Trace.Secondary,
                    radius = 2f,
                )
            }
        }

        if (controls.charge && charge >= 1f) {
            if (form == Form.Pilot) {
                // A full meter on foot buys a new one rather than an explosion.
                charge = 0f
                form = Form.Suit
                integrity = SUIT_INTEGRITY
                secondaryReady = 1f
                ownFlash = FLASH
                ripples += Ripple(ownX, ownY, 0f, 20f)
            } else {
                charge = 0f
                detonate()
            }
        }
    }

    /**
     * Everything on the field goes up, and the suit with it.
     *
     * It is the same explosion whether it was set off deliberately or caught in the second
     * after the suit gives out, which is what makes that second worth taking.
     */
    private fun detonate() {
        ripples += Ripple(ownX, ownY, 0f, min(width, height) * 0.9f)
        markers.toList().forEach { hit(it, BLAST_DAMAGE, charges = false) }
        motes.removeAll { it.kind == Trace.Incoming }
        toPilot()
    }

    private fun toPilot() {
        form = Form.Pilot
        charge = 0f
        integrity = 0
        ejectIn = 0f
        ownFlash = FLASH
        ownY = ownY.coerceAtMost(height - PILOT_RADIUS)
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
            x = 8f + random.nextFloat() * (width - 16f),
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
            x = width / 2f,
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
                if (marker.x < marker.radius || marker.x > width - marker.radius) {
                    marker.vx = -marker.vx
                    marker.x = marker.x.coerceIn(marker.radius, width - marker.radius)
                }
            } else if (marker.x < marker.radius || marker.x > width - marker.radius) {
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

            if (marker.y - marker.radius > height) {
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
            if (mote.y < -8f || mote.y > height + 8f || mote.x < -8f || mote.x > width + 8f) {
                iterator.remove()
            }
        }
    }

    private fun resolve() {
        val spent = mutableListOf<Mote>()

        for (mote in motes) {
            if (mote.kind == Trace.Incoming) {
                if (!vulnerable()) continue
                if (hypot(mote.x - ownX, mote.y - ownY) <= mote.radius + radius) {
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

        if (vulnerable()) {
            markers.firstOrNull {
                hypot(it.x - ownX, it.y - ownY) <= it.radius + radius
            }?.let { marker ->
                wound(1)
                if (!marker.heavy) hit(marker, 2)
            }
        }
    }

    private fun vulnerable(): Boolean = ownFlash <= 0f && form != Form.Ejecting

    private fun hit(marker: Marker, amount: Int, charges: Boolean = true) {
        marker.health -= amount
        marker.flash = 0.12f
        if (charges) {
            val rate = if (form == Form.Pilot) PILOT_CHARGE_PER_DAMAGE else CHARGE_PER_DAMAGE
            charge = min(1f, charge + amount * rate)
        }
        if (marker.health <= 0) {
            markers.remove(marker)
            tally += marker.value * tier
            ripples += Ripple(marker.x, marker.y, 0f, if (marker.heavy) 34f else 9f)
        }
    }

    private fun wound(amount: Int) {
        if (!vulnerable()) return

        if (form == Form.Pilot) {
            // On foot there is nothing left to lose but the life itself.
            spendLife()
            if (stage != Stage.Finished) {
                ownFlash = FLASH
                ripples += Ripple(ownX, ownY, 0f, 12f)
            }
            return
        }

        integrity -= amount
        ownFlash = FLASH
        ripples += Ripple(ownX, ownY, 0f, 12f)
        if (integrity <= 0) {
            integrity = 0
            form = Form.Ejecting
            ejectIn = EJECT_WINDOW
        }
    }

    private fun spendLife() {
        lives -= 1
        if (lives <= 0) {
            lives = 0
            stage = Stage.Finished
        }
    }

    private fun ageRipples(dt: Float) {
        ripples.forEach { it.age += dt }
        ripples.removeAll { it.age > 0.45f }
    }

    /**
     * Takes a new shape without interrupting the run.
     *
     * Turning the phone would otherwise mean either throwing the game away or playing the
     * rest of it letterboxed. Everything is scaled in proportion, velocities included, so a
     * descent keeps taking the same time and nothing lands somewhere it could not reach.
     */
    fun reshape(newWidth: Float, newHeight: Float) {
        if (newWidth <= 0f || newHeight <= 0f) return
        val sx = newWidth / width
        val sy = newHeight / height
        if (sx == 1f && sy == 1f) return

        width = newWidth
        height = newHeight

        ownX *= sx
        ownY *= sy
        for (marker in markers) {
            marker.x *= sx; marker.y *= sy; marker.vx *= sx; marker.vy *= sy
        }
        for (mote in motes) {
            mote.x *= sx; mote.y *= sy; mote.vx *= sx; mote.vy *= sy
        }
        for (ripple in ripples) {
            ripple.x *= sx; ripple.y *= sy
        }
    }

    fun flickering(): Boolean = ownFlash > 0f && (ownFlash * 12f).toInt() % 2 == 0
}
