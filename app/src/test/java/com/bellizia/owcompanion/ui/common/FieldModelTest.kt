package com.bellizia.owcompanion.ui.common

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class FieldModelTest {

    private val side = 900f
    private val middle = side / 2f

    private fun onRing(degreesFromTop: Double, fraction: Float = 0.36f): Offset {
        val radians = Math.toRadians(degreesFromTop - 90.0)
        return Offset(
            middle + (side * fraction * cos(radians)).toFloat(),
            middle + (side * fraction * sin(radians)).toFloat(),
        )
    }

    @Test
    fun `the hub is the centre`() {
        assertEquals(HUB, pieceAt(Offset(middle, middle), side))
    }

    @Test
    fun `pieces run clockwise from the top`() {
        repeat(SEGMENTS) { index ->
            val centreOfPiece = index * (360.0 / SEGMENTS) + (360.0 / SEGMENTS) / 2
            assertEquals(index, pieceAt(onRing(centreOfPiece), side))
        }
    }

    @Test
    fun `every piece is reachable, so the wheel can always be completed`() {
        val reached = buildSet {
            add(pieceAt(Offset(middle, middle), side))
            repeat(360) { degree -> add(pieceAt(onRing(degree.toDouble()), side)) }
        }
        assertEquals((0..SEGMENTS).toSet(), reached.filterNotNull().toSet())
    }

    @Test
    fun `taps in the gap and outside are ignored`() {
        assertNull(pieceAt(onRing(45.0, fraction = 0.21f), side))
        assertNull(pieceAt(onRing(45.0, fraction = 0.49f), side))
        assertNull(pieceAt(Offset(5f, 5f), side))
    }

    // --- the field itself -------------------------------------------------------------

    private fun run(model: FieldModel, seconds: Float, controls: Controls = Controls()) {
        // A sixtieth at a time, so the result is what a real frame rate would produce.
        repeat((seconds * 60).toInt()) { model.step(controls, 1f / 60f) }
    }

    /**
     * Fire, and slide away from the nearest thing coming at her.
     *
     * Crude, but it is the difference between testing the game and testing a stationary
     * target: on foot a single hit costs a life, so a still pilot proves nothing.
     */
    private fun dodging(model: FieldModel): Controls {
        val incoming = model.motes
            .filter { it.kind == Trace.Incoming && it.y < model.ownY && it.vy > 0f }
            .minByOrNull { kotlin.math.abs(it.x - model.ownX) }

        // Step aside from anything about to arrive; otherwise line up on something to shoot.
        val dx = if (incoming != null && kotlin.math.abs(incoming.x - model.ownX) < 9f) {
            if (incoming.x > model.ownX) -1f else 1f
        } else {
            val target = model.markers.minByOrNull { kotlin.math.abs(it.x - model.ownX) }
            when {
                target == null -> 0f
                target.x > model.ownX + 1f -> 1f
                target.x < model.ownX - 1f -> -1f
                else -> 0f
            }
        }
        return Controls(dx = dx, primary = true)
    }

    /** A field stepped forward to the moment the suit gives out. */
    private fun ejecting(seed: Long): FieldModel {
        val model = FieldModel(seed = seed)
        var frames = 0
        while (model.form != Form.Ejecting && frames < 60 * 200) {
            model.step(Controls(), 1f / 60f)
            frames += 1
        }
        check(model.form == Form.Ejecting) { "the suit never gave out" }
        return model
    }

    @Test
    fun `it starts suited, on the first level, with nothing scored`() {
        val model = FieldModel(seed = 1)
        assertEquals(Stage.Running, model.stage)
        assertEquals(Form.Suit, model.form)
        assertEquals(1, model.tier)
        assertEquals(0, model.tally)
        assertEquals(model.maxIntegrity, model.integrity)
        assertEquals(3, model.lives)
    }

    @Test
    fun `the field never leaks past its own edges`() {
        val model = FieldModel(seed = 2)
        run(model, 6f, Controls(dx = -1f, dy = -1f))
        assertTrue(model.ownX >= 0f && model.ownX <= FIELD_W)
        // The upper part of the field stays out of reach.
        assertTrue(model.ownY > FIELD_H * 0.4f)
    }

    @Test
    fun `holding fire produces shots that travel upwards`() {
        val model = FieldModel(seed = 3)
        run(model, 0.5f, Controls(primary = true))
        val own = model.motes.filter { it.kind == Trace.Primary }
        assertTrue(own.isNotEmpty())
        assertTrue(own.all { it.vy < 0f })
    }

    @Test
    fun `the second weapon has to recharge before it fires again`() {
        val model = FieldModel(seed = 4)
        run(model, 0.1f, Controls(secondary = true))
        val first = model.motes.count { it.kind == Trace.Secondary }
        assertEquals(3, first)
        assertTrue(model.secondaryReady < 1f)

        run(model, 0.3f, Controls(secondary = true))
        assertEquals(first, model.motes.count { it.kind == Trace.Secondary })
    }

    @Test
    fun `the heavy arrives only once the wave is spent, and it is named`() {
        val model = FieldModel(seed = 5)
        // Long enough for the whole first wave to be released and to fall through.
        run(model, 40f)
        assertNotNull(model.heavyLabel)
        assertEquals(HEAVIES[0], model.heavyLabel)
    }

    @Test
    fun `it gets harder as the levels go up`() {
        // Same seed, different level: everything that differs is the difficulty curve.
        val early = FieldModel(seed = 6, startTier = 1)
        val late = FieldModel(seed = 6, startTier = 8)
        run(early, 2f)
        run(late, 2f)

        val earlyFall = early.markers.first().vy
        val lateFall = late.markers.first().vy
        assertTrue("they should fall faster", lateFall > earlyFall)
        assertTrue("more of them should be out", late.markers.size >= early.markers.size)
        assertTrue("the heavy should be tougher", late.tier > early.tier)
    }

    @Test
    fun `each level fields a different heavy, and the list wraps`() {
        assertEquals(HEAVIES, (1..HEAVIES.size).map { heavyFor(it) })
        // Past the end it starts again rather than falling off it.
        assertEquals(HEAVIES[0], heavyFor(HEAVIES.size + 1))
        assertEquals(HEAVIES[1], heavyFor(HEAVIES.size * 3 + 2))
    }

    @Test
    fun `a spent suit opens the eject window rather than ending the run`() {
        val model = FieldModel(seed = 7)
        // Sit still until the suit gives out. Nothing has been lost yet at that point.
        var frames = 0
        while (model.form != Form.Ejecting && frames < 60 * 200) {
            model.step(Controls(), 1f / 60f)
            frames += 1
        }
        assertEquals(Form.Ejecting, model.form)
        assertEquals(0, model.integrity)
        assertEquals("the life is only spent when the window closes", 3, model.lives)
        assertTrue(model.ejectIn > 0f)
    }

    @Test
    fun `taking the window blows the field up and costs nothing`() {
        val model = ejecting(seed = 11)
        // Wait for something to be on the field worth taking with her.
        assertTrue(model.markers.isNotEmpty())
        val livesBefore = model.lives

        model.step(Controls(charge = true), 1f / 60f)

        assertEquals(Form.Pilot, model.form)
        assertEquals("no life is spent when the window is taken", livesBefore, model.lives)
        assertTrue("the field is cleared", model.markers.none { !it.heavy })
        assertTrue("incoming fire is swept away", model.motes.none { it.kind == Trace.Incoming })
    }

    @Test
    fun `letting the window close costs a life`() {
        val model = ejecting(seed = 12)
        val livesBefore = model.lives

        run(model, 1.5f)

        assertEquals(Form.Pilot, model.form)
        assertEquals(livesBefore - 1, model.lives)
    }

    @Test
    fun `on foot she has the pistol but not the missiles`() {
        val model = ejecting(seed = 13)
        run(model, 1.5f)
        assertEquals(Form.Pilot, model.form)

        val before = model.motes.count { it.kind == Trace.Secondary }
        run(model, 0.5f, Controls(primary = true, secondary = true))
        assertTrue("the pistol fires", model.motes.any { it.kind == Trace.Primary })
        assertEquals("the missiles do not", before, model.motes.count { it.kind == Trace.Secondary })
    }

    @Test
    fun `landing hits on foot fills the meter`() {
        val model = ejecting(seed = 14)
        run(model, 1.5f)
        assertEquals(Form.Pilot, model.form)
        assertEquals(0f, model.charge, 0.0001f)

        var frames = 0
        while (model.charge == 0f && model.stage == Stage.Running && frames < 60 * 30) {
            model.step(dodging(model), 1f / 60f)
            frames += 1
        }
        assertTrue("the pistol charges the meter", model.charge > 0f)
    }

    @Test
    fun `a full meter on foot buys a new suit`() {
        // Getting back in is meant to be hard, and a stationary pilot never manages it, so
        // this plays several fixed seeds and asks that the way back exists at all.
        val recovered = (1L..12L).count { seed ->
            val model = ejecting(seed = seed)
            run(model, 1.5f)
            if (model.form != Form.Pilot) return@count false

            var frames = 0
            while (model.form == Form.Pilot && model.stage == Stage.Running && frames < 60 * 90) {
                val meterFull = model.charge >= 1f
                model.step(dodging(model).copy(charge = meterFull), 1f / 60f)
                frames += 1
            }
            model.form == Form.Suit && model.integrity == model.maxIntegrity
        }
        assertTrue("a pilot should be able to earn a suit back", recovered > 0)
    }

    @Test
    fun `running out of lives ends it`() {
        val model = FieldModel(seed = 15)
        run(model, 400f)
        assertEquals(Stage.Finished, model.stage)
        assertEquals(0, model.lives)
    }

    @Test
    fun `a finished field ignores further input`() {
        val model = FieldModel(seed = 8)
        run(model, 400f)
        val before = model.tally
        run(model, 5f, Controls(primary = true, dx = 1f))
        assertEquals(before, model.tally)
    }
}
