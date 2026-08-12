package com.bellizia.owcompanion.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two weapons built around setting a target alight, checked against the wiki's own
 * damage-per-second figures.
 *
 * That is the point of this file: the wiki states what these weapons do to a burning
 * target as finished numbers, so the model either reproduces them or it is wrong. Both
 * reports said the app behaved as though nothing ever caught fire.
 *
 * Everything is fired point blank at the body, with no spread luck involved, so the figures
 * are the deterministic best case the wiki also describes.
 */
class BurningTest {

    private val simulator = Simulator()
    private val pointBlank = Crosshair(x = 0.0, z = 1.0, distance = 1.0)

    private fun dps(spec: WeaponSpec, modifiers: Modifiers): Double =
        simulator.simulateMean(WeaponModel(spec), pointBlank, modifiers).dpsWithoutReload

    /** Mauga's chainguns: 4 a shot at 17.36 shots a second. */
    private fun chaingun(name: String, burningFactor: Double? = null, burnDps: Double? = null) =
        WeaponSpec(
            name = name,
            hero = "Mauga",
            type = "hitscan",
            damage = DamageSpec(dpshot = listOf(4.0)),
            fireRate = 17.36,
            ammo = 300.0,
            reloadTime = 2.0,
            critFactor = 2.0,
            burningFactor = burningFactor,
            burnDps = burnDps,
        )

    @Test
    fun `Gunny alone matches the wiki's 69 point 44`() {
        val train = dps(chaingun("Incendiary Chaingun", burnDps = 15.0), Modifiers.NONE)
        assertEquals(69.44, train, 0.5)
    }

    @Test
    fun `Gunny with its own burn matches the wiki's 84 point 44`() {
        val train = dps(
            chaingun("Incendiary Chaingun", burnDps = 15.0),
            Modifiers(burning = true),
        )
        assertEquals(84.44, train, 0.5)
    }

    @Test
    fun `Cha-Cha doubles on a burning target and leaves everything else alone`() {
        val chacha = chaingun("Volatile Chaingun", burningFactor = 2.0)
        val cold = dps(chacha, Modifiers.NONE)
        val alight = dps(chacha, Modifiers(burning = true))
        assertEquals(2.0, alight / cold, 0.01)
    }

    @Test
    fun `Fan the Flames matches both figures the wiki gives`() {
        val fan = WeaponSpec(
            name = "Fan the Flames",
            hero = "Anran",
            type = "beam",
            damage = DamageSpec(dpshot = listOf(6.0)),
            pellets = listOf(6.0),
            fireRate = 1.66667,
            ammo = 10.0,
            reloadTime = 1.5,
            critFactor = 1.0,
            burningFactor = 2.0,
            burnDps = 20.0,
        )
        // 36 a volley at 1.667 volleys a second.
        assertEquals(60.0, dps(fan, Modifiers.NONE), 1.0)
        // 72 a volley, plus the burn it has lifted from 10 a second to 20.
        assertEquals(140.0, dps(fan, Modifiers(burning = true)), 1.0)
    }

    @Test
    fun `a weapon that has nothing to do with fire is untouched`() {
        val plain = chaingun("Plain")
        assertEquals(dps(plain, Modifiers.NONE), dps(plain, Modifiers(burning = true)), 1e-9)
    }

    @Test
    fun `burning stacks with a damage buff rather than replacing it`() {
        val chacha = chaingun("Volatile Chaingun", burningFactor = 2.0)
        val both = dps(chacha, Modifiers(burning = true, damageBoost = true))
        val onlyBurning = dps(chacha, Modifiers(burning = true))
        assertTrue("damage boost should still add its 30%", both > onlyBurning * 1.25)
    }

    /**
     * Report 3: both barrels at once, which is how he is actually played.
     *
     * Not twice one gun - the wiki gives it its own falloff and spread, and the magazine
     * drains twice as fast - but at point blank the three damage figures it lists should
     * come straight back out.
     */
    @Test
    fun `both chainguns together match all three of the wiki's figures`() {
        val both = WeaponSpec(
            name = "Both Chainguns",
            hero = "Mauga",
            type = "hitscan",
            damage = DamageSpec(dpshot = listOf(8.0)),
            fireRate = 17.36,
            ammo = 300.0,
            ammoUsage = 2.0,
            reloadTime = 2.0,
            critFactor = 2.0,
            burningFactor = 1.5,
            burnDps = 15.0,
        )
        assertEquals(138.88, dps(both, Modifiers.NONE), 1.0)
        // Only Cha-Cha crits on a lit target, so the pair gains half again, not double.
        assertEquals(208.32, dps(both, Modifiers(burning = true)) - 15.0, 1.5)
        assertEquals(223.32, dps(both, Modifiers(burning = true)), 1.5)
    }
}
