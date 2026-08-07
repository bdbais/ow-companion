@file:OptIn(ExperimentalLayoutApi::class)

package com.bellizia.owcompanion.ui.wiki

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.bellizia.owcompanion.R
import com.bellizia.owcompanion.data.WikiRepository
import com.bellizia.owcompanion.data.model.HeroWiki
import com.bellizia.owcompanion.ui.chart.HeroRole
import com.bellizia.owcompanion.ui.chart.parseHeroColor
import com.bellizia.owcompanion.ui.theme.StatNumber

private val BuffGreen = Color(0xFF7BC96F)
private val NerfRed = Color(0xFFE0645C)

@Composable
fun WikiScreen(
    modifier: Modifier = Modifier,
    viewModel: WikiViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.loading) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val selected = state.selected

    // Wide enough to hold both: the grid keeps its place while a hero is open, so moving
    // between heroes does not mean going back and hunting for the next one.
    if (LocalConfiguration.current.screenWidthDp >= WIDE_LAYOUT_DP) {
        Row(modifier = modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                HeroGrid(state = state, viewModel = viewModel)
            }
            VerticalDivider()
            Box(modifier = Modifier.weight(1.2f)) {
                if (selected != null) {
                    HeroDetail(hero = selected, onBack = { viewModel.select(null) })
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.wiki_pick_a_hero),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        return
    }

    if (selected != null) {
        BackHandler { viewModel.select(null) }
        HeroDetail(hero = selected, onBack = { viewModel.select(null) }, modifier = modifier)
    } else {
        HeroGrid(state = state, viewModel = viewModel, modifier = modifier)
    }
}

/** Below this the grid and the detail take turns; at or above it they share the screen. */
private const val WIDE_LAYOUT_DP = 600

@Composable
private fun HeroGrid(
    state: WikiUiState,
    viewModel: WikiViewModel,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Surface(tonalElevation = 2.dp) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    placeholder = { Text(stringResource(R.string.wiki_search)) },
                )
                // Filtering by role and ordering the grid are different questions, and a
                // single run of chips makes them look like one.
                ChipRow(title = stringResource(R.string.chart_section_role)) {
                    HeroRole.entries.forEach { role ->
                        WikiChip(
                            label = stringResource(role.labelRes),
                            selected = role in state.roles,
                            onClick = { viewModel.toggleRole(role) },
                        )
                    }
                }
                ChipRow(title = stringResource(R.string.chart_section_sort)) {
                    HeroSort.entries.forEach { sort ->
                        WikiChip(
                            label = stringResource(sort.labelRes),
                            selected = state.sort == sort,
                            onClick = { viewModel.setSort(sort) },
                        )
                    }
                }
            }
        }
        HorizontalDivider()

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 104.dp),
            contentPadding = PaddingValues(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(state.visible, key = { it.key }) { hero ->
                HeroCard(hero = hero, onClick = { viewModel.select(hero.key) })
            }
        }
    }
}

@Composable
private fun HeroCard(hero: HeroWiki, onClick: () -> Unit) {
    val color = parseHeroColor(hero.color, MaterialTheme.colorScheme.primary)

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(
                    // The portraits are cut out on a dark backdrop; a wash of the hero's
                    // own colour behind them keeps the grid from reading as a grey slab.
                    Brush.verticalGradient(
                        listOf(color.copy(alpha = 0.35f), Color.Transparent),
                    ),
                ),
        ) {
            AsyncImage(
                model = WikiRepository.imageUri(hero.portrait),
                contentDescription = hero.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            text = hero.name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
        Text(
            text = hero.role.replaceFirstChar(Char::uppercase),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(start = 6.dp, bottom = 6.dp),
        )
    }
}

@Composable
private fun HeroDetail(hero: HeroWiki, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val color = parseHeroColor(hero.color, MaterialTheme.colorScheme.primary)
    // Reset when moving to another hero: an ability name means nothing on a different one.
    var abilityFilter by remember(hero.key) { mutableStateOf<String?>(null) }
    val onAbilityFilterChange: (String?) -> Unit = { abilityFilter = it }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        item {
            Box {
                // Sized by the row in front of it rather than by a fixed height, which left
                // a band of empty colour under short headers.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(color.copy(alpha = 0.45f), Color.Transparent),
                            ),
                        ),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 48.dp, top = 12.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = WikiRepository.imageUri(hero.portrait),
                        contentDescription = hero.name,
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape),
                    )
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(hero.name, style = MaterialTheme.typography.headlineMedium)
                        Text(
                            text = listOfNotNull(
                                hero.subrole?.replaceFirstChar(Char::uppercase),
                                hero.role.replaceFirstChar(Char::uppercase),
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.labelLarge,
                            color = color,
                        )
                        hero.location?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                // A close button in the corner, not a back arrow over the artwork: the
                // arrow was invisible against a dark portrait.
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.wiki_close))
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Stat(stringResource(R.string.wiki_health), hero.totalHitpoints?.toString() ?: "-", color)
                hero.armor?.takeIf { it > 0 }?.let { Stat(stringResource(R.string.wiki_armor), it.toString(), color) }
                hero.shields?.takeIf { it > 0 }?.let { Stat(stringResource(R.string.wiki_shields), it.toString(), color) }
                Stat(
                    label = hero.heroNumber?.let { stringResource(R.string.wiki_hero_number, it) }
                        ?: stringResource(R.string.wiki_released),
                    value = hero.releaseDate ?: "-",
                    color = color,
                )
            }
        }

        item {
            // How often a hero has been strengthened or weakened over their whole life,
            // counted from the numeric changes the patch notes state.
            val buffs = hero.patches.sumOf { patch ->
                patch.stats.count { it.direction == "buff" }
            }
            val nerfs = hero.patches.sumOf { patch ->
                patch.stats.count { it.direction == "nerf" }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Stat(
                    label = stringResource(R.string.wiki_total_buffs),
                    value = buffs.toString(),
                    color = BuffGreen,
                )
                Stat(
                    label = stringResource(R.string.wiki_total_nerfs),
                    value = nerfs.toString(),
                    color = NerfRed,
                )
                Stat(
                    label = stringResource(R.string.wiki_total_patches),
                    value = hero.patches.size.toString(),
                    color = color,
                )
            }
        }

        hero.description?.takeIf { it.isNotBlank() }?.let { description ->
            item {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                )
            }
        }

        item {
            Text(
                text = stringResource(R.string.wiki_abilities),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
            )
        }

        items(hero.abilities) { ability ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                AsyncImage(
                    model = WikiRepository.imageUri(ability.icon),
                    contentDescription = null,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(color.copy(alpha = 0.18f)),
                )
                Column(modifier = Modifier.padding(start = 10.dp)) {
                    Text(
                        text = ability.name ?: "",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = ability.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (hero.perks.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.wiki_perks),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp),
                )
            }
            items(hero.perks) { perk ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = stringResource(
                            if (perk.tier == "major") R.string.wiki_perk_major
                            else R.string.wiki_perk_minor,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .padding(top = 2.dp, end = 8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (perk.tier == "major") color else color.copy(alpha = 0.45f),
                            )
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                    Column {
                        Text(
                            text = perk.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = perk.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item {
            val changes = hero.patches.sumOf { it.changes.size }
            Text(
                text = stringResource(
                    R.string.wiki_history_summary,
                    changes,
                    hero.patches.size,
                    hero.patches.lastOrNull()?.date ?: "-",
                    hero.patches.firstOrNull()?.date ?: "-",
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }

        patchTimeline(
            hero = hero,
            color = color,
            abilityFilter = abilityFilter,
            onAbilityFilterChange = onAbilityFilterChange,
        )
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color) {
    Column {
        Text(text = value, style = StatNumber, color = color)
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChipRow(title: String, content: @Composable FlowRowScope.() -> Unit) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy((-4).dp),
        content = content,
    )
}

@Composable
private fun WikiChip(label: String, selected: Boolean, onClick: () -> Unit) {
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
