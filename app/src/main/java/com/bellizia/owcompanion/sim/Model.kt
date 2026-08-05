package com.bellizia.owcompanion.sim

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A weapon set as produced by the dataset pipeline.
 *
 * Specs are expected to be **normalised**: defaults resolved, beams already converted from
 * a damage-per-second figure to damage-per-tick, `shotTime` derived from `fireRate`, and so
 * on. Normalisation is a build-time concern so the engine never has to guess whether a
 * value has been through it already — see `tools/js_oracle/export_weapons.js`.
 */
@Serializable
data class WeaponSet(
    val meta: DatasetMeta = DatasetMeta(),
    val heroes: List<Hero> = emptyList(),
    val weapons: List<WeaponSpec> = emptyList(),
) {
    private val heroesByName: Map<String, Hero> = heroes.associateBy { it.name }

    fun hero(name: String): Hero? = heroesByName[name]
}

@Serializable
data class DatasetMeta(
    val source: String = "",
    val patch: String = "",
    val generatedBy: String = "",
    val normalized: Boolean = true,
)

@Serializable
data class Hero(
    val key: String,
    val name: String,
    /** Hex string, e.g. `#718ab3`; the chart colours each weapon by its hero. */
    val color: String,
    val role: String,
)

/**
 * Weapons whose damage or timing cannot be expressed by the shared rules and need bespoke
 * handling. The upstream implementation attached these as one-off closures keyed by weapon
 * name; making it an explicit field means the dataset declares the exception rather than
 * the engine pattern-matching on names.
 */
@Serializable
enum class WeaponBehavior {
    @SerialName("standard")
    Standard,

    /** Roadhog's alt fire: a slug up close, a spread of pellets past `rangeBall`. */
    @SerialName("scrapGunSecondary")
    ScrapGunSecondary,

    /** Zarya: damage scales with charge energy and cuts off hard at max range. */
    @SerialName("particleCannon")
    ParticleCannon,

    /** Symmetra: damage steps up through charge levels while the beam is held. */
    @SerialName("photonProjector")
    PhotonProjector,

    /** Doomfist: ammo regenerates one round at a time instead of reloading in full. */
    @SerialName("handCannon")
    HandCannon,
}

@Serializable
data class DamageSpec(
    /** One value, or `[near, far]` when [falloff] is set. */
    val dpshot: List<Double> = emptyList(),
    /** `[startRange, endRange]` in metres over which damage ramps from near to far. */
    val falloff: List<Double>? = null,
    /** Hard cutoff in metres: beyond it the weapon does nothing. */
    val maxRange: Double? = null,
    /** Damage-over-time weapons deal `dpshot` this many times per shot. */
    val segments: Double? = null,
    /** Seconds a single shot's effect lasts, for beams and damage-over-time weapons. */
    val duration: Double? = null,
    /** Damage per second, before normalisation into per-tick damage. */
    val dps: Double? = null,
    /** Multipliers per charge level, for [WeaponBehavior.PhotonProjector]. */
    val dpsFactors: List<Double>? = null,
    /** Seconds of sustained fire needed to reach the next charge level. */
    val levelChargingTime: Double? = null,
    /** Damage of the single close-range slug, for [WeaponBehavior.ScrapGunSecondary]. */
    val dpshotBall: Double? = null,
    /** Distance in metres within which the slug, rather than the spread, applies. */
    val rangeBall: Double? = null,
)

@Serializable
data class SpreadSpec(
    /** Full cone angle in degrees, constant. */
    val angle: Double? = null,
    /** Cone angle in degrees at its tightest, for weapons that bloom as they fire. */
    val minAngle: Double? = null,
    /** Cone angle in degrees once fully bloomed. */
    val maxAngle: Double? = null,
    /** `[from, to]` rounds spent over which the cone grows from min to max. */
    val spreadingAmmoRange: List<Double>? = null,
    /** Fixed per-pellet offsets as `[yaw, pitch]` degree pairs. */
    val constantAngles: List<List<Double>>? = null,
    /** Whether the fixed pattern is rotated by a random angle on every shot. */
    val randomlyRotated: Boolean = false,
    val fixedAngle: Double? = null,
)

@Serializable
data class BurstSpec(
    /** Rounds per burst. */
    val ammo: Double,
    /** Seconds between rounds within a burst. */
    val delay: Double,
)

@Serializable
data class WeaponSpec(
    val name: String,
    /** Hero display name; matches [Hero.name]. */
    val hero: String,
    /** `M1` / `M2` when a hero has more than one firing mode. */
    val mousebutton: String? = null,
    /**
     * Space-separated traits, e.g. `hitscan shotgun`, `arc projectile`, `projectile EOT`.
     * Drives falloff, travel time and which damage accounting a weapon gets.
     */
    val type: String,
    val behavior: WeaponBehavior = WeaponBehavior.Standard,
    /** One value, or `[near, far]` for [WeaponBehavior.ScrapGunSecondary]. */
    val pellets: List<Double> = listOf(1.0),
    val damage: DamageSpec = DamageSpec(),
    val spread: SpreadSpec? = null,
    /** Projectile speed in m/s; absent for hitscan weapons. */
    val velocity: Double? = null,
    val fireRate: Double? = null,
    val shotTime: Double? = null,
    val tickRate: Double? = null,
    val ammoUsage: Double? = null,
    /** Magazine size; `null` means unlimited. */
    val ammo: Double? = null,
    val reloadTime: Double? = null,
    /** Seconds between the trigger and the shot leaving the barrel. */
    val chargeDelay: Double? = null,
    /** Headshot multiplier; 1.0 for weapons that cannot crit. */
    val critFactor: Double? = null,
    val burst: BurstSpec? = null,
    /** Zarya's charge, 0-100. */
    val energy: Double? = null,
    val energyFactor: Double? = null,
    /** Seconds attributable to one shot, ignoring reload. */
    val dpsPeriodBase: Double? = null,
    /** Reload time amortised across the magazine. */
    val dpsPeriodAdd: Double? = null,
    /** Fraction of the inter-shot gap a drawn shot may occupy. */
    val filling: Double? = null,
) {
    val id: String get() = if (mousebutton == null) name else "$name ($mousebutton)"
}
