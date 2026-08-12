package com.bellizia.owcompanion.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The duel engine, checked on the question it exists to answer.
 *
 * The example that prompted it: Reinhardt under Nano Boost swinging at a Mauga who is
 * healing himself. A damage chart can say what the hammer does and what the healing does;
 * only putting them against each other says whether the fight is winnable.
 */
class DuelTest {

    private val hammer = WeaponSpec(
        name = "Rocket Hammer",
        hero = "Reinhardt",
        type = "melee",
        damage = DamageSpec(dpshot = listOf(85.0), maxRange = 4.0),
        fireRate = 0.9,
        critFactor = 1.0,
    )

    private val mauga = Duel.Defender(
        target = Breakpoints.Target(name = "Mauga", health = 350, armor = 500),
    )

    private fun resolve(
        weapon: WeaponSpec = hammer,
        defender: Duel.Defender = mauga,
        buffs: Modifiers = Modifiers.NONE,
    ) = Duel.resolve(weapon, WeaponModel(weapon), defender, buffs)

    @Test
    fun `with nothing healing the fight is a breakpoint`() {
        val outcome = resolve()
        assertEquals(Duel.Verdict.Kills, outcome.verdict)
        assertNotNull("a hammer should kill eventually", outcome.shotsToKill)
        assertNotNull(outcome.secondsToKill)
    }

    @Test
    fun `nano boost shortens the fight`() {
        val plain = resolve().secondsToKill!!
        val nanoed = resolve(buffs = Modifiers(nanoboostOffence = true)).secondsToKill!!
        assertTrue("Nano should make it quicker, was $plain then $nanoed", nanoed < plain)
    }

    @Test
    fun `healing that beats the damage means it never dies`() {
        val healed = mauga.copy(healingPerSecond = 400.0)
        val outcome = resolve(defender = healed)
        assertEquals(Duel.Verdict.Outhealed, outcome.verdict)
        assertNull("an unwinnable fight has no time to kill", outcome.secondsToKill)
        assertTrue("it should say how much more is needed", outcome.shortfall > 0)
    }

    @Test
    fun `the breakpoint survives even when the fight is unwinnable`() {
        // The two halves are independent on purpose: how many shots it would take if
        // nothing healed is still worth knowing when something does.
        val outcome = resolve(defender = mauga.copy(healingPerSecond = 400.0))
        assertNotNull(outcome.shotsToKill)
    }

    @Test
    fun `a fight decided by nothing is called a stalemate rather than a slow win`() {
        val damage = resolve().damagePerSecond
        val outcome = resolve(defender = mauga.copy(healingPerSecond = damage))
        assertEquals(Duel.Verdict.Stalemate, outcome.verdict)
        assertNull(outcome.secondsToKill)
    }

    @Test
    fun `mitigation on the target lengthens the fight`() {
        val open = resolve().secondsToKill!!
        val blocked = resolve(
            defender = mauga.copy(mitigation = Modifiers(cardiacOverdrive = true)),
        ).secondsToKill!!
        assertTrue("40% off the damage should show, was $open then $blocked", blocked > open)
    }

    @Test
    fun `help is ranked by what it does to this target, and says whether it is enough`() {
        val losing = resolve(defender = mauga.copy(healingPerSecond = 300.0))
        val small = WeaponSpec(
            name = "Peashooter",
            hero = "Test",
            type = "hitscan",
            damage = DamageSpec(dpshot = listOf(5.0)),
            fireRate = 2.0,
        )
        val big = WeaponSpec(
            name = "Cannon",
            hero = "Test",
            type = "hitscan",
            damage = DamageSpec(dpshot = listOf(120.0)),
            fireRate = 4.0,
        )
        val help = Duel.helpFor(
            outcome = losing,
            candidates = listOf(small to WeaponModel(small), big to WeaponModel(big)),
            defender = mauga.copy(healingPerSecond = 300.0),
        )
        assertEquals("Cannon", help.first().weapon.name)
        assertTrue("the big one closes a 300 hps gap", help.first().enough)
        assertTrue("the small one does not", !help.last().enough)
    }
}
