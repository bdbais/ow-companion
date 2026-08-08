package com.bellizia.owcompanion.data.model

import kotlinx.serialization.Serializable

@Serializable
data class WikiData(val heroes: List<HeroWiki> = emptyList())

@Serializable
data class HeroWiki(
    val key: String,
    val name: String,
    /** Hex colour, shared with the damage chart so a hero looks the same in both. */
    val color: String = "#9aa4b2",
    val role: String = "",
    val subrole: String? = null,
    val description: String? = null,
    val location: String? = null,
    val age: Int? = null,
    val birthday: String? = null,
    val health: Int? = null,
    val shields: Int? = null,
    val armor: Int? = null,
    val totalHitpoints: Int? = null,
    /** File name under `assets/heroes/`. */
    val portrait: String? = null,
    val abilities: List<AbilityWiki> = emptyList(),
    /** ISO date the hero became permanently playable. */
    val releaseDate: String? = null,
    /** Position in the roster's arrival order; absent for the launch heroes. */
    val heroNumber: Int? = null,
    val perks: List<PerkWiki> = emptyList(),
    val patches: List<PatchEntry> = emptyList(),
    val matchups: List<MatchupWiki> = emptyList(),
    /** Damaging abilities Blizzard's own roster does not list, chiefly perks. */
    val extraAbilities: List<ExtraAbility> = emptyList(),
) {
    /** Everything this hero can do damage with, other than a weapon. */
    val damagingAbilities: List<AbilityWiki>
        get() = abilities.filter { it.damage != null } +
            extraAbilities
                .filter { it.damage != null && it.kind != "perk" }
                .map {
                    AbilityWiki(
                        name = it.name,
                        damage = it.damage,
                        cooldown = it.cooldown,
                        castTime = it.castTime,
                        damageLines = it.lines,
                        damageUncertain = it.uncertain,
                    )
                }
}

@Serializable
data class ExtraAbility(
    val name: String = "",
    /** `ability` or `perk`; a perk is only true of players who picked it. */
    val kind: String = "ability",
    val damage: Double? = null,
    val cooldown: Double? = null,
    val castTime: Double? = null,
    val lines: List<String> = emptyList(),
    val uncertain: Boolean = false,
)

/**
 * How this hero fares against one particular opponent, in the wiki's words.
 *
 * Written from the perspective of playing *as* this hero, which is the wiki's own house
 * rule, so `matchup` reads as advice rather than a scouting report on the enemy.
 */
@Serializable
data class MatchupWiki(
    val opponent: String = "",
    /** The opponent's dataset key, for looking up their portrait. */
    val key: String = "",
    /** The opponent's role: `tank`, `damage` or `support`. */
    val role: String = "",
    val matchup: String = "",
    val synergy: String = "",
    /** The wiki's verbatim grade, e.g. `EVEN -> WEAK MATCHUP`; absent on older pages. */
    val rating: String? = null,
    /** Normalised from [rating]: `very-weak`, `weak`, `even`, `strong`, `very-strong`. */
    val stance: String? = null,
    /** How urgently to shoot them - a separate axis from who wins the duel. */
    val priority: String? = null,
    /** How dangerous engaging is: `low`, `medium`, `high`, `extreme`. */
    val risk: String? = null,
)

/**
 * A perk a player picks mid-match. Two minor and two major per hero.
 *
 * These are documented here but not simulated: most change abilities the damage model never
 * covers, and the few that do change a weapon - Ana's Headhunter letting her rifle crit -
 * are stated explicitly in the dataset rather than inferred from the wording.
 */
@Serializable
data class PerkWiki(
    val name: String,
    /** `minor` or `major`. */
    val tier: String = "minor",
    val description: String = "",
)

@Serializable
data class AbilityWiki(
    val name: String? = null,
    val description: String = "",
    /** File name under `assets/heroes/`. */
    val icon: String? = null,
    /**
     * Most damage one cast does to one enemy, where the wiki gives a figure.
     *
     * Never a sum of the lines below: several of them are the same ability in different
     * states rather than parts of one hit. When [damageUncertain] is set this is the
     * largest single line rather than a stated total, and the lines say the rest.
     */
    val damage: Double? = null,
    val cooldown: Double? = null,
    val castTime: Double? = null,
    val damageLines: List<String> = emptyList(),
    val damageUncertain: Boolean = false,
)

@Serializable
data class PatchEntry(
    val date: String = "",
    /** Which game mode the change applied to: `owpvp`, `owstadium`, and so on. */
    val mode: String = "owpvp",
    val changes: List<PatchChange> = emptyList(),
    val stats: List<StatChange> = emptyList(),
    /** Blizzard's own explanation of the change, when they gave one. */
    val comments: List<String> = emptyList(),
)

@Serializable
data class PatchChange(
    val ability: String = "",
    val text: String = "",
)

@Serializable
data class StatChange(
    val ability: String = "",
    val stat: String = "",
    val from: Double = 0.0,
    val to: Double = 0.0,
    val unit: String? = null,
    /** `buff`, `nerf` or `neutral`, judged per stat - a rising cooldown is a nerf. */
    val direction: String = "neutral",
)
