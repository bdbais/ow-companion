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

    @Test
    fun `it starts alive, on the first level, with nothing scored`() {
        val model = FieldModel(seed = 1)
        assertEquals(Stage.Running, model.stage)
        assertEquals(1, model.tier)
        assertEquals(0, model.tally)
        assertEquals(model.maxHealth, model.health)
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
    fun `running out of health ends it`() {
        val model = FieldModel(seed = 7)
        // Sit still and let everything through; the floor takes a life at a time.
        run(model, 120f)
        assertEquals(Stage.Finished, model.stage)
        assertEquals(0, model.health)
    }

    @Test
    fun `a finished field ignores further input`() {
        val model = FieldModel(seed = 8)
        run(model, 120f)
        val before = model.tally
        run(model, 5f, Controls(primary = true, dx = 1f))
        assertEquals(before, model.tally)
    }
}
