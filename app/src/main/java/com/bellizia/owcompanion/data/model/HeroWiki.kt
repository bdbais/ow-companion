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
    val patches: List<PatchEntry> = emptyList(),
)

@Serializable
data class AbilityWiki(
    val name: String? = null,
    val description: String = "",
    /** File name under `assets/heroes/`. */
    val icon: String? = null,
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
