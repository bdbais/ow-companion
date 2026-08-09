package com.bellizia.owcompanion

import com.bellizia.owcompanion.sim.Sensitivity
import com.bellizia.owcompanion.sim.Sensitivity.Match
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two figures the community has settled on are the check on this arithmetic. If a scope
 * of 50.94 against a view of 103 does not produce 37.89% and 49.46%, either the formulas or
 * the field of view are wrong, and there would be no way to tell which from the app alone.
 */
class SensitivityTest {

    private val base = Sensitivity.DEFAULT_FOV
    private val scope = 50.94

    @Test
    fun `a measured scope reproduces the figures players use`() {
        assertEquals(37.89, Sensitivity.relative(base, scope, Match.Centre), 0.01)
        assertEquals(49.46, Sensitivity.relative(base, scope, Match.Ratio), 0.01)
    }

    @Test
    fun `a narrower view needs a higher setting`() {
        // Turning the slider down magnifies the hip view too, so the gap to close is smaller.
        val at103 = Sensitivity.relative(103.0, scope, Match.Centre)
        val at90 = Sensitivity.relative(90.0, scope, Match.Centre)
        val at80 = Sensitivity.relative(80.0, scope, Match.Centre)
        assertTrue("$at90 should exceed $at103", at90 > at103)
        assertTrue("$at80 should exceed $at90", at80 > at90)
    }

    @Test
    fun `no zoom at all means no change`() {
        assertEquals(100.0, Sensitivity.relative(base, base, Match.Centre), 1e-9)
        assertEquals(100.0, Sensitivity.relative(base, base, Match.Ratio), 1e-9)
    }

    @Test
    fun `converting between two identical scopes changes nothing`() {
        assertEquals(37.89, Sensitivity.convert(37.89, scope, scope, Match.Centre), 1e-9)
        assertEquals(37.89, Sensitivity.convert(37.89, scope, scope, Match.Ratio), 1e-9)
    }

    @Test
    fun `converting between scopes does not depend on the view slider`() {
        // A scope that zooms less needs a higher setting to feel the same.
        val looser = 68.7
        val converted = Sensitivity.convert(37.89, scope, looser, Match.Centre)
        assertTrue("$converted should exceed 37.89", converted > 37.89)

        // The same answer arrives by going through either field of view.
        val viaBoth = { fov: Double ->
            val target = Sensitivity.relative(fov, looser, Match.Centre)
            val source = Sensitivity.relative(fov, scope, Match.Centre)
            37.89 * target / source
        }
        assertEquals(converted, viaBoth(103.0), 1e-9)
        assertEquals(converted, viaBoth(80.0), 1e-9)
    }

    @Test
    fun `a magnification is read the way players quote it`() {
        // Ana and Widowmaker are called 2x, and 103 over 50.94 is 2.02.
        assertEquals(2.02, base / scope, 0.01)
        assertEquals(scope, Sensitivity.fovFromMagnification(base, base / scope), 1e-9)
    }

    @Test
    fun `only measured scopes are shipped`() {
        val heroes = Sensitivity.KNOWN.map { it.hero }
        assertEquals(listOf("Ana", "Widowmaker"), heroes)
        assertTrue("Ashe's scope is an estimate, not a measurement", "Ashe" !in heroes)
        Sensitivity.KNOWN.forEach { assertEquals(50.94, it.fov, 1e-9) }
    }
}
