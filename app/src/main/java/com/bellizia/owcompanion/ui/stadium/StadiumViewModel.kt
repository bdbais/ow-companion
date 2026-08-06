package com.bellizia.owcompanion.ui.stadium

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bellizia.owcompanion.data.BuildRepository
import com.bellizia.owcompanion.data.DatasetRepository
import com.bellizia.owcompanion.data.SavedBuild
import com.bellizia.owcompanion.sim.BoostedAbility
import com.bellizia.owcompanion.sim.StadiumItem
import com.bellizia.owcompanion.sim.StadiumOptimizer
import com.bellizia.owcompanion.sim.StadiumStats
import com.bellizia.owcompanion.sim.StadiumStatsCalculator
import com.bellizia.owcompanion.sim.WeaponSet
import com.bellizia.owcompanion.sim.WeaponSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class StadiumUiState(
    val loading: Boolean = true,
    val heroes: List<String> = emptyList(),
    val selectedHero: String? = null,
    val heroColor: String = "#9aa4b2",
    val weapon: WeaponSpec? = null,
    val budget: Float = 10_000f,
    /** Every item in the Armory, filtered to this hero plus the general ones. */
    val items: List<StadiumItem> = emptyList(),
    val categories: List<String> = emptyList(),
    val category: String = "",
    /** Item names currently in the build. */
    val selected: Set<String> = emptySet(),
    val stats: StadiumStats = StadiumStats(0, 0, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
    val boosted: List<BoostedAbility> = emptyList(),
    val dps: Double? = null,
    val baseDps: Double? = null,
    val computing: Boolean = false,
    val savedBuilds: List<SavedBuild> = emptyList(),
    val naming: Boolean = false,
    val nameDraft: String = "",
) {
    val visibleItems: List<StadiumItem>
        get() = items.filter { category.isBlank() || it.category == category }

    val chosenItems: List<StadiumItem> get() = items.filter { it.name in selected }

    val spent: Double get() = chosenItems.sumOf { it.cost ?: 0.0 }

    fun canAfford(item: StadiumItem): Boolean =
        item.name in selected || spent + (item.cost ?: 0.0) <= budget
}

/**
 * The Armory, with the items chosen by hand.
 *
 * The optimiser is still here but is a suggestion rather than the product: it fills the
 * selection, which can then be argued with. It only reasons about damage, and a build is
 * rarely only about damage.
 */
class StadiumViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DatasetRepository(application)
    private val builds = BuildRepository(application)
    private val optimizer = StadiumOptimizer()

    private var weaponSet: WeaponSet? = null
    private var job: Job? = null

    private val _state = MutableStateFlow(StadiumUiState())
    val state: StateFlow<StadiumUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val set = repository.weapons()
            weaponSet = set
            _state.update {
                it.copy(
                    loading = false,
                    heroes = set.heroes.map { hero -> hero.name }.sorted(),
                    savedBuilds = builds.all(),
                )
            }
            selectHero(set.heroes.first().name)
        }
    }

    fun selectHero(name: String) {
        val set = weaponSet ?: return
        val weapon = set.weapons
            .filter { it.hero == name && it.name != "Quick Melee" }
            .maxByOrNull { it.damage.dpshot.firstOrNull() ?: 0.0 }
        val items = set.stadiumItems
            .filter { it.hero == "All heroes" || it.hero == name }
            .sortedWith(compareBy({ it.cost ?: 0.0 }, { it.name }))
        val categories = items.map { it.category }.filter { it.isNotBlank() }.distinct()

        _state.update {
            it.copy(
                selectedHero = name,
                heroColor = set.hero(name)?.color ?: "#9aa4b2",
                weapon = weapon,
                items = items,
                categories = categories,
                category = categories.firstOrNull().orEmpty(),
                selected = emptySet(),
            )
        }
        recompute()
    }

    fun setCategory(category: String) = _state.update { it.copy(category = category) }

    fun setBudget(budget: Float) {
        _state.update { it.copy(budget = budget) }
    }

    fun toggleSelected(name: String) {
        _state.update { current ->
            val item = current.items.firstOrNull { it.name == name }
            when {
                name in current.selected -> current.copy(selected = current.selected - name)
                item != null && !current.canAfford(item) -> current
                else -> current.copy(selected = current.selected + name)
            }
        }
        recompute()
    }

    fun clearSelection() {
        _state.update { it.copy(selected = emptySet()) }
        recompute()
    }

    /** Fills the selection with what the optimiser would buy on this budget. */
    fun suggest() {
        val set = weaponSet ?: return
        val weapon = _state.value.weapon ?: return
        _state.update { it.copy(computing = true) }
        viewModelScope.launch {
            val build = withContext(Dispatchers.Default) {
                optimizer.bestBuild(weapon, set.stadiumItems, _state.value.budget.toDouble())
            }
            _state.update {
                it.copy(selected = build.steps.map { step -> step.item.name }.toSet())
            }
            recompute()
        }
    }

    fun loadBuild(build: SavedBuild) {
        if (build.hero != _state.value.selectedHero) selectHero(build.hero)
        _state.update { it.copy(selected = build.items.toSet()) }
        recompute()
    }

    fun startNaming() = _state.update {
        it.copy(naming = true, nameDraft = defaultName(it))
    }

    fun setNameDraft(name: String) = _state.update { it.copy(nameDraft = name) }

    fun cancelNaming() = _state.update { it.copy(naming = false) }

    private fun defaultName(state: StadiumUiState): String {
        val count = state.savedBuilds.count { it.hero == state.selectedHero } + 1
        return "${state.selectedHero.orEmpty()} build $count"
    }

    fun confirmSave() {
        val state = _state.value
        val hero = state.selectedHero ?: return
        viewModelScope.launch {
            val saved = builds.save(
                SavedBuild(
                    id = BuildRepository.newId(),
                    name = state.nameDraft.ifBlank { defaultName(state) },
                    hero = hero,
                    weapon = state.weapon?.name.orEmpty(),
                    items = state.chosenItems.map { it.name },
                    savedAt = System.currentTimeMillis(),
                ),
            )
            _state.update { it.copy(savedBuilds = saved, naming = false) }
        }
    }

    fun deleteBuild(id: String) = viewModelScope.launch {
        _state.update { it.copy(savedBuilds = builds.delete(id)) }
    }

    fun cloneBuild(id: String) = viewModelScope.launch {
        _state.update { it.copy(savedBuilds = builds.clone(id)) }
    }

    /**
     * Stats update immediately - they are arithmetic. The damage figure is a full
     * simulation sweep, so it is debounced and computed off the main thread.
     */
    private fun recompute() {
        val set = weaponSet ?: return
        val current = _state.value
        val hero = set.hero(current.selectedHero.orEmpty())
        val chosen = current.chosenItems

        _state.update {
            it.copy(
                stats = StadiumStatsCalculator.apply(hero, chosen),
                boosted = StadiumStatsCalculator.boostedAbilities(hero, chosen),
            )
        }

        val weapon = current.weapon ?: return
        job?.cancel()
        job = viewModelScope.launch {
            delay(DEBOUNCE_MILLIS)
            _state.update { it.copy(computing = true) }
            val (base, boosted) = withContext(Dispatchers.Default) {
                optimizer.dpsWith(weapon, emptyList()) to optimizer.dpsWith(weapon, chosen)
            }
            _state.update { it.copy(computing = false, baseDps = base, dps = boosted) }
        }
    }

    private companion object {
        const val DEBOUNCE_MILLIS = 120L
    }
}
