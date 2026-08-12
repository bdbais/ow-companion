package com.bellizia.owcompanion.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Self-healing, and the case it was written for.
 *
 * "Reinhardt with Nano against a Mauga who is healing himself" was answerable before this
 * only by looking up Cardiac Overdrive somewhere else and typing the number in. The point
 * of the table is that the app knows what a hero does for themselves.
 */
class SelfHealTest {

    @Test
    fun `a flat rate ignores what the hero is dealing`() {
        val breather = SelfHeal.forHero("Roadhog").first()
        assertEquals(150.0, SelfHeal.rateOf(breather, ownDamagePerSecond = 0.0), 1e-9)
        assertEquals(150.0, SelfHeal.rateOf(breather, ownDamagePerSecond = 400.0), 1e-9)
    }

    @Test
    fun `a share scales with what the hero is dealing`() {
        val overdrive = SelfHeal.forHero("Mauga").first()
        assertTrue(overdrive is SelfHeal.ShareOfOwnDamage)
        // Cardiac Overdrive returns all of it, so his own output is his healing.
        assertEquals(139.0, SelfHeal.rateOf(overdrive, ownDamagePerSecond = 139.0), 1e-9)
        assertEquals(0.0, SelfHeal.rateOf(overdrive, ownDamagePerSecond = 0.0), 1e-9)
    }

    @Test
    fun `Reaper gets a third of his back`() {
        val reaping = SelfHeal.forHero("Reaper").first()
        assertEquals(60.0, SelfHeal.rateOf(reaping, ownDamagePerSecond = 200.0), 1e-9)
    }

    @Test
    fun `a hero with nothing of their own gets an empty list`() {
        assertTrue(SelfHeal.forHero("Widowmaker").isEmpty())
    }

    @Test
    fun `every entry names a real hero and a positive figure`() {
        SelfHeal.All.forEach { heal ->
            assertTrue("${heal.hero} has no name", heal.hero.isNotBlank())
            assertTrue("${heal.ability} has no name", heal.ability.isNotBlank())
            val rate = SelfHeal.rateOf(heal, ownDamagePerSecond = 100.0)
            assertTrue("${heal.hero} ${heal.ability} heals nothing", rate > 0.0)
        }
    }

    @Test
    fun `a Mauga healing himself survives what kills him otherwise`() {
        val hammer = WeaponSpec(
            name = "Rocket Hammer",
            hero = "Reinhardt",
            type = "melee",
            damage = DamageSpec(dpshot = listOf(85.0), maxRange = 4.0),
            fireRate = 0.9,
            critFactor = 1.0,
        )
        val mauga = Breakpoints.Target(name = "Mauga", health = 350, armor = 500)

        val open = Duel.resolve(hammer, WeaponModel(hammer), Duel.Defender(mauga))
        assertEquals(Duel.Verdict.Kills, open.verdict)

        // Cardiac Overdrive returns everything he deals, and his chainguns deal a lot.
        val overdrive = SelfHeal.forHero("Mauga").first()
        val healing = SelfHeal.rateOf(overdrive, ownDamagePerSecond = 138.88)
        val healed = Duel.resolve(
            hammer,
            WeaponModel(hammer),
            Duel.Defender(mauga, healingPerSecond = healing),
        )
        assertEquals(Duel.Verdict.Outhealed, healed.verdict)
        assertTrue("it should say how much more is needed", healed.shortfall > 0)
    }
}
