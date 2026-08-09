package com.bellizia.owcompanion

import com.bellizia.owcompanion.sim.Breakpoints
import com.bellizia.owcompanion.sim.DamageSpec
import com.bellizia.owcompanion.sim.Modifiers
import com.bellizia.owcompanion.sim.WeaponSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A shot count is a whole number, so it is either right or obviously wrong - which makes it
 * worth pinning the cases that carry a rule rather than only the arithmetic.
 */
class BreakpointsTest {

    private fun weapon(
        damage: Double,
        pellets: Int = 1,
        crit: Double? = 2.0,
        type: String = "hitscan",
    ) = WeaponSpec(
        hero = "Test",
        name = "Test",
        type = type,
        damage = DamageSpec(dpshot = listOf(damage)),
        pellets = listOf(pellets.toDouble()),
        critFactor = crit,
    )

    private val squishy = Breakpoints.Target("250", health = 250)

    @Test
    fun `a whole number of shots, rounded up`() {
        // 250 over 60 is 4.17, and there is no such thing as a fifth of a shot.
        assertEquals(5, Breakpoints.shotsToKill(weapon(60.0), squishy).body)
        // Exactly five shots of fifty stays five, rather than becoming six.
        assertEquals(5, Breakpoints.shotsToKill(weapon(50.0), squishy).body)
    }

    @Test
    fun `the head is worth aiming at only when it removes a shot`() {
        // 120 to the body is three shots; 240 to the head is two.
        val saves = Breakpoints.shotsToKill(weapon(120.0), squishy)
        assertEquals(3, saves.body)
        assertEquals(2, saves.head)
        assertTrue(saves.headSaves)

        // A weapon that already kills outright gains nothing from the head, which is the
        // only case where it truly saves nothing: anywhere else a crit eventually crosses
        // a threshold.
        val pointless = Breakpoints.shotsToKill(weapon(250.0), squishy)
        assertEquals(1, pointless.body)
        assertEquals(1, pointless.head)
        assertTrue("nothing is saved", !pointless.headSaves)
    }

    @Test
    fun `a weapon that cannot crit reports no head count at all`() {
        val result = Breakpoints.shotsToKill(weapon(60.0, crit = null), squishy)
        assertNull(result.head)
        assertTrue(!result.headSaves)
    }

    @Test
    fun `armour costs a shotgun far more than a sniper`() {
        // The same 120 damage a trigger pull, in one lump and in twelve pellets.
        val armoured = Breakpoints.Target("armoured", health = 250, armor = 100)
        val sniper = Breakpoints.shotsToKill(weapon(120.0), armoured)
        val shotgun = Breakpoints.shotsToKill(weapon(10.0, pellets = 12), armoured)

        // Against bare health they are the same weapon.
        assertEquals(
            Breakpoints.shotsToKill(weapon(120.0), squishy).body,
            Breakpoints.shotsToKill(weapon(10.0, pellets = 12), squishy).body,
        )
        // Against armour the pellets are taxed one by one, and it shows.
        assertTrue(
            "shotgun ${shotgun.body} should need more than sniper ${sniper.body}",
            shotgun.body > sniper.body,
        )
        assertTrue(shotgun.perBodyShot < sniper.perBodyShot)
    }

    @Test
    fun `shields are spent before armour, and take damage plainly`() {
        // 250 health behind 100 shields: the shields do not reduce anything.
        val shielded = Breakpoints.Target("shielded", health = 250, shields = 100)
        assertEquals(350.0, Breakpoints.shotsToKill(weapon(350.0), shielded).perBodyShot, 0.001)

        // The same pool as armour instead reduces every hit.
        val armoured = Breakpoints.Target("armoured", health = 250, armor = 100)
        assertTrue(Breakpoints.shotsToKill(weapon(350.0), armoured).perBodyShot < 350.0)
    }

    @Test
    fun `a buff can cross a threshold, which is the whole point`() {
        // Damage boost is thirty per cent: 100 a shot needs three, 130 needs two.
        val plain = Breakpoints.shotsToKill(weapon(100.0), squishy)
        val boosted = Breakpoints.shotsToKill(weapon(100.0), squishy, Modifiers(damageBoost = true))
        assertEquals(3, plain.body)
        assertEquals(2, boosted.body)
    }

    @Test
    fun `every distinct pool is offered once, smallest first`() {
        val heroes = listOf(
            Breakpoints.Target("Ana", 250),
            Breakpoints.Target("Ashe", 250),
            Breakpoints.Target("Reinhardt", 300, armor = 300),
            Breakpoints.Target("Nobody", 0),
        )
        val targets = Breakpoints.targetsFrom(heroes)
        assertEquals(2, targets.size)
        assertEquals(250, targets[0].total)
        assertEquals(600, targets[1].total)
    }
}
