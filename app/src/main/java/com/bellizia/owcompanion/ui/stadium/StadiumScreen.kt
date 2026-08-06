@file:OptIn(ExperimentalLayoutApi::class)

package com.bellizia.owcompanion.ui.stadium

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bellizia.owcompanion.R
import com.bellizia.owcompanion.data.BuildRepository
import com.bellizia.owcompanion.data.DatasetRepository
import com.bellizia.owcompanion.data.SavedBuild
import com.bellizia.owcompanion.sim.StadiumBuild
import com.bellizia.owcompanion.sim.StadiumItem
import com.bellizia.owcompanion.sim.StadiumOptimizer
import com.bellizia.owcompanion.sim.WeaponSet
import com.bellizia.owcompanion.sim.WeaponSpec
import com.bellizia.owcompanion.ui.chart.parseHeroColor
import com.bellizia.owcompanion.ui.theme.StatNumber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

data class StadiumUiState(
    val loading: Boolean = true,
    val heroes: List<String> = emptyList(),
    val selectedHero: String? = null,
    val weapon: WeaponSpec? = null,
    val budget: Float = 10_000f,
    val build: StadiumBuild? = null,
    val computing: Boolean = false,
    val itemsConsidered: Int = 0,
    val itemsTotal: Int = 0,
    val heroColor: String = "#9aa4b2",
    val savedBuilds: List<SavedBuild> = emptyList(),
    /** The saved build being viewed and edited, if any. */
    val editing: SavedBuild? = null,
    val editingDps: Double = 0.0,
    val naming: Boolean = false,
    val nameDraft: String = "",
    /** Items a build may contain: those the damage model can act on. */
    val editableItems: List<StadiumItem> = emptyList(),
)

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
                    itemsTotal = set.stadiumItems.size,
                    itemsConsidered = set.stadiumItems.count { item -> item.affectsDamage },
                    editableItems = set.stadiumItems.filter { item -> item.affectsDamage },
                )
            }
            _state.update { it.copy(savedBuilds = builds.all()) }
            selectHero(set.heroes.first().name)
        }
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

    /** Saves whatever is on screen - the proposal, or the build being edited. */
    fun confirmSave() {
        val state = _state.value
        val hero = state.selectedHero ?: return
        val weapon = state.weapon ?: return
        val items = state.editing?.items ?: state.build?.steps?.map { it.item.name }.orEmpty()
        viewModelScope.launch {
            val saved = builds.save(
                SavedBuild(
                    id = state.editing?.id ?: BuildRepository.newId(),
                    name = state.nameDraft.ifBlank { defaultName(state) },
                    hero = hero,
                    weapon = weapon.name,
                    items = items,
                    savedAt = System.currentTimeMillis(),
                ),
            )
            _state.update { it.copy(savedBuilds = saved, naming = false) }
        }
    }

    fun openBuild(build: SavedBuild?) {
        _state.update { it.copy(editing = build) }
        if (build != null && build.hero != _state.value.selectedHero) {
            selectHero(build.hero)
        }
        recomputeEditing()
    }

    fun deleteBuild(id: String) = viewModelScope.launch {
        val remaining = builds.delete(id)
        _state.update {
            it.copy(savedBuilds = remaining, editing = it.editing?.takeIf { e -> e.id != id })
        }
    }

    fun cloneBuild(id: String) = viewModelScope.launch {
        _state.update { it.copy(savedBuilds = builds.clone(id)) }
    }

    fun renameBuild(id: String, name: String) = viewModelScope.launch {
        _state.update { it.copy(savedBuilds = builds.rename(id, name)) }
    }

    /** Adds or removes one item from the build being edited. */
    fun toggleItem(name: String) {
        val editing = _state.value.editing ?: return
        val items = if (name in editing.items) editing.items - name else editing.items + name
        val updated = editing.copy(items = items)
        _state.update { it.copy(editing = updated) }
        viewModelScope.launch {
            val saved = builds.save(updated)
            _state.update { it.copy(savedBuilds = saved) }
            recomputeEditing()
        }
    }

    private fun recomputeEditing() {
        val set = weaponSet ?: return
        val editing = _state.value.editing
        val weapon = _state.value.weapon
        if (editing == null || weapon == null) {
            _state.update { it.copy(editingDps = 0.0) }
            return
        }
        viewModelScope.launch {
            val items = set.stadiumItems.filter { it.name in editing.items }
            val dps = withContext(Dispatchers.Default) { optimizer.dpsWith(weapon, items) }
            _state.update { it.copy(editingDps = dps) }
        }
    }

    fun selectHero(name: String) {
        val set = weaponSet ?: return
        val weapon = set.weapons
            .filter { it.hero == name && it.name != "Quick Melee" }
            .maxByOrNull { it.damage.dpshot.firstOrNull() ?: 0.0 }
        _state.update {
            it.copy(
                selectedHero = name,
                weapon = weapon,
                heroColor = set.hero(name)?.color ?: "#9aa4b2",
            )
        }
        recompute()
    }

    fun setBudget(budget: Float) {
        _state.update { it.copy(budget = budget) }
        recompute()
    }

    private fun recompute() {
        job?.cancel()
        job = viewModelScope.launch {
            delay(150)
            val set = weaponSet ?: return@launch
            val weapon = _state.value.weapon ?: return@launch
            _state.update { it.copy(computing = true) }
            val build = withContext(Dispatchers.Default) {
                optimizer.bestBuild(
                    spec = weapon,
                    items = set.stadiumItems,
                    budget = _state.value.budget.toDouble(),
                )
            }
            _state.update { it.copy(computing = false, build = build) }
        }
    }
}

/**
 * Proposes a Stadium build instead of asking you to assemble one.
 *
 * Every item is shown with what it was actually worth, because that is the part worth
 * knowing: an item that adds 180 damage per second and one that adds 12 cost the same cash
 * and take the same slot.
 */
@Composable
fun StadiumScreen(
    modifier: Modifier = Modifier,
    viewModel: StadiumViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.loading) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val color = parseHeroColor(state.heroColor, MaterialTheme.colorScheme.primary)
    val build = state.build

    LazyColumn(modifier = modifier.fillMaxSize()) {
        item {
            Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(state.heroes) { hero ->
                            FilterChip(
                                selected = hero == state.selectedHero,
                                onClick = { viewModel.selectHero(hero) },
                                label = {
                                    Text(hero, style = MaterialTheme.typography.labelSmall)
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(
                                R.string.stadium_budget,
                                state.budget.roundToInt(),
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.width(130.dp),
                        )
                        Slider(
                            value = state.budget,
                            onValueChange = viewModel::setBudget,
                            valueRange = 1_000f..30_000f,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            HorizontalDivider()
        }

        item {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = build?.let { "%.0f".format(it.finalDps) } ?: "-",
                        style = StatNumber,
                        color = color,
                    )
                    Text(
                        text = " dps",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    if (state.computing) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.padding(start = 10.dp, bottom = 6.dp).width(14.dp),
                        )
                    }
                }
                if (build != null && build.baseDps > 0) {
                    val gain = (build.finalDps / build.baseDps - 1) * 100
                    Text(
                        text = stringResource(
                            R.string.stadium_gain,
                            "%.0f".format(build.baseDps),
                            "+%.0f".format(gain),
                            build.cost.roundToInt(),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                state.weapon?.let {
                    Text(
                        text = it.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        items(build?.steps.orEmpty()) { step ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = step.item.rarity.take(1),
                        style = MaterialTheme.typography.labelMedium,
                        color = color,
                    )
                }
                Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(
                        text = step.item.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = step.item.buffs.joinToString(" · ") { buff ->
                            "${buff.stat} ${buff.value?.let { "%.0f".format(it) } ?: ""}" +
                                if (buff.percent) "%" else ""
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "+%.0f".format(step.gain),
                        style = MaterialTheme.typography.titleMedium,
                        color = color,
                    )
                    Text(
                        text = "${(step.item.cost ?: 0.0).roundToInt()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }


        // --- saved builds ---------------------------------------------------------
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.build_saved),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = viewModel::startNaming) {
                    Text(stringResource(R.string.build_save))
                }
            }
        }

        if (state.naming) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = state.nameDraft,
                        onValueChange = viewModel::setNameDraft,
                        singleLine = true,
                        label = { Text(stringResource(R.string.build_name)) },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = viewModel::confirmSave) {
                        Text(stringResource(R.string.build_confirm))
                    }
                    TextButton(onClick = viewModel::cancelNaming) {
                        Text(stringResource(R.string.build_cancel))
                    }
                }
            }
        }

        val heroBuilds = state.savedBuilds.filter { it.hero == state.selectedHero }
        if (heroBuilds.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.build_none),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }

        items(heroBuilds, key = { it.id }) { saved ->
            val open = state.editing?.id == saved.id
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 3.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (open) {
                            color.copy(alpha = 0.16f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        },
                    )
                    .padding(10.dp),
            ) {
                // Only the header opens and closes the card. With the whole card
                // clickable, a tap that missed an item chip closed it instead.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        viewModel.openBuild(if (open) null else saved)
                    },
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(saved.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = stringResource(
                                R.string.build_items,
                                saved.items.size,
                                if (open) "%.0f".format(state.editingDps) else "-",
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { viewModel.cloneBuild(saved.id) }) {
                        Text(
                            text = stringResource(R.string.build_clone),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    TextButton(onClick = { viewModel.deleteBuild(saved.id) }) {
                        Text(
                            text = stringResource(R.string.build_delete),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }

                if (open) {
                    OutlinedTextField(
                        value = saved.name,
                        onValueChange = { viewModel.renameBuild(saved.id, it) },
                        singleLine = true,
                        label = { Text(stringResource(R.string.build_rename)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                    )
                    Text(
                        text = stringResource(R.string.build_edit_items),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.editableItems.forEach { item ->
                            FilterChip(
                                selected = item.name in saved.items,
                                onClick = { viewModel.toggleItem(item.name) },
                                label = {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                            )
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = stringResource(
                    R.string.stadium_footer,
                    state.itemsConsidered,
                    state.itemsTotal,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}
