package com.bellizia.owcompanion.ui.leaderboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bellizia.owcompanion.data.DatasetRepository
import com.bellizia.owcompanion.data.WikiRepository
import com.bellizia.owcompanion.ui.wiki.Combo
import com.bellizia.owcompanion.sim.DamageOptimizer
import com.bellizia.owcompanion.sim.DamagePeak
import com.bellizia.owcompanion.sim.Hero
import com.bellizia.owcompanion.sim.Modifiers
import com.bellizia.owcompanion.sim.WeaponModel
import com.bellizia.owcompanion.sim.WeaponSet
import com.bellizia.owcompanion.ui.chart.HeroRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One entry in the ranking: a weapon, its peak, and the hero it belongs to. */
data class LeaderboardEntry(
    val rank: Int,
    val peak: DamagePeak,
    val hero: Hero?,
)

/** The buffs allowed when hunting for the peak. All of them raise damage. */
data class BuffSelection(
    val damageBoost: Boolean = true,
    val discord: Boolean = true,
    val nanoboost: Boolean = true,
    val supercharger: Boolean = true,
    val amplificationMatrix: Boolean = true,
    val kitsuneRush: Boolean = true,
) {
    fun toModifiers() = Modifiers(
        damageBoost = damageBoost,
        discord = discord,
        nanoboostOffence = nanoboost,
        supercharger = supercharger,
        amplificationMatrix = amplificationMatrix,
        kitsuneRush = kitsuneRush,
    )
}

/** Three different questions, three different metrics. */
enum class RankingMode(@androidx.annotation.StringRes val labelRes: Int) {
    Weapons(com.bellizia.owcompanion.R.string.rank_weapons),
    Ultimates(com.bellizia.owcompanion.R.string.rank_ultimates),
    Healing(com.bellizia.owcompanion.R.string.rank_healing),
    Combos(com.bellizia.owcompanion.R.string.rank_combos),
}

/** A hero's whole opening, totalled. */
data class ComboEntry(
    val rank: Int,
    val hero: Hero?,
    val heroName: String,
    val total: Double,
    val seconds: Double,
    val steps: List<com.bellizia.owcompanion.ui.wiki.ComboStep>,
)

data class UltimateEntry(
    val rank: Int,
    val ultimate: com.bellizia.owcompanion.sim.UltimateSpec,
    val hero: Hero?,
)

data class HealingEntry(
    val rank: Int,
    val source: com.bellizia.owcompanion.sim.HealingSpec,
    val healingPerSecond: Double,
    val hero: Hero?,
)

data class LeaderboardUiState(
    val loading: Boolean = true,
    val mode: RankingMode = RankingMode.Weapons,
    val entries: List<LeaderboardEntry> = emptyList(),
    val ultimates: List<UltimateEntry> = emptyList(),
    val healing: List<HealingEntry> = emptyList(),
    private val allCombos: List<ComboEntry> = emptyList(),
    /** Ultimates that deal no damage, and so are left out rather than ranked at zero. */
    val ultimatesWithoutDamage: Int = 0,
    val buffs: BuffSelection = BuffSelection(),
    val roles: Set<HeroRole> = HeroRole.entries.toSet(),
    val computeMillis: Long = 0,
) {
    /** Ranked combos, narrowed to the chosen roles like every other list here. */
    val combos: List<ComboEntry>
        get() = allCombos.filter { entry ->
            entry.hero?.role?.let(HeroRole::of)?.let { it in roles } ?: true
        }

    /** Applies the role filter to whichever ranking is on screen. */
    fun <T> visible(items: List<T>, heroOf: (T) -> Hero?): List<T> = items.filter {
        val role = heroOf(it)?.role?.let(HeroRole::of)
        role == null || role in roles
    }
}

class LeaderboardViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DatasetRepository(application)
    private val optimizer = DamageOptimizer()

    private var weaponSet: WeaponSet? = null
    private var job: Job? = null

    // Rebuilt whenever a rate-of-fire buff changes, since those live in the weapon's timing.
    private var modelCache: Pair<Double, Map<String, WeaponModel>>? = null

    private fun modelsFor(speedFactor: Double): Map<String, WeaponModel> {
        modelCache?.let { (cachedFactor, cached) -> if (cachedFactor == speedFactor) return cached }
        val set = weaponSet ?: return emptyMap()
        val built = set.weapons.associate { it.id to WeaponModel(it, speedFactor) }
        modelCache = speedFactor to built
        return built
    }

    private val _state = MutableStateFlow(LeaderboardUiState())
    val state: StateFlow<LeaderboardUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val set = repository.weapons()
            weaponSet = set
            _state.update { current ->
                current.copy(
                    ultimates = set.ultimates
                        // An ultimate whose wiki figure is not a single-cast total is left
                        // out rather than ranked on a number that means something else.
                        .filter { (it.damage ?: 0.0) > 0 && !it.unrankable }
                        .sortedByDescending { it.damage }
                        .mapIndexed { index, ultimate ->
                            UltimateEntry(index + 1, ultimate, set.hero(ultimate.hero))
                        },
                    ultimatesWithoutDamage = set.ultimates.count {
                        (it.damage ?: 0.0) <= 0 || it.unrankable
                    },
                    healing = set.healing
                        // Weapons only: an ultimate that heals is not competing with a
                        // primary fire, and one list would say it was.
                        .filter { it.kind == "weapon" }
                        .mapNotNull { source ->
                            source.healingPerSecond?.let { source to it }
                        }
                        .sortedByDescending { it.second }
                        .mapIndexed { index, (source, hps) ->
                            HealingEntry(index + 1, source, hps, set.hero(source.hero))
                        },
                )
            }
            // The combo needs the wiki's abilities as well as the chart's weapons, and it
            // is the only thing on this screen that does, so it is loaded here rather than
            // making every ranking wait for a file twenty times the size.
            val heroes = WikiRepository(getApplication()).wiki().heroes
            _state.update { current ->
                current.copy(
                    allCombos = heroes
                        .map { hero -> hero to Combo.stepsFor(hero, set.weapons) }
                        .filter { (_, steps) -> steps.size > 1 }
                        .map { (hero, steps) ->
                            Triple(hero, steps, steps.sumOf { it.damage })
                        }
                        .sortedByDescending { it.third }
                        .mapIndexed { index, (hero, steps, total) ->
                            ComboEntry(
                                rank = index + 1,
                                hero = set.hero(hero.name),
                                heroName = hero.name,
                                total = total,
                                seconds = steps.sumOf { it.seconds },
                                steps = steps,
                            )
                        },
                )
            }
            recompute()
        }
    }

    fun setMode(mode: RankingMode) = _state.update { it.copy(mode = mode) }

    fun setRoles(roles: Set<HeroRole>) = _state.update { it.copy(roles = roles) }

    fun setBuffs(buffs: BuffSelection) {
        _state.update { it.copy(buffs = buffs, loading = true) }
        recompute()
    }

    private fun recompute() {
        job?.cancel()
        job = viewModelScope.launch {
            val buffs = _state.value.buffs
            val started = System.currentTimeMillis()
            val entries = search(buffs)
            _state.update {
                it.copy(
                    loading = false,
                    entries = entries,
                    computeMillis = System.currentTimeMillis() - started,
                )
            }
        }
    }

    private suspend fun search(buffs: BuffSelection): List<LeaderboardEntry> = coroutineScope {
        val set = weaponSet ?: return@coroutineScope emptyList()
        val modifiers = buffs.toModifiers()
        val models = modelsFor(modifiers.attackSpeedFactor)

        set.weapons
            .mapNotNull { spec -> models[spec.id] }
            .map { model -> async(Dispatchers.Default) { optimizer.bestFor(model, modifiers) } }
            .awaitAll()
            // One entry per hero, not per weapon: a hero's ranking is what their best gun
            // can do, and listing Roadhog twice for two barrels of the same shotgun buries
            // the heroes further down.
            .groupBy { it.spec.hero }
            .mapNotNull { (_, peaks) -> peaks.maxByOrNull { it.dps } }
            .sortedByDescending { it.dps }
            .mapIndexed { index, peak ->
                LeaderboardEntry(
                    rank = index + 1,
                    peak = peak,
                    hero = set.hero(peak.spec.hero),
                )
            }
    }
}
