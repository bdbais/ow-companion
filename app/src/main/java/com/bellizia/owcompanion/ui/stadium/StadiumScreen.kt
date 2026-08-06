package com.bellizia.owcompanion.ui.stadium

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.bellizia.owcompanion.data.DatasetRepository
import com.bellizia.owcompanion.sim.StadiumBuild
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
)

class StadiumViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DatasetRepository(application)
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
