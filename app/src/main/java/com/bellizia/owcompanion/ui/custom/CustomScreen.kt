@file:OptIn(ExperimentalLayoutApi::class)

package com.bellizia.owcompanion.ui.custom

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bellizia.owcompanion.R
import com.bellizia.owcompanion.ui.chart.parseHeroColor
import com.bellizia.owcompanion.ui.theme.StatNumber
import kotlin.math.abs
import kotlin.math.roundToInt

private data class StatSlider(val stat: TunableStat, val labelRes: Int)

private val Sliders = listOf(
    StatSlider(TunableStat.Damage, R.string.custom_stat_damage),
    StatSlider(TunableStat.FireRate, R.string.custom_stat_fire_rate),
    StatSlider(TunableStat.Ammo, R.string.custom_stat_ammo),
    StatSlider(TunableStat.Reload, R.string.custom_stat_reload),
    StatSlider(TunableStat.Pellets, R.string.custom_stat_pellets),
)

/**
 * "What if Roadhog reloaded a third of a second faster?"
 *
 * Pick a real weapon, move its numbers, and watch where the hero lands in the ranking. The
 * ranking behind it is the real one, so the answer is a position among actual heroes rather
 * than an abstract percentage.
 */
@Composable
fun CustomScreen(
    modifier: Modifier = Modifier,
    viewModel: CustomViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.loading) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val spec = state.weaponsForHero.firstOrNull { it.name == state.selectedWeapon }
    val color = parseHeroColor(
        state.baseline.firstOrNull { it.name == state.selectedHero }?.color,
        MaterialTheme.colorScheme.primary,
    )

    LazyColumn(modifier = modifier.fillMaxSize()) {
        item {
            Surface(tonalElevation = 2.dp) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Label(R.string.custom_pick_hero)
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(state.heroes) { hero ->
                            Chip(
                                label = hero,
                                selected = hero == state.selectedHero,
                                onClick = { viewModel.selectHero(hero) },
                            )
                        }
                    }
                    if (state.weaponsForHero.size > 1) {
                        Label(R.string.custom_pick_weapon)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 12.dp),
                        ) {
                            state.weaponsForHero.forEach { weapon ->
                                Chip(
                                    label = weapon.name,
                                    selected = weapon.name == state.selectedWeapon,
                                    onClick = { viewModel.selectWeapon(weapon.name) },
                                )
                            }
                        }
                    }
                }
            }
        }

        item { HorizontalDivider() }

        item {
            ResultPanel(state = state, color = color)
        }

        items(Sliders) { slider ->
            val factor = state.tuning.factor(slider.stat)
            val original = spec?.let { originalValue(it, slider.stat) }
            Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(slider.labelRes),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.width(150.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = original?.let { value ->
                            val tuned = value * factor
                            if (abs(factor - 1f) < 0.005f) format(tuned)
                            else "${format(value)} → ${format(tuned)}"
                        } ?: "-",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (abs(factor - 1f) < 0.005f) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            color
                        },
                    )
                }
                Slider(
                    value = factor,
                    onValueChange = { viewModel.setFactor(slider.stat, it) },
                    valueRange = 0.25f..3f,
                )
            }
        }

        item {
            TextButton(
                onClick = viewModel::reset,
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                Text(stringResource(R.string.custom_reset))
            }
        }

        item {
            Text(
                text = stringResource(R.string.custom_neighbours),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp),
            )
        }

        // A window of the real ranking around wherever the tuned weapon landed, so the
        // number has company: "ahead of Reaper, behind Bastion" beats "rank 14".
        val window = neighbours(state)
        items(window) { entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${entry.rank}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(32.dp),
                )
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = parseHeroColor(entry.color, MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "%.0f".format(entry.dps),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

private data class Neighbour(val rank: Int, val name: String, val dps: Double, val color: String)

private fun neighbours(state: CustomUiState): List<Neighbour> {
    val others = state.baseline.filter { it.name != state.selectedHero }
    val target = (state.tunedRank - 1).coerceIn(0, maxOf(0, others.size - 1))
    val from = (target - 3).coerceAtLeast(0)
    val to = (target + 3).coerceAtMost(others.size)
    return others.subList(from, to).mapIndexed { index, hero ->
        Neighbour(from + index + 1, hero.name, hero.dps, hero.color)
    }
}

@Composable
private fun ResultPanel(state: CustomUiState, color: androidx.compose.ui.graphics.Color) {
    val peak = state.tunedPeak
    Column(modifier = Modifier.padding(12.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = peak?.let { "%.0f".format(it.dps) } ?: "-",
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
                    modifier = Modifier
                        .padding(start = 10.dp, bottom = 6.dp)
                        .width(14.dp),
                )
            }
        }

        if (state.originalRank > 0) {
            Text(
                text = stringResource(
                    R.string.custom_rank,
                    state.tunedRank,
                    state.baseline.size,
                ) + if (state.tunedRank != state.originalRank) {
                    " · " + stringResource(R.string.custom_rank_was, state.originalRank)
                } else {
                    ""
                },
                style = MaterialTheme.typography.labelLarge,
            )
        }

        if (state.tuning.isPristine) {
            Text(
                text = stringResource(R.string.custom_unchanged),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (peak != null && state.originalDps > 0) {
            val delta = (peak.dps / state.originalDps - 1) * 100
            Text(
                text = stringResource(
                    R.string.custom_delta,
                    (if (delta >= 0) "+" else "") + "%.1f".format(delta),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = if (delta >= 0) color else MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun Label(res: Int) {
    Text(
        text = stringResource(res),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 2.dp),
    )
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}

private fun originalValue(
    spec: com.bellizia.owcompanion.sim.WeaponSpec,
    stat: TunableStat,
): Float? = when (stat) {
    TunableStat.Damage -> spec.damage.dpshot.firstOrNull()?.toFloat()
    TunableStat.FireRate -> spec.fireRate?.toFloat()
    TunableStat.Ammo -> spec.ammo?.toFloat()
    TunableStat.Reload -> spec.reloadTime?.toFloat()
    TunableStat.Pellets -> spec.pellets.firstOrNull()?.toFloat()
}

private fun format(value: Float): String =
    if (abs(value - value.roundToInt()) < 0.05f) value.roundToInt().toString()
    else "%.2f".format(value)
