package com.bellizia.owcompanion.sim

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The mitigation rules, which went unchecked long enough for every one of them to be wrong.
 *
 * Fortify, Take a Breather and Nano Boost shared one flat 50% - the Overwatch 1 figure -
 * while the wiki gives 45, 40 and 50. Nothing failed when that was corrected, because
 * nothing tested it.
 *
 * Each expected number below is the wiki's `damage_red` for that ability.
 */
class ModifiersTest {

    private fun kept(modifiers: Modifiers) = modifiers.factorMeleeBeam

    private fun assertClose(expected: Double, actual: Double, what: String) {
        assertTrue(
            "$what: expected $expected but was $actual",
            abs(expected - actual) < 1e-9,
        )
    }

    @Test
    fun `no modifiers leave damage alone`() {
        assertEquals(1.0, kept(Modifiers.NONE), 1e-9)
    }

    @Test
    fun `each mitigation uses the figure the wiki states`() {
        assertClose(0.55, kept(Modifiers(fortify = true)), "Fortify is 45%")
        assertClose(0.60, kept(Modifiers(takeABreather = true)), "Take a Breather is 40%")
        assertClose(0.50, kept(Modifiers(nanoboostDefence = true)), "Nano Boost is 50%")
        assertClose(0.50, kept(Modifiers(overrun = true)), "Overrun is 50%")
        assertClose(0.60, kept(Modifiers(cardiacOverdrive = true)), "Cardiac Overdrive is 40%")
        assertClose(0.25, kept(Modifiers(powerBlock = true)), "Power Block is 75%")
        assertClose(0.25, kept(Modifiers(nemesisBlock = true)), "Ramattra's Block is 75%")
        assertClose(0.40, kept(Modifiers(spikeGuard = true)), "Spike Guard is 60%")
    }

    @Test
    fun `stacked buffs stop at half`() {
        // 45% and 40% multiply out to 67% mitigation, which the wiki caps at 50%.
        val both = Modifiers(fortify = true, takeABreather = true)
        assertClose(0.50, kept(both), "two buffs are held to the 50% cap")

        val everything = Modifiers(
            fortify = true,
            takeABreather = true,
            nanoboostDefence = true,
            overrun = true,
            cardiacOverdrive = true,
        )
        assertClose(0.50, kept(everything), "five buffs are still held to the cap")
    }

    @Test
    fun `a block ignores the cap and multiplies with what the buffs left`() {
        // Power Block is not a buff, so it is not capped: 75% on its own.
        // With Fortify's 45% underneath, 55% of the damage survives Fortify and a quarter
        // of that survives the block.
        val blocked = Modifiers(powerBlock = true, fortify = true)
        assertClose(0.55 * 0.25, kept(blocked), "Power Block stacks past the cap")
    }

    @Test
    fun `two blocks do not stack because no hero can hold both`() {
        val impossible = Modifiers(powerBlock = true, spikeGuard = true)
        assertClose(0.25, kept(impossible), "the stronger block wins rather than compounding")
    }

    @Test
    fun `amplification still doubles shots and not beams`() {
        val amplified = Modifiers(amplificationMatrix = true)
        assertEquals(2.0, amplified.factor, 1e-9)
        assertEquals(1.0, amplified.factorMeleeBeam, 1e-9)
    }
}
