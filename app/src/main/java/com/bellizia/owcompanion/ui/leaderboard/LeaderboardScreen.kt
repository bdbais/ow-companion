@file:OptIn(ExperimentalLayoutApi::class)

package com.bellizia.owcompanion.ui.leaderboard

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import com.bellizia.owcompanion.sim.Breakpoints
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bellizia.owcompanion.R
import kotlin.math.roundToInt
import com.bellizia.owcompanion.ui.rememberFilterTaps
import com.bellizia.owcompanion.sim.DamagePeak
import com.bellizia.owcompanion.ui.chart.HeroRole
import com.bellizia.owcompanion.ui.chart.parseHeroColor
import com.bellizia.owcompanion.ui.theme.StatNumber

private data class BuffToggle(
    @StringRes val labelRes: Int,
    val isOn: (BuffSelection) -> Boolean,
    val set: (BuffSelection, Boolean) -> BuffSelection,
)

private val BuffToggles = listOf(
    BuffToggle(R.string.modifier_damage_boost, { it.damageBoost }, { b, v -> b.copy(damageBoost = v) }),
    BuffToggle(R.string.modifier_discord, { it.discord }, { b, v -> b.copy(discord = v) }),
    BuffToggle(R.string.buff_nano, { it.nanoboost }, { b, v -> b.copy(nanoboost = v) }),
    BuffToggle(R.string.modifier_supercharger, { it.supercharger }, { b, v -> b.copy(supercharger = v) }),
    BuffToggle(R.string.modifier_amplification_matrix, { it.amplificationMatrix }, { b, v -> b.copy(amplificationMatrix = v) }),
    BuffToggle(R.string.modifier_kitsune_rush, { it.kitsuneRush }, { b, v -> b.copy(kitsuneRush = v) }),
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
    val roleTaps = rememberFilterTaps<HeroRole>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                // Three different questions with three different metrics, so they get
                // separate rankings rather than one list sorted three ways.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RankingMode.entries.forEach { mode ->
                        FilterChip(
                            selected = state.mode == mode,
                            onClick = { viewModel.setMode(mode) },
                            label = {
                                Text(
                                    stringResource(mode.labelRes),
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

                Text(
                    text = stringResource(R.string.rank_era),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Era.entries.forEach { era ->
                        FilterChip(
                            selected = state.era == era,
                            onClick = { viewModel.setEra(era) },
                            label = {
                                Text(
                                    stringResource(era.labelRes),
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

                Text(
                    text = stringResource(R.string.chart_section_role),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HeroRole.entries.forEach { role ->
                        FilterChip(
                            selected = role in state.roles,
                            onClick = { viewModel.setRoles(roleTaps.onTap(role, state.roles, HeroRole.entries.toSet())) },
                            label = {
                                Text(
                                    stringResource(role.labelRes),
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

                // Buffs only mean something for weapons: an ultimate's damage is what it
                // is, and healing output is not what damage boost multiplies. Saying so
                // beats having the row silently vanish between tabs.
                if (state.mode != RankingMode.Weapons) {
                    Text(
                        text = stringResource(R.string.rank_buffs_weapons_only),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (state.mode == RankingMode.Weapons) {
                    Text(
                        text = stringResource(R.string.leaderboard_buffs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        BuffToggles.forEach { toggle ->
                            val on = toggle.isOn(state.buffs)
                            FilterChip(
                                selected = on,
                                onClick = { viewModel.setBuffs(toggle.set(state.buffs, !on)) },
                                label = {
                                    Text(
                                        stringResource(toggle.labelRes),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                },
                                leadingIcon = if (on) {
                                    { Icon(Icons.Filled.Check, contentDescription = null) }
                                } else {
                                    null
                                },
                                // The default selected container is nearly invisible on a
                                // dark surface, and these change every number on screen.
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
        }
        HorizontalDivider()

        when (state.mode) {
            RankingMode.Ultimates -> {
                UltimateList(state)
                return@Column
            }

            RankingMode.Healing -> {
                HealingList(state)
                return@Column
            }

            RankingMode.Combos -> {
                ComboList(state)
                return@Column
            }

            RankingMode.Shots -> {
                ShotsList(state)
                return@Column
            }

            RankingMode.Duel -> {
                DuelView(state)
                return@Column
            }

            RankingMode.Weapons -> Unit
        }

        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text(
                        text = stringResource(R.string.leaderboard_searching),
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
            items(state.visible(state.entries) { it.hero }, key = { it.peak.spec.id }) { entry ->
                LeaderboardCard(entry)
            }
            item {
                Text(
                    text = stringResource(R.string.leaderboard_footer, state.computeMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun UltimateList(state: LeaderboardUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.visible(state.ultimates) { it.hero }, key = { "${it.ultimate.hero}|${it.ultimate.name}" }) { entry ->
            val color = parseHeroColor(entry.hero?.color, MaterialTheme.colorScheme.primary)
            RankRow(
                rank = entry.rank,
                hero = entry.ultimate.hero,
                subtitle = entry.ultimate.name,
                detail = buildString {
                    entry.ultimate.detail?.let { append(it) }
                    entry.ultimate.duration?.let {
                        append("  ·  ")
                        append(stringResource(R.string.ultimate_duration, trim(it)))
                    }
                },
                value = "%.0f".format(entry.ultimate.damage ?: 0.0),
                unit = stringResource(R.string.ultimate_damage, "").trim(),
                color = color,
            )
        }
        item {
            Text(
                text = stringResource(
                    R.string.ultimate_no_damage,
                    state.ultimatesWithoutDamage,
                ) + "\n" + stringResource(R.string.ultimate_footer),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/**
 * Every hero's opening, totalled and ranked.
 *
 * The wiki mentions combos constantly and never as a sequence with numbers, so these are
 * computed - each ability once, a melee, then the hardest single shot - out of figures that
 * are individually sourced. Best case on one target, which is what makes it comparable.
 */
@Composable
private fun ComboList(state: LeaderboardUiState) {
    LazyColumn(contentPadding = PaddingValues(12.dp)) {
        item {
            Text(
                text = stringResource(R.string.rank_combos_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }
        items(state.combos, key = { it.heroName }) { entry ->
            val color = parseHeroColor(entry.hero?.color, MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${entry.rank}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(28.dp),
                    )
                    Text(
                        text = entry.heroName,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = entry.total.roundToInt().toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = color,
                    )
                }
                Text(
                    text = entry.steps.joinToString("  +  ") {
                        "${it.name} ${it.damage.roundToInt()}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 28.dp, top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun HealingList(state: LeaderboardUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.visible(state.healing) { it.hero }, key = { "${it.source.hero}|${it.source.name}" }) { entry ->
            val color = parseHeroColor(entry.hero?.color, MaterialTheme.colorScheme.primary)
            RankRow(
                rank = entry.rank,
                hero = entry.source.hero,
                subtitle = entry.source.name,
                detail = entry.source.detail.orEmpty(),
                value = "%.0f".format(entry.healingPerSecond),
                unit = "hps",
                color = color,
            )
        }
        item {
            Text(
                text = stringResource(R.string.healing_footer),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** Shared row shape so the three rankings read as the same kind of thing. */
@Composable
private fun RankRow(
    rank: Int,
    hero: String,
    subtitle: String,
    detail: String,
    value: String,
    unit: String,
    color: androidx.compose.ui.graphics.Color,
) {
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
                    text = rank.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.surface,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(hero, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (detail.isNotBlank()) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(value, style = StatNumber, color = color, textAlign = TextAlign.End)
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun trim(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else "%.1f".format(value)

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

/**
 * How many shots each weapon needs, against a target you choose.
 *
 * The other three lists answer "who does the most damage". This one answers the question
 * that actually decides a duel - two shots or three - and it is a whole number, unmoved by
 * how well anyone aims. It is the part of this app that survives the complaint that
 * statistics stop mattering above a certain rank.
 *
 * Point blank, and the screen says so: past the falloff the counts change, and a table that
 * quietly averaged over range would be worse than no table.
 */
@Composable
private fun ShotsList(state: LeaderboardUiState) {
    val targets = remember(state.heroes) {
        Breakpoints.targetsFrom(
            state.heroes.values.map { hero ->
                Breakpoints.Target(
                    hero.name,
                    hero.health ?: 0,
                    hero.armor ?: 0,
                    hero.shields ?: 0,
                )
            },
        )
    }
    if (targets.isEmpty()) return
    var target by rememberSaveable { mutableIntStateOf(0) }
    val chosen = targets[target.coerceIn(targets.indices)]

    val rows = remember(chosen, state.weapons) {
        state.weapons
            .map { it to Breakpoints.shotsToKill(it, chosen) }
            .filter { (_, result) -> result.body in 1..40 }
            .sortedBy { (_, result) -> result.body }
    }

    Column(modifier = Modifier.padding(horizontal = 12.dp)) {
        Text(
            text = stringResource(R.string.rank_shots_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(targets) { index, option ->
                FilterChip(
                    selected = index == target,
                    onClick = { target = index },
                    label = {
                        Text(
                            text = if (option.armor > 0 || option.shields > 0) {
                                "${option.total} (${option.name})"
                            } else {
                                "${option.total}"
                            },
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                )
            }
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(rows, key = { (weapon, _) -> weapon.hero + weapon.name }) { (weapon, result) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = weapon.hero, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = weapon.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // The head count only earns its place when it removes a shot.
                if (result.headSaves) {
                    Text(
                        text = stringResource(R.string.rank_shots_head, result.head ?: 0),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 10.dp),
                    )
                }
                Text(
                    text = "${result.body}",
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            HorizontalDivider()
        }
    }
}
