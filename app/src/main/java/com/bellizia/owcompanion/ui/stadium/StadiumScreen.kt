@file:OptIn(ExperimentalLayoutApi::class)

package com.bellizia.owcompanion.ui.stadium

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bellizia.owcompanion.R
import com.bellizia.owcompanion.ui.common.localised
import com.bellizia.owcompanion.sim.StadiumItem
import com.bellizia.owcompanion.sim.StadiumStats
import com.bellizia.owcompanion.ui.chart.parseHeroColor
import com.bellizia.owcompanion.ui.theme.StatNumber
import kotlin.math.roundToInt

/**
 * The Armory: a hero, their stats, and the items you choose.
 *
 * Items are picked by hand, the way they are in the game. The optimiser is still here but
 * demoted to a suggestion - it fills the selection, which you then argue with, because it
 * can only reason about damage and a build is rarely only about damage.
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

    LazyColumn(modifier = modifier.fillMaxSize()) {
        item { HeroAndBudget(state, viewModel) }
        item { HorizontalDivider() }
        item { StatsPanel(state, color) }
        item { BoostedAbilities(state, color) }
        item { SavedBuilds(state, viewModel, color) }
        item { CategoryTabs(state, viewModel) }

        items(state.visibleItems, key = { it.name }) { item ->
            ItemRow(
                item = item,
                selected = item.name in state.selected,
                affordable = state.canAfford(item),
                color = color,
                onClick = { viewModel.toggleSelected(item.name) },
            )
        }

        item {
            Text(
                text = stringResource(R.string.stadium_footer_v2),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

@Composable
private fun HeroAndBudget(state: StadiumUiState, viewModel: StadiumViewModel) {
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
                        label = { Text(hero, style = MaterialTheme.typography.labelSmall) },
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
                        R.string.stadium_spent,
                        state.spent.roundToInt(),
                        state.budget.roundToInt(),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(150.dp),
                )
                Slider(
                    value = state.budget,
                    onValueChange = viewModel::setBudget,
                    valueRange = 1_000f..30_000f,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(modifier = Modifier.padding(horizontal = 4.dp)) {
                TextButton(onClick = viewModel::suggest) {
                    Text(stringResource(R.string.stadium_suggest))
                }
                TextButton(onClick = viewModel::clearSelection) {
                    Text(stringResource(R.string.stadium_clear))
                }
            }
        }
    }
}

@Composable
private fun StatsPanel(state: StadiumUiState, color: Color) {
    val stats = state.stats
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = state.dps?.let { "%.0f".format(it) } ?: "-",
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
            val base = state.baseDps
            val dps = state.dps
            if (base != null && dps != null && base > 0 && dps > base * 1.001) {
                Text(
                    text = "  +%.0f%%".format((dps / base - 1) * 100),
                    style = MaterialTheme.typography.labelLarge,
                    color = color,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(top = 6.dp),
        ) {
            StatChip(stringResource(R.string.wiki_health), stats.health.toString(), color)
            if (stats.armor > 0) {
                StatChip(stringResource(R.string.wiki_armor), stats.armor.toString(), color)
            }
            if (stats.shields > 0) {
                StatChip(stringResource(R.string.wiki_shields), stats.shields.toString(), color)
            }
            percentStats(stats).forEach { (label, value) ->
                StatChip(label, "+%.0f%%".format(value), color)
            }
        }
    }
}

/** Only the percentage stats a build actually moved; a row of zeroes is noise. */
@Composable
private fun percentStats(stats: StadiumStats): List<Pair<String, Double>> {
    val weaponPower = stringResource(R.string.stat_weapon_power)
    val attackSpeed = stringResource(R.string.stat_attack_speed)
    val abilityPower = stringResource(R.string.stat_ability_power)
    val cooldown = stringResource(R.string.stat_cooldown)
    val moveSpeed = stringResource(R.string.stat_move_speed)
    val weaponLifesteal = stringResource(R.string.stat_weapon_lifesteal)
    val abilityLifesteal = stringResource(R.string.stat_ability_lifesteal)
    val maxAmmo = stringResource(R.string.stat_max_ammo)

    return listOf(
        weaponPower to stats.weaponPower,
        attackSpeed to stats.attackSpeed,
        abilityPower to stats.abilityPower,
        cooldown to stats.cooldownReduction,
        moveSpeed to stats.moveSpeed,
        weaponLifesteal to stats.weaponLifesteal,
        abilityLifesteal to stats.abilityLifesteal,
        maxAmmo to stats.maxAmmo,
    ).filter { it.second != 0.0 }
}

@Composable
private fun StatChip(label: String, value: String, color: Color) {
    Column {
        Text(text = value, style = MaterialTheme.typography.titleMedium, color = color)
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BoostedAbilities(state: StadiumUiState, color: Color) {
    if (state.boosted.isEmpty()) return
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text(
            text = stringResource(R.string.stadium_boosted),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        state.boosted.forEach { boosted ->
            Row(modifier = Modifier.padding(vertical = 2.dp)) {
                Text(
                    text = boosted.ability,
                    style = MaterialTheme.typography.titleSmall,
                    color = color,
                    modifier = Modifier.width(140.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = boosted.items.joinToString(", ") { it.name },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CategoryTabs(state: StadiumUiState, viewModel: StadiumViewModel) {
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(
            text = stringResource(R.string.stadium_armory),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            state.categories.forEach { category ->
                FilterChip(
                    selected = category == state.category,
                    onClick = { viewModel.setCategory(category) },
                    label = { Text(vocab(category), style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
        }
    }
}

@Composable
private fun ItemRow(
    item: StadiumItem,
    selected: Boolean,
    affordable: Boolean,
    color: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) {
                    color.copy(alpha = 0.22f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                },
            )
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                // The armoury's own items have no official translation - Blizzard publish
                // hero powers per locale but not these - so most of them stay English until
                // somebody contributes them. The lookup costs nothing and is ready for it.
                text = localised(item.name),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                // An item that cannot be paid for is dimmed, not hidden: knowing what is
                // out of reach is part of deciding what to save for.
                color = if (selected || affordable) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
            )
            Text(
                text = item.buffs.joinToString(" · ") { buff ->
                    buildString {
                        append(buff.stat)
                        buff.value?.let {
                            append(' ')
                            append("%.0f".format(it))
                            if (buff.percent) append('%')
                        }
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${(item.cost ?: 0.0).roundToInt()}",
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = vocab(item.rarity),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SavedBuilds(state: StadiumUiState, viewModel: StadiumViewModel, color: Color) {
    Column {
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

        if (state.naming) {
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

        val heroBuilds = state.savedBuilds.filter { it.hero == state.selectedHero }
        if (heroBuilds.isEmpty()) {
            Text(
                text = stringResource(R.string.build_none),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }

        heroBuilds.forEach { saved ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 3.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { viewModel.loadBuild(saved) },
                ) {
                    Text(saved.name, style = MaterialTheme.typography.titleSmall, color = color)
                    Text(
                        text = stringResource(R.string.build_item_count, saved.items.size),
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
        }
    }
}

/**
 * Ordinary words that arrive from the dataset in English.
 *
 * Item and hero names are proper nouns and stay as they are, but "Weapon" and "Common" are
 * not names - reading them in English inside an Italian screen is an omission rather than a
 * decision. Anything not in this short list is passed through untouched.
 */
@Composable
private fun vocab(word: String): String {
    val id = when (word.lowercase()) {
        "weapon" -> R.string.vocab_weapon
        "ability" -> R.string.vocab_ability
        "survival" -> R.string.vocab_survival
        "gadget" -> R.string.vocab_gadget
        "common" -> R.string.vocab_common
        "rare" -> R.string.vocab_rare
        "epic" -> R.string.vocab_epic
        "legendary" -> R.string.vocab_legendary
        else -> return word
    }
    return stringResource(id)
}
