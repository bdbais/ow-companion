package com.bellizia.owcompanion.ui.board

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The splitter has to be right about the boring cases before the interesting one matters: a
 * sheet that is one map must come back whole, and a line drawn down the middle of a diagram
 * must not be mistaken for a gutter. Getting either wrong would quietly cut a map in half.
 */
class PanelsTest {

    /** A picture 1000 columns wide, flat everywhere except the given spans. */
    private fun picture(vararg content: IntRange): FloatArray {
        val variance = FloatArray(1000)
        content.forEach { range -> range.forEach { variance[it] = 0.7f } }
        return variance
    }

    @Test
    fun `one map comes back as one panel`() {
        val panels = Panels.split(picture(20..980))
        assertEquals(1, panels.size)
        assertEquals(0.02f, panels[0].start, 0.001f)
    }

    @Test
    fun `three maps side by side come back as three`() {
        // Busan: three diagrams with a wide gutter between them.
        val panels = Panels.split(picture(10..320, 350..660, 690..990))
        assertEquals(3, panels.size)
        assertTrue(panels[0].end <= panels[1].start)
        assertTrue(panels[1].end <= panels[2].start)
        panels.forEach { assertTrue("${it.width} too narrow", it.width > 0.2f) }
    }

    @Test
    fun `two maps come back as two`() {
        val panels = Panels.split(picture(10..480, 520..990))
        assertEquals(2, panels.size)
    }

    @Test
    fun `a thin line inside a diagram is not a gutter`() {
        // A one-percent gap: a border or a road, not a separation.
        val panels = Panels.split(picture(20..500, 505..980))
        assertEquals("a five-column gap should not split anything", 1, panels.size)
    }

    @Test
    fun `a caption strip is not a map`() {
        // A wide diagram, a real gutter, then something too narrow to be a second map.
        val panels = Panels.split(picture(10..700, 800..850))
        assertEquals(1, panels.size)
        assertTrue(panels[0].width > 0.6f)
    }

    @Test
    fun `an empty or blank picture is still one panel`() {
        assertEquals(listOf(Panels.Panel(0f, 1f)), Panels.split(FloatArray(0)))
        assertEquals(listOf(Panels.Panel(0f, 1f)), Panels.split(FloatArray(500)))
    }

    @Test
    fun `panels never overlap and stay inside the picture`() {
        val panels = Panels.split(picture(5..300, 340..640, 680..995))
        panels.zipWithNext().forEach { (left, right) ->
            assertTrue("panels overlap", left.end <= right.start)
        }
        assertTrue(panels.first().start >= 0f)
        assertTrue(panels.last().end <= 1f)
    }
}
