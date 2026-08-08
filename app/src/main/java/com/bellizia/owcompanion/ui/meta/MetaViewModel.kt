package com.bellizia.owcompanion.ui.meta

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bellizia.owcompanion.R
import com.bellizia.owcompanion.data.MetaRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The three questions the page can answer, in the order players ask them. */
enum class MetaSort(val labelRes: Int) {
    Ban(R.string.meta_sort_ban),
    Pick(R.string.meta_sort_pick),
    Win(R.string.meta_sort_win);

    fun valueOf(hero: MetaRepository.HeroRate): Double = when (this) {
        Ban -> hero.ban
        Pick -> hero.pick
        Win -> hero.win
    }
}

enum class MetaQueue(val value: String, val labelRes: Int) {
    Competitive("1", R.string.meta_queue_comp),
    QuickPlay("0", R.string.meta_queue_qp),
}

enum class MetaTier(val value: String, val labelRes: Int) {
    All("All", R.string.meta_tier_all),
    Bronze("Bronze", R.string.meta_tier_bronze),
    Silver("Silver", R.string.meta_tier_silver),
    Gold("Gold", R.string.meta_tier_gold),
    Platinum("Platinum", R.string.meta_tier_platinum),
    Diamond("Diamond", R.string.meta_tier_diamond),
    Master("Master", R.string.meta_tier_master),
    Grandmaster("Grandmaster", R.string.meta_tier_grandmaster),
}

enum class MetaRegion(val value: String, val labelRes: Int) {
    // Not one of Blizzard's: the three below are fetched together and averaged.
    World(MetaRepository.WORLD, R.string.meta_region_world),
    Europe("Europe", R.string.meta_region_europe),
    Americas("Americas", R.string.meta_region_americas),
    Asia("Asia", R.string.meta_region_asia),
}

enum class MetaInput(val value: String, val labelRes: Int) {
    Pc("PC", R.string.meta_input_pc),
    Console("Console", R.string.meta_input_console),
}

/** Only the three top-level roles; Blizzard's fourteen subroles would swamp the chip row. */
enum class MetaRole(val value: String, val labelRes: Int) {
    All("All", R.string.meta_role_all),
    Tank("tank", R.string.role_tank),
    Damage("damage", R.string.role_damage),
    Support("support", R.string.role_support),
}

data class MetaUiState(
    val filters: MetaRepository.Filters = MetaRepository.Filters(),
    val sort: MetaSort = MetaSort.Ban,
    val heroes: List<MetaRepository.HeroRate> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
) {
    val sorted: List<MetaRepository.HeroRate>
        get() = heroes.sortedByDescending { sort.valueOf(it) }

    /** The labels the folded filter row shows, so collapsing does not hide the selection. */
    val summaryLabels: List<Int>
        get() = listOfNotNull(
            MetaQueue.entries.firstOrNull { it.value == filters.queue }?.labelRes,
            MetaTier.entries.firstOrNull { it.value == filters.tier }?.labelRes
                ?.takeIf { filters.queue == MetaQueue.Competitive.value },
            MetaRegion.entries.firstOrNull { it.value == filters.region }?.labelRes,
            MetaInput.entries.firstOrNull { it.value == filters.input }?.labelRes,
            MetaRole.entries.firstOrNull { it.value == filters.role }?.labelRes,
        )

    /** The chosen map, or null while every map is being counted together. */
    val map: MetaMap?
        get() = MetaMaps.firstOrNull { it.slug == filters.map }
}

class MetaViewModel : ViewModel() {

    private val repository = MetaRepository()

    private val _state = MutableStateFlow(MetaUiState())
    val state: StateFlow<MetaUiState> = _state.asStateFlow()

    private var inFlight: Job? = null

    init {
        reload()
    }

    fun reload() {
        // Every chip cancels the request the previous chip started, so a burst of taps costs
        // one fetch rather than five, and the last tap is the one that wins.
        inFlight?.cancel()
        _state.update { it.copy(loading = true, error = null) }
        inFlight = viewModelScope.launch {
            when (val result = repository.rates(_state.value.filters)) {
                is MetaRepository.Result.Loaded ->
                    _state.update { it.copy(heroes = result.heroes, loading = false) }
                is MetaRepository.Result.Failed ->
                    _state.update { it.copy(loading = false, error = result.cause) }
            }
        }
    }

    fun sortBy(sort: MetaSort) = _state.update { it.copy(sort = sort) }

    fun queue(queue: MetaQueue) = refilter { it.copy(queue = queue.value) }

    fun tier(tier: MetaTier) = refilter { it.copy(tier = tier.value) }

    fun region(region: MetaRegion) = refilter { it.copy(region = region.value) }

    fun input(input: MetaInput) = refilter { it.copy(input = input.value) }

    fun role(role: MetaRole) = refilter { it.copy(role = role.value) }

    /** `null` is every map at once, which is what the rates page calls `all-maps`. */
    fun map(map: MetaMap?) = refilter { it.copy(map = map?.slug ?: "all-maps") }

    private fun refilter(change: (MetaRepository.Filters) -> MetaRepository.Filters) {
        val next = change(_state.value.filters)
        if (next == _state.value.filters) return
        _state.update { it.copy(filters = next) }
        reload()
    }
}
