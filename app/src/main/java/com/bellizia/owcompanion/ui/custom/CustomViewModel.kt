package com.bellizia.owcompanion.ui.custom

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bellizia.owcompanion.data.DatasetRepository
import com.bellizia.owcompanion.sim.DamageOptimizer
import com.bellizia.owcompanion.sim.DamagePeak
import com.bellizia.owcompanion.sim.Modifiers
import com.bellizia.owcompanion.sim.WeaponModel
import com.bellizia.owcompanion.sim.WeaponSet
import com.bellizia.owcompanion.sim.WeaponSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The stats a person can move, and what each does to the spec. */
enum class TunableStat {
    Damage,
    FireRate,
    Ammo,
    Reload,
    Pellets,
}

/** Multipliers on the original values, one per stat. 1.0 means untouched. */
data class Tuning(val factors: Map<TunableStat, Float> = emptyMap()) {
    fun factor(stat: TunableStat): Float = factors[stat] ?: 1f

    fun with(stat: TunableStat, factor: Float) = Tuning(factors + (stat to factor))

    val isPristine: Boolean get() = factors.values.all { it == 1f }

    /**
     * Applies the tuning to a weapon. Everything derived from these values - the damage
     * ramp, the shot clock, reload amortised over the magazine - is recomputed by
     * [WeaponModel] from the spec, so editing the spec is all that is needed.
     */
    fun apply(spec: WeaponSpec): WeaponSpec {
        val damage = factor(TunableStat.Damage).toDouble()
        val fireRate = factor(TunableStat.FireRate).toDouble()
        val ammo = factor(TunableStat.Ammo).toDouble()
        val reload = factor(TunableStat.Reload).toDouble()
        val pellets = factor(TunableStat.Pellets).toDouble()

        val newFireRate = spec.fireRate?.times(fireRate)
        val newShotTime = spec.shotTime?.div(fireRate)
        val newAmmo = spec.ammo?.times(ammo)
        val newReload = spec.reloadTime?.times(reload)

        return spec.copy(
            damage = spec.damage.copy(
                dpshot = spec.damage.dpshot.map { it * damage },
                dpshotBall = spec.damage.dpshotBall?.times(damage),
                dps = spec.damage.dps?.times(damage),
            ),
            pellets = spec.pellets.map { (it * pellets).coerceAtLeast(1.0) },
            fireRate = newFireRate,
            shotTime = newShotTime,
            ammo = newAmmo,
            reloadTime = newReload,
            dpsPeriodBase = spec.dpsPeriodBase?.div(fireRate),
            dpsPeriodAdd = if (newAmmo != null && newReload != null && newAmmo > 0) {
                newReload / newAmmo
            } else {
                spec.dpsPeriodAdd?.times(reload)?.div(ammo)
            },
        )
    }
}

data class RankedHero(val name: String, val weapon: String, val dps: Double, val color: String)

data class CustomUiState(
    val loading: Boolean = true,
    val heroes: List<String> = emptyList(),
    val weaponsForHero: List<WeaponSpec> = emptyList(),
    val selectedHero: String? = null,
    val selectedWeapon: String? = null,
    val tuning: Tuning = Tuning(),
    val baseline: List<RankedHero> = emptyList(),
    /** Where the untouched weapon sits, 1-based; 0 when it is not ranked. */
    val originalRank: Int = 0,
    val originalDps: Double = 0.0,
    val tunedPeak: DamagePeak? = null,
    val tunedRank: Int = 0,
    val computing: Boolean = false,
)

/**
 * A what-if bench: take a real weapon, move its numbers, and see where that would put the
 * hero in the ranking.
 *
 * The whole thing rests on [WeaponSpec] being immutable data that [WeaponModel] derives
 * everything else from. A tuned weapon is not a special case anywhere in the engine - it is
 * just another spec.
 */
class CustomViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DatasetRepository(application)
    private val optimizer = DamageOptimizer()

    private var weaponSet: WeaponSet? = null
    private var job: Job? = null

    private val _state = MutableStateFlow(CustomUiState())
    val state: StateFlow<CustomUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val set = repository.weapons()
            weaponSet = set
            val baseline = withContext(Dispatchers.Default) { computeBaseline(set) }
            _state.update {
                it.copy(
                    loading = false,
                    heroes = set.heroes.map { hero -> hero.name }.sorted(),
                    baseline = baseline,
                )
            }
            selectHero(baseline.firstOrNull()?.name ?: set.heroes.first().name)
        }
    }

    fun selectHero(name: String) {
        val set = weaponSet ?: return
        // Quick Melee is the same on everyone, so it makes a dull thing to tune.
        val weapons = set.weapons.filter { it.hero == name && it.name != "Quick Melee" }
        _state.update {
            it.copy(
                selectedHero = name,
                weaponsForHero = weapons,
                selectedWeapon = weapons.firstOrNull()?.name,
                tuning = Tuning(),
            )
        }
        recompute()
    }

    fun selectWeapon(name: String) {
        _state.update { it.copy(selectedWeapon = name, tuning = Tuning()) }
        recompute()
    }

    fun setFactor(stat: TunableStat, factor: Float) {
        _state.update { it.copy(tuning = it.tuning.with(stat, factor)) }
        recompute()
    }

    fun reset() {
        _state.update { it.copy(tuning = Tuning()) }
        recompute()
    }

    private fun currentSpec(): WeaponSpec? {
        val state = _state.value
        return state.weaponsForHero.firstOrNull { it.name == state.selectedWeapon }
    }

    private fun recompute() {
        job?.cancel()
        job = viewModelScope.launch {
            delay(DEBOUNCE_MILLIS)
            val spec = currentSpec() ?: return@launch
            _state.update { it.copy(computing = true) }

            val result = withContext(Dispatchers.Default) {
                val original = optimizer.bestFor(WeaponModel(spec), MODIFIERS)
                val tuned = optimizer.bestFor(
                    WeaponModel(_state.value.tuning.apply(spec)),
                    MODIFIERS,
                )
                original to tuned
            }
            val (original, tuned) = result
            val baseline = _state.value.baseline

            _state.update {
                it.copy(
                    computing = false,
                    originalDps = original.dps,
                    originalRank = rankOf(baseline, original.dps, spec.hero),
                    tunedPeak = tuned,
                    tunedRank = rankOf(baseline, tuned.dps, spec.hero),
                )
            }
        }
    }

    /** Where a dps figure would land, with the hero's own entry taken out of the way. */
    private fun rankOf(baseline: List<RankedHero>, dps: Double, hero: String): Int =
        baseline.count { it.name != hero && it.dps > dps } + 1

    private suspend fun computeBaseline(set: WeaponSet): List<RankedHero> = coroutineScope {
        set.weapons
            .map { spec -> async { spec to optimizer.bestFor(WeaponModel(spec), MODIFIERS) } }
            .awaitAll()
            .groupBy { it.first.hero }
            .mapNotNull { (hero, entries) ->
                val best = entries.maxByOrNull { it.second.dps } ?: return@mapNotNull null
                RankedHero(
                    name = hero,
                    weapon = best.first.name,
                    dps = best.second.dps,
                    color = set.hero(hero)?.color ?: "#9aa4b2",
                )
            }
            .sortedByDescending { it.dps }
    }

    private companion object {
        /** No buffs: a what-if is clearer measured against the plain hero. */
        val MODIFIERS = Modifiers.NONE
        const val DEBOUNCE_MILLIS = 120L
    }
}
