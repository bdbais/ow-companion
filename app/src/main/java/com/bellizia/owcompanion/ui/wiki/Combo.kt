package com.bellizia.owcompanion.ui.wiki

import com.bellizia.owcompanion.data.model.HeroWiki
import com.bellizia.owcompanion.sim.WeaponSpec

/** One thing you press, and what it is worth. */
data class ComboStep(
    val name: String,
    val damage: Double,
    /** Seconds this step takes before the next one can start. */
    val seconds: Double,
    /** True when the figure is a second of sustained fire rather than one trigger pull. */
    val perSecond: Boolean = false,
)

/**
 * The most damage a hero can put on one target in a single opening.
 *
 * The wiki talks about combos two hundred and thirty-five times and never once as a
 * sequence with numbers - "a single headshot followed by a quick melee" is as precise as it
 * gets. So this is computed rather than copied, out of figures that are each individually
 * sourced: every ability once, then a melee, then one shot of the hero's hardest-hitting
 * weapon. Roadhog's hook, melee and point-blank Scrap Gun come to 240, which is why that
 * combo kills anyone with 200 health.
 *
 * What it deliberately does not claim is a kill. Whether the sequence lands before the
 * target is healed, breaks line of sight or simply walks away depends on things no dataset
 * holds. The total and the time are facts; the outcome is not.
 */
object Combo {

    /** Abilities first, because they are what opens a fight, then melee, then the shot. */
    fun stepsFor(hero: HeroWiki, weapons: List<WeaponSpec>): List<ComboStep> {
        val steps = mutableListOf<ComboStep>()

        hero.damagingAbilities
            .filter { (it.damage ?: 0.0) > 0 }
            .sortedByDescending { it.damage }
            .forEach { ability ->
                steps += ComboStep(
                    name = ability.name.orEmpty(),
                    damage = ability.damage ?: 0.0,
                    seconds = ability.castTime ?: 0.0,
                )
            }

        val mine = weapons.filter { it.hero == hero.name }
        mine.firstOrNull { it.name == "Quick Melee" }?.let { melee ->
            steps += ComboStep(
                name = melee.name,
                damage = contribution(melee),
                seconds = melee.shotTime ?: 1.0,
            )
        }

        // One shot of whichever gun hits hardest. Not a burst: a combo is what lands before
        // the target reacts, and a magazine is not that.
        //
        // A beam has no such thing as one shot - a tick of Moira's drain is three damage,
        // which would make her combo look absurd - so it contributes a second of fire, and
        // says so.
        // A perked gun is not the gun you start the match with, so the plain combo uses
        // what every player of this hero actually has.
        mine.filter { it.name != "Quick Melee" && it.perk == null }
            .maxByOrNull { contribution(it) }
            ?.let { weapon ->
                val beam = weapon.type.contains("beam")
                steps += ComboStep(
                    name = weapon.name,
                    damage = contribution(weapon),
                    seconds = if (beam) 1.0 else weapon.shotTime ?: 0.0,
                    perSecond = beam,
                )
            }

        return steps
    }

    /**
     * What one weapon adds to an opening, at point-blank range before falloff.
     *
     * One trigger pull for anything that fires shots; one second for a beam, whose ticks
     * are far too small to mean anything on their own.
     */
    private fun contribution(spec: WeaponSpec): Double {
        val perPellet = spec.damage.dpshot.firstOrNull() ?: 0.0
        val pellets = spec.pellets.firstOrNull() ?: 1.0
        val perShot = perPellet * pellets
        if (!spec.type.contains("beam")) return perShot
        val ticks = spec.tickRate ?: spec.fireRate ?: 1.0
        return perShot * ticks
    }
}
