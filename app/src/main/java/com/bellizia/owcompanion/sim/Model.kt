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
/**
 * An ultimate's headline damage.
 *
 * Ranked on its own because it is burst, not sustained fire: a figure for one cast, with
 * the wiki's full wording kept alongside so a reader can see what a single number leaves
 * out - a centre and an outer radius, a rate over a duration.
 */
@Serializable
data class UltimateSpec(
    val hero: String,
    val name: String,
    /** Damage one cast can deal, or null when the ultimate deals none. */
    val damage: Double? = null,
    /** Every damage figure the wiki lists, verbatim. */
    val detail: String? = null,
    val duration: Double? = null,
    val radius: Double? = null,
    val description: String = "",
    /** True when the figure is a channel total, not a burst - eight seconds on one target. */
    val sustained: Boolean = false,
    /** True when the wiki's figure is not a single-cast total and cannot honestly be one. */
    val unrankable: Boolean = false,
)

/**
 * A healing source.
 *
 * Healing needs no hitbox simulation: an ally being healed is not dodging, so the rate is
 * arithmetic rather than sampled.
 */
@Serializable
data class HealingSpec(
    val hero: String,
    val name: String,
    /** `weapon` or `ultimate`: the two are not comparable and are ranked apart. */
    val kind: String = "weapon",
    /** True when the rate is the wiki's own figure rather than one derived here. */
    val statedHps: Boolean = false,
    val healPerShot: Double? = null,
    val healPerSecond: Double? = null,
    val fireRate: Double? = null,
    val ammo: Double? = null,
    val reloadTime: Double? = null,
    val detail: String? = null,
) {
    /** Healing per second including reload, or null when there is not enough to say. */
    val healingPerSecond: Double?
        get() {
            healPerSecond?.let { return it }
            val perShot = healPerShot ?: return null
            val rate = fireRate ?: return null
            if (rate <= 0) return null
            val cycle = 1.0 / rate + reloadAmortised()
            return if (cycle > 0) perShot / cycle else null
        }

    private fun reloadAmortised(): Double {
        val reload = reloadTime ?: return 0.0
        val magazine = ammo ?: return 0.0
        return if (magazine > 0) reload / magazine else 0.0
    }
}

@Serializable
data class WeaponSet(
    val meta: DatasetMeta = DatasetMeta(),
    val heroes: List<Hero> = emptyList(),
    val weapons: List<WeaponSpec> = emptyList(),
    val ultimates: List<UltimateSpec> = emptyList(),
    val healing: List<HealingSpec> = emptyList(),
    val stadiumItems: List<StadiumItem> = emptyList(),
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
    /** File name under `assets/heroes/`, so a chart row can show the hero's face. */
    val portrait: String? = null,
    val health: Int? = null,
    val shields: Int? = null,
    val armor: Int? = null,
    /** Ability names, so a build can show which of them an item touches. */
    val abilities: List<String> = emptyList(),
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

    /**
     * Damage rises with how long the trigger was held, between a minimum and a maximum.
     * Symmetra's alt fire and Mizuki's glaive both work this way.
     */
    @SerialName("chargeScaled")
    ChargeScaled,
}

/** Which weapons respond to the charge control, and what to call it. */
val WeaponBehavior.isChargeScaled: Boolean
    get() = this == WeaponBehavior.ParticleCannon ||
        this == WeaponBehavior.PhotonProjector ||
        this == WeaponBehavior.ChargeScaled

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
    /** `[atMinimumCharge, atFullCharge]` for [WeaponBehavior.ChargeScaled]. */
    val chargeDamage: List<Double>? = null,
    /** Seconds of holding the trigger to reach full charge. */
    val chargeTime: Double? = null,
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
     * The perk that has to be picked for this weapon to exist at all, if any.
     *
     * Bastion's Assault turret fires explosive shells only once Lindholm Explosives is
     * taken, so the row is true of some Bastions and not others - which is worth saying on
     * the row rather than leaving the reader to infer it from a name in brackets.
     */
    val perk: String? = null,
    /** What the weapon is called without [perk]; null when there is no perk involved. */
    val baseWeapon: String? = null,
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
    /**
     * `[atMinimumCharge, atFullCharge]` seconds per shot, for a weapon that takes longer to
     * fire the more it is wound up.
     *
     * Symmetra's alt fire is the case that needed it: 0.256 s between uncharged orbs and
     * 1.25 s between full ones. Recording only the second made the chart show her weakest
     * orb fired at her slowest rate, which is neither of the two things she can do.
     */
    val chargeShotTime: List<Double>? = null,
    val tickRate: Double? = null,
    val ammoUsage: Double? = null,
    /**
     * `[atMinimumCharge, atFullCharge]` rounds spent per shot.
     *
     * Symmetra's alt fire drains 1 of its 100 ammo uncharged and 10 fully charged, so the
     * magazine is a hundred quick orbs or ten heavy ones. Storing only the ten made the
     * uncharged orb reload ten times too often.
     */
    val chargeAmmoUsage: List<Double>? = null,
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
    /**
     * Unique across the whole set. The hero has to be part of it: every hero carries a
     * weapon called "Quick Melee", so the name alone is not an identity.
     */
    val id: String get() = buildString {
        append(hero)
        append('|')
        append(name)
        if (mousebutton != null) {
            append(" (")
            append(mousebutton)
            append(')')
        }
    }
}
