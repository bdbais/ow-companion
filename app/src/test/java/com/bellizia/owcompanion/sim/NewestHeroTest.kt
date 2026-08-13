package com.bellizia.owcompanion.sim

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The three weapons the season's newest hero brings, against the figures their wiki states.
 *
 * These came in by hand rather than through the parser, which is why they are worth a test.
 * The Fandom page the pipeline reads is still a stub - no ability block at all - so two of
 * these weapons were held out of the chart entirely for missing a fire rate, and the third
 * was never seen, being an ability rather than a weapon. The numbers below come from the
 * community wiki, and every one of them is a figure that wiki publishes itself: if the
 * model cannot reproduce them, the model is wrong, not the source.
 *
 * Fired point blank at the body, so no spread luck is involved and the result is the
 * deterministic best case the published figures also describe.
 */
class NewestHeroTest {

    private val simulator = Simulator()
    private val pointBlank = Crosshair(x = 0.0, z = 1.0, distance = 1.0)

    private fun train(spec: WeaponSpec) =
        simulator.simulateMean(WeaponModel(spec), pointBlank, Modifiers.NONE)

    /** 12 a shot at 7.813 a second, 30 rounds, 1.5 s to reload. */
    private val pilotGun = WeaponSpec(
        name = "Portable Fusion Repeater",
        hero = "D.Mon",
        type = "hitscan",
        damage = DamageSpec(dpshot = listOf(12.0, 3.6), falloff = listOf(30.0, 40.0)),
        fireRate = 7.813,
        ammo = 30.0,
        reloadTime = 1.5,
        critFactor = 2.0,
    )

    /** 16 a shot at 8.9 a second, for the four seconds the mech's gun runs. */
    private val mechGun = WeaponSpec(
        name = "Fusion Repeater",
        hero = "D.Mon",
        type = "hitscan",
        damage = DamageSpec(dpshot = listOf(16.0, 4.8), falloff = listOf(30.0, 40.0)),
        fireRate = 8.9,
        ammo = 35.0,
        reloadTime = 0.288,
        critFactor = 2.0,
    )

    /** 65 a swing, alternating 0.72 s and 0.592 s: 0.656 s on average. */
    private val saber = WeaponSpec(
        name = "Plasma Saber",
        hero = "D.Mon",
        type = "melee",
        damage = DamageSpec(dpshot = listOf(65.0), maxRange = 4.0),
        fireRate = 1.5244,
        critFactor = 1.0,
    )

    @Test
    fun `the pilot's gun matches the published 93 point 75 firing`() {
        assertEquals(93.75, train(pilotGun).dpsWithoutReload, 0.5)
    }

    @Test
    fun `and the published 67 point 42 once reloading counts`() {
        // The figure the wiki prints beside the first one, and the reason ammo and reload
        // are here at all: 360 damage spread over 30 shots plus a second and a half.
        assertEquals(67.42, train(pilotGun).dps, 0.5)
    }

    @Test
    fun `the mech's gun matches the published 142 point 86`() {
        assertEquals(142.86, train(mechGun).dpsWithoutReload, 0.5)
    }

    @Test
    fun `the saber matches the published 99 point 09`() {
        assertEquals(99.09, train(saber).dpsWithoutReload, 0.5)
    }

    @Test
    fun `the saber cannot crit and the guns can`() {
        // Stated by absence: the saber's entry carries no headshot field and is keyworded
        // melee. Getting this backwards would flatter it by a hundred per cent.
        assertEquals(1.0, saber.critFactor)
        assertEquals(2.0, pilotGun.critFactor)
        assertEquals(2.0, mechGun.critFactor)
    }

    @Test
    fun `falloff takes both guns down to the game's floor`() {
        // Thirty per cent past forty metres, which is what every other falloff weapon in
        // the dataset does and the one number the source does not state.
        assertEquals(0.30, pilotGun.damage.dpshot.last() / pilotGun.damage.dpshot.first(), 0.001)
        assertEquals(0.30, mechGun.damage.dpshot.last() / mechGun.damage.dpshot.first(), 0.001)
    }
}
