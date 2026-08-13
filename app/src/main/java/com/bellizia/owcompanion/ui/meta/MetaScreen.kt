@file:OptIn(ExperimentalLayoutApi::class)

package com.bellizia.owcompanion.ui.meta

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.bellizia.owcompanion.R
import com.bellizia.owcompanion.data.MetaRepository
import kotlin.math.roundToInt

/**
 * Which heroes are being banned, picked and won with right now.
 *
 * Everything else in this app ships inside the APK and works on a plane, because the damage
 * a weapon deals and a patch that already happened do not change. The meta does, weekly, so
 * this one screen needs a connection and says so when it has not got one.
 */
@Composable
fun MetaScreen(
    modifier: Modifier = Modifier,
    viewModel: MetaViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.meta_title),
                style = MaterialTheme.typography.headlineMedium,
            )
        }

        item {
            MetaFilters(state = state, viewModel = viewModel)
        }

        when {
            state.loading -> item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }

            state.error != null -> item {
                Column(modifier = Modifier.padding(vertical = 24.dp)) {
                    Text(
                        text = stringResource(R.string.meta_offline),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = state.error.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Button(
                        onClick = viewModel::reload,
                        modifier = Modifier.padding(top = 12.dp),
                    ) { Text(stringResource(R.string.meta_retry)) }
                }
            }

            else -> {
                val heroes = state.sorted
                val peak = heroes.maxOfOrNull { state.effectiveSort.valueOf(it) } ?: 1.0
                items(heroes, key = { it.slug }) { hero ->
                    HeroRateRow(
                        hero = hero,
                        sort = state.effectiveSort,
                        peak = peak,
                        bans = state.hasBans,
                    )
                }
            }
        }

        item {
            Column(modifier = Modifier.padding(top = 16.dp)) {
                Text(
                    text = stringResource(R.string.meta_credit),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(MetaRepository.url(state.filters)),
                    )
                    runCatching { context.startActivity(intent) }
                        .onFailure { if (it !is ActivityNotFoundException) throw it }
                }) { Text(stringResource(R.string.meta_open_source)) }
            }
        }
    }
}

@Composable
private fun MetaFilters(state: MetaUiState, viewModel: MetaViewModel) {
    // Expanded, the five filter rows eat half a phone screen before the first hero appears.
    // The defaults answer the common question already, so they start folded away behind a
    // line that says what they currently are.
    var expanded by remember { mutableStateOf(false) }

    Column {
        // Sorting is the question being asked - "who is banned most?" - so it stays out
        // where it can be seen. The rest only narrow who is being asked about.
        ChipRow(stringResource(R.string.meta_sort)) {
            state.sorts.forEach { sort ->
                FilterChip(
                    selected = state.effectiveSort == sort,
                    onClick = { viewModel.sortBy(sort) },
                    label = { Text(stringResource(sort.labelRes)) },
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(top = 10.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                // stringResource is composable, so the labels are resolved one at a time
                // rather than inside a joinToString lambda, which is not.
                text = state.summaryLabels
                    .map { label -> stringResource(label) }
                    .joinToString("  ·  "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = stringResource(
                    if (expanded) R.string.meta_filters_hide else R.string.meta_filters_show,
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (expanded) {
            ChipRow(stringResource(R.string.meta_region)) {
                MetaRegion.entries.forEach { region ->
                    FilterChip(
                        selected = state.filters.region == region.value,
                        onClick = { viewModel.region(region) },
                        label = { Text(stringResource(region.labelRes)) },
                    )
                }
            }
            // Said on the screen, because the average is a choice this app made rather
            // than a figure Blizzard publish.
            if (state.filters.region == MetaRepository.WORLD) {
                Text(
                    text = stringResource(R.string.meta_region_world_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
                )
            }
            ChipRow(stringResource(R.string.meta_input)) {
                MetaInput.entries.forEach { input ->
                    FilterChip(
                        selected = state.filters.input == input.value,
                        onClick = { viewModel.input(input) },
                        label = { Text(stringResource(input.labelRes)) },
                    )
                }
            }
            ChipRow(stringResource(R.string.meta_role)) {
                MetaRole.entries.forEach { role ->
                    FilterChip(
                        selected = state.filters.role == role.value,
                        onClick = { viewModel.role(role) },
                        label = { Text(stringResource(role.labelRes)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChipRow(title: String, content: @Composable () -> Unit) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

@Composable
private fun HeroRateRow(hero: MetaRepository.HeroRate, sort: MetaSort, peak: Double, bans: Boolean) {
    val value = sort.valueOf(hero)
    Row(verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model = hero.portrait,
            contentDescription = hero.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(40.dp).clip(CircleShape),
        )
        Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = hero.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "%.1f%%".format(value),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 3.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (peak > 0) (value / peak).toFloat() else 0f)
                        .fillMaxSize()
                        .background(sort.tint()),
                )
            }
            Text(
                // A ban figure on every row is worth reading; fifty-three zeroes are not.
                text = if (bans) {
                    stringResource(R.string.meta_row_detail, hero.ban, hero.pick, hero.win)
                } else {
                    stringResource(R.string.meta_row_detail_no_bans, hero.pick, hero.win)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * A win rate is not a bigger-is-better bar like the others: 50% is the neutral point and
 * both directions are interesting, so it gets its own colour rather than pretending to be
 * a ranking of the same kind.
 */
private fun MetaSort.tint(): Color = when (this) {
    MetaSort.Ban -> Color(0xFFE0645C)
    MetaSort.Pick -> Color(0xFFF99E1A)
    MetaSort.Win -> Color(0xFF7BC96F)
}

private fun Double.asPercent(): Int = this.roundToInt()
