package com.bellizia.owcompanion

import com.bellizia.owcompanion.ui.FilterTaps
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The tap rule is shared by every filter row in the app, so getting it wrong is wrong in six
 * places at once. The clock is a parameter precisely so the double-tap window can be tested
 * without waiting for it.
 */
class FilterTapsTest {

    private val all = setOf("tank", "damage", "support")

    @Test
    fun `one tap removes, another puts back`() {
        val taps = FilterTaps<String>()
        val without = taps.onTap("tank", all, all, now = 0)
        assertEquals(setOf("damage", "support"), without)

        // Far enough apart to be two separate taps rather than a double.
        val restored = taps.onTap("tank", without, all, now = 5_000)
        assertEquals(all, restored)
    }

    @Test
    fun `two quick taps narrow to that one alone`() {
        val taps = FilterTaps<String>()
        val first = taps.onTap("tank", all, all, now = 1_000)
        val second = taps.onTap("tank", first, all, now = 1_150)
        assertEquals(setOf("tank"), second)
    }

    @Test
    fun `double tapping the only one left clears the filter`() {
        val taps = FilterTaps<String>()
        val alone = setOf("tank")
        val first = taps.onTap("tank", alone, all, now = 0)
        val second = taps.onTap("tank", first, all, now = 120)
        assertEquals(all, second)
    }

    @Test
    fun `the second tap ignores what the first one did`() {
        // Whether the first tap added or removed, the pair means "just this one". Judging
        // the second tap against the state the first tap produced would flip the meaning.
        val taps = FilterTaps<String>()
        val narrowed = setOf("damage")
        val first = taps.onTap("tank", narrowed, all, now = 0)
        assertEquals(setOf("damage", "tank"), first)
        val second = taps.onTap("tank", first, all, now = 100)
        assertEquals(setOf("tank"), second)
    }

    @Test
    fun `two quick taps on different chips are not a double tap`() {
        val taps = FilterTaps<String>()
        val first = taps.onTap("tank", all, all, now = 0)
        val second = taps.onTap("damage", first, all, now = 80)
        assertEquals(setOf("support"), second)
    }

    @Test
    fun `turning off the last one shows everything rather than nothing`() {
        val taps = FilterTaps<String>()
        val emptied = taps.onTap("tank", setOf("tank"), all, now = 0)
        assertEquals(all, emptied)
    }
}
