package com.bellizia.owcompanion.ui.chart

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bellizia.owcompanion.data.DatasetRepository
import com.bellizia.owcompanion.sim.Crosshair
import com.bellizia.owcompanion.sim.Modifiers
import com.bellizia.owcompanion.sim.Simulator
import com.bellizia.owcompanion.sim.WeaponModel
import com.bellizia.owcompanion.sim.WeaponSet
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

class ChartViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DatasetRepository(application)
    private val simulator = Simulator()

    private var weaponSet: WeaponSet? = null
    private var recomputeJob: Job? = null

    // Rate-of-fire buffs are baked into the weapon's timing, so the models have to be
    // rebuilt when one is toggled. Cheap enough to redo, worth caching between recomputes.
    private var modelCache: Pair<Double, Map<String, WeaponModel>>? = null

    private fun modelsFor(speedFactor: Double): Map<String, WeaponModel> {
        modelCache?.let { (cachedFactor, cached) -> if (cachedFactor == speedFactor) return cached }
        val set = weaponSet ?: return emptyMap()
        val built = set.weapons.associate { it.id to WeaponModel(it, speedFactor) }
        modelCache = speedFactor to built
        return built
    }

    private val _state = MutableStateFlow(ChartUiState())
    val state: StateFlow<ChartUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            weaponSet = repository.weapons()
            recompute(immediate = true)
        }
    }

    fun setDistance(distance: Float) = edit { it.copy(distance = distance) }

    fun setAim(x: Float, z: Float) = edit { it.copy(aimX = x, aimZ = z) }

    fun setModifiers(modifiers: Modifiers) = edit { it.copy(modifiers = modifiers) }

    /**
     * Reordering never changes a number, so it must not pay for a simulation.
     *
     * A full pass takes over two seconds on 129 weapons, and this used to run one every time
     * the sort was changed - to display exactly the values already on screen, in a different
     * order.
     */
    fun setSortOrder(order: SortOrder) {
        _state.update { it.copy(sortOrder = order, rows = it.rows.sortedWith(comparatorFor(order))) }
    }

    fun setEnergy(energy: Float) = edit { it.copy(energy = energy.coerceIn(0f, 100f)) }

    fun setZoom(zoom: Float) {
        // Zoom is a pure drawing concern, so it never triggers a recompute.
        _state.update { it.copy(zoom = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)) }
    }

    fun setRoles(roles: Set<HeroRole>) = edit { it.copy(roles = roles) }

    fun setCategories(categories: Set<WeaponCategory>) = edit { it.copy(categories = categories) }

    fun setFireModes(modes: Set<FireMode>) = edit { it.copy(fireModes = modes) }


    private fun edit(transform: (ChartUiState) -> ChartUiState) {
        _state.update(transform)
        recompute(immediate = false)
    }

    /**
     * Recomputes every visible weapon. Dragging the distance slider fires this continuously,
     * so a short debounce keeps one simulation pass per gesture rather than one per pixel.
     */
    private fun recompute(immediate: Boolean) {
        recomputeJob?.cancel()
        recomputeJob = viewModelScope.launch {
            if (!immediate) delay(DEBOUNCE_MILLIS)
            val current = _state.value
            val started = System.currentTimeMillis()
            val rows = simulate(current)
            _state.update {
                it.copy(
                    loading = false,
                    rows = rows,
                    lastComputeMillis = System.currentTimeMillis() - started,
                )
            }
        }
    }

    /** Weapons are independent, so they are simulated across all available cores. */
    private suspend fun simulate(state: ChartUiState): List<ChartRow> = coroutineScope {
        val set = weaponSet ?: return@coroutineScope emptyList()
        val crosshair = Crosshair(
            x = state.aimX.toDouble(),
            z = state.aimZ.toDouble(),
            distance = state.distance.toDouble(),
        )
        val models = modelsFor(state.modifiers.attackSpeedFactor)
        val cached = cacheFor(SimInputs(state))

        val visible = set.weapons.filter { spec ->
            val role = set.hero(spec.hero)?.role?.let(HeroRole::of)
            val roleVisible = role == null || role in state.roles
            roleVisible &&
                WeaponCategory.of(spec).any { it in state.categories } &&
                FireMode.of(spec) in state.fireModes
        }

        visible
            .filter { it.id !in cached }
            .map { spec ->
                async(Dispatchers.Default) {
                    val model = models[spec.id] ?: return@async null
                    ChartRow(
                        spec = spec,
                        hero = set.hero(spec.hero),
                        train = simulator.simulateMean(
                            model = model,
                            crosshair = crosshair,
                            modifiers = state.modifiers,
                            maxTrials = MAX_TRIALS,
                            charge = state.energy.toDouble() / 100.0,
                        ),
                    )
                }
            }
            .awaitAll()
            .filterNotNull()
            .forEach { cached[it.spec.id] = it }

        visible.mapNotNull { cached[it.id] }.sortedWith(comparatorFor(state.sortOrder))
    }

    /**
     * Everything a simulated row actually depends on.
     *
     * Filters and sort order are deliberately absent: narrowing the role filter hides rows,
     * it does not change what the remaining ones say. Widening it back used to re-simulate
     * every weapon on screen to arrive at numbers that had not moved.
     */
    private data class SimInputs(
        val distance: Float,
        val aimX: Float,
        val aimZ: Float,
        val modifiers: Modifiers,
        val energy: Float,
    ) {
        constructor(state: ChartUiState) : this(
            distance = state.distance,
            aimX = state.aimX,
            aimZ = state.aimZ,
            modifiers = state.modifiers,
            energy = state.energy,
        )
    }

    private var rowCache: Pair<SimInputs, MutableMap<String, ChartRow>>? = null

    /**
     * Rows already simulated for these inputs, ready to be added to.
     *
     * Only one set is kept. Moving the distance slider invalidates everything anyway, and
     * holding older sets would mean deciding when to let 129 results go.
     */
    private fun cacheFor(inputs: SimInputs): MutableMap<String, ChartRow> {
        rowCache?.let { (cachedInputs, rows) -> if (cachedInputs == inputs) return rows }
        return mutableMapOf<String, ChartRow>().also { rowCache = inputs to it }
    }

    private fun comparatorFor(order: SortOrder): Comparator<ChartRow> = when (order) {
        SortOrder.Dps -> compareByDescending { it.train.dps }
        SortOrder.DpsWithoutReload -> compareByDescending { it.train.dpsWithoutReload }
        SortOrder.Accuracy -> compareByDescending { it.train.accuracy }
        // Weapons that cannot crit sort last rather than tying at zero with those that miss.
        SortOrder.CritAccuracy -> compareByDescending {
            if (it.spec.critFactor == 1.0) -1.0 else it.train.critAccuracy
        }
        SortOrder.TimeToKill -> compareBy { it.train.timeToKill }
        SortOrder.Hero -> compareBy<ChartRow> { it.spec.hero }.thenBy { it.spec.name }
    }

    private companion object {
        /** Ceiling on averaging passes; the simulator scales down from here per weapon. */
        const val MAX_TRIALS = 32
        const val DEBOUNCE_MILLIS = 60L
        const val MIN_ZOOM = 0.35f
        const val MAX_ZOOM = 4f
    }
}
