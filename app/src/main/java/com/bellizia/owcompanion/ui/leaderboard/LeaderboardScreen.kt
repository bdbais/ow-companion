@file:OptIn(ExperimentalLayoutApi::class)

package com.bellizia.owcompanion.ui.leaderboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bellizia.owcompanion.sim.DamagePeak
import com.bellizia.owcompanion.ui.chart.parseHeroColor
import com.bellizia.owcompanion.ui.theme.StatNumber

private data class BuffToggle(
    val label: String,
    val isOn: (BuffSelection) -> Boolean,
    val set: (BuffSelection, Boolean) -> BuffSelection,
)

private val BuffToggles = listOf(
    BuffToggle("Damage boost", { it.damageBoost }, { b, v -> b.copy(damageBoost = v) }),
    BuffToggle("Discord", { it.discord }, { b, v -> b.copy(discord = v) }),
    BuffToggle("Nano Boost", { it.nanoboost }, { b, v -> b.copy(nanoboost = v) }),
    BuffToggle("Supercharger", { it.supercharger }, { b, v -> b.copy(supercharger = v) }),
    BuffToggle("Ampl. Matrix", { it.amplificationMatrix }, { b, v -> b.copy(amplificationMatrix = v) }),
)

/**
 * The ten highest damage-per-second figures anyone can reach, and what it takes to reach
 * them.
 *
 * Each weapon is swept across the full range and every sensible aim point to find where it
 * peaks, so the ranking reflects what a weapon can do at its best rather than at some
 * arbitrary distance. The buffs are all amplifying, so switching more of them on can only
 * raise the numbers - what changes is the order, because they do not all help equally.
 */
@Composable
fun LeaderboardScreen(
    modifier: Modifier = Modifier,
    viewModel: LeaderboardViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        Surface(tonalElevation = 2.dp) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    text = "Buffs allowed",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BuffToggles.forEach { toggle ->
                        val on = toggle.isOn(state.buffs)
                        FilterChip(
                            selected = on,
                            onClick = { viewModel.setBuffs(toggle.set(state.buffs, !on)) },
                            label = {
                                Text(toggle.label, style = MaterialTheme.typography.labelSmall)
                            },
                            leadingIcon = if (on) {
                                { Icon(Icons.Filled.Check, contentDescription = null) }
                            } else {
                                null
                            },
                            // The default selected container is nearly invisible against a
                            // dark surface, and these toggles change every number on screen.
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        )
                    }
                }
            }
        }
        HorizontalDivider()

        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text(
                        text = "Sweeping every weapon across every range",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.entries, key = { it.peak.spec.id }) { entry ->
                LeaderboardCard(entry)
            }
            item {
                Text(
                    text = "Searched in ${state.computeMillis} ms. Ranked by sustained damage " +
                        "per second, reload included.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun LeaderboardCard(entry: LeaderboardEntry) {
    val color = parseHeroColor(entry.hero?.color, MaterialTheme.colorScheme.primary)
    val peak = entry.peak

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(color),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = entry.rank.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.surface,
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = peak.spec.hero,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = peak.spec.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = conditionsOf(peak),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "%.0f".format(peak.dps),
                    style = StatNumber,
                    color = color,
                    textAlign = TextAlign.End,
                )
                Text(
                    text = "dps",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // A buffed figure means little on its own; the multiplier says how much of
                // it is the weapon and how much is the team standing behind it.
                if (peak.unbuffedDps > 0 && peak.dps > peak.unbuffedDps * 1.01) {
                    Text(
                        text = "×%.1f".format(peak.dps / peak.unbuffedDps),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }
    }
}

private fun conditionsOf(peak: DamagePeak): String = buildString {
    append("at %.1f m".format(peak.distance))
    append(if (peak.onHead) " · aimed at the head" else " · centre mass")
    if (peak.critAccuracy > 0.01) {
        append(" · %.0f%% crits".format(peak.critAccuracy * 100))
    }
    if (peak.timeToKill.isFinite()) {
        // At the top of this ranking the first shot already kills, and "0.0 s" reads like
        // a bug rather than like a one-shot.
        val kill = if (peak.timeToKill < 0.05) "instantly" else "in %.1f s".format(peak.timeToKill)
        append(" · kills a 600 hp target $kill")
    }
}
