package com.bellizia.owcompanion.sim

import kotlin.math.roundToInt

/** A hero's numbers with a set of Stadium items applied, the way the Armory shows them. */
data class StadiumStats(
    val health: Int,
    val shields: Int,
    val armor: Int,
    /** Percentage points added, e.g. 25.0 for +25%. */
    val weaponPower: Double,
    val abilityPower: Double,
    val attackSpeed: Double,
    val cooldownReduction: Double,
    val moveSpeed: Double,
    val weaponLifesteal: Double,
    val abilityLifesteal: Double,
    val maxAmmo: Double,
) {
    val totalHitpoints: Int get() = health + shields + armor
}

/** Which abilities a chosen item touches, so a build says what it actually changes. */
data class BoostedAbility(
    val ability: String,
    val items: List<StadiumItem>,
)

/**
 * Adds up what a set of items does to a hero.
 *
 * Every buff is counted, not only the two the damage simulation can act on. Weapon Power
 * and Attack Speed drive the damage figure; the rest are shown because they are the reason
 * half the catalogue exists, and a build that only reported damage would quietly suggest
 * that health and cooldowns do not matter.
 */
object StadiumStatsCalculator {

    private val FLAT = mapOf(
        "health" to "health",
        "shields" to "shields",
        "armor" to "armor",
        "max ammo" to "maxAmmo",
    )

    fun apply(hero: Hero?, items: List<StadiumItem>): StadiumStats {
        var health = (hero?.health ?: 0).toDouble()
        var shields = (hero?.shields ?: 0).toDouble()
        var armor = (hero?.armor ?: 0).toDouble()
        val percentages = mutableMapOf<String, Double>()

        for (item in items) {
            for (buff in item.buffs) {
                val stat = buff.stat.lowercase().trim()
                val value = buff.value ?: continue
                when {
                    stat == "health" && !buff.percent -> health += value
                    stat == "shields" && !buff.percent -> shields += value
                    stat == "armor" && !buff.percent -> armor += value
                    else -> percentages[stat] = (percentages[stat] ?: 0.0) + value
                }
            }
        }

        return StadiumStats(
            health = health.roundToInt(),
            shields = shields.roundToInt(),
            armor = armor.roundToInt(),
            weaponPower = percentages["weapon power"] ?: 0.0,
            abilityPower = percentages["ability power"] ?: 0.0,
            attackSpeed = percentages["attack speed"] ?: 0.0,
            cooldownReduction = percentages["cooldown reduction"] ?: 0.0,
            moveSpeed = percentages["move speed"] ?: 0.0,
            weaponLifesteal = percentages["weapon lifesteal"] ?: 0.0,
            abilityLifesteal = percentages["ability lifesteal"] ?: 0.0,
            maxAmmo = percentages["max ammo"] ?: 0.0,
        )
    }

    private val WEAPON_BUFFS = setOf("weapon power", "attack speed", "weapon lifesteal", "max ammo")
    private val ABILITY_BUFFS = setOf("ability power", "ability lifesteal", "cooldown reduction")

    /**
     * Which of the hero's abilities each item actually bears on.
     *
     * Attribution follows the buff, not the item's shelf in the Armory: an item filed under
     * Ability that only grants health boosts no ability at all, and saying it did would be
     * a small lie repeated on every line. Hero-specific items naming their ability in the
     * text are matched to it directly.
     */
    fun boostedAbilities(hero: Hero?, items: List<StadiumItem>): List<BoostedAbility> {
        val abilities = hero?.abilities.orEmpty()
        if (abilities.isEmpty()) return emptyList()

        val byAbility = linkedMapOf<String, MutableList<StadiumItem>>()
        for (item in items) {
            val named = abilities.filter { ability ->
                item.description.contains(ability, ignoreCase = true) ||
                    item.name.contains(ability, ignoreCase = true)
            }
            val stats = item.buffs.map { it.stat.lowercase().trim() }.toSet()
            val targets = when {
                named.isNotEmpty() -> named
                // The weapon is the hero's first listed ability.
                stats.any { it in WEAPON_BUFFS } -> abilities.take(1)
                stats.any { it in ABILITY_BUFFS } -> abilities.drop(1)
                else -> emptyList()
            }
            targets.forEach { byAbility.getOrPut(it) { mutableListOf() }.add(item) }
        }
        return byAbility.map { (ability, list) -> BoostedAbility(ability, list) }
    }
}
