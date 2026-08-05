@file:OptIn(ExperimentalLayoutApi::class)

package com.bellizia.owcompanion.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bellizia.owcompanion.sim.Modifiers
import com.bellizia.owcompanion.sim.Simulator
import kotlin.math.roundToInt

private data class ModifierToggle(
    val label: String,
    val isOn: (Modifiers) -> Boolean,
    val set: (Modifiers, Boolean) -> Modifiers,
)

private val ModifierToggles = listOf(
    ModifierToggle("Armor", { it.armor }, { m, v -> m.copy(armor = v) }),
    ModifierToggle("Damage boost", { it.damageBoost }, { m, v -> m.copy(damageBoost = v) }),
    ModifierToggle("Discord", { it.discord }, { m, v -> m.copy(discord = v) }),
    ModifierToggle("Nano (dmg)", { it.nanoboostOffence }, { m, v -> m.copy(nanoboostOffence = v) }),
    ModifierToggle("Nano (def)", { it.nanoboostDefence }, { m, v -> m.copy(nanoboostDefence = v) }),
    ModifierToggle("Supercharger", { it.supercharger }, { m, v -> m.copy(supercharger = v) }),
    ModifierToggle("Ampl. matrix", { it.amplificationMatrix }, { m, v -> m.copy(amplificationMatrix = v) }),
    ModifierToggle("Fortify", { it.fortify }, { m, v -> m.copy(fortify = v) }),
    ModifierToggle("Breather", { it.takeABreather }, { m, v -> m.copy(takeABreather = v) }),
    ModifierToggle("Kitsune Rush", { it.kitsuneRush }, { m, v -> m.copy(kitsuneRush = v) }),
)

@Composable
fun ChartScreen(
    modifier: Modifier = Modifier,
    viewModel: ChartViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val timelineScroll = rememberScrollState()
    var controlsExpanded by rememberSaveable { mutableStateOf(false) }
    val transformState = rememberTransformableState { zoomChange, _, _ ->
        viewModel.setZoom(state.zoom * zoomChange)
    }

    Column(modifier = modifier.fillMaxSize()) {
        ControlPanel(
            state = state,
            viewModel = viewModel,
            expanded = controlsExpanded,
            onExpandedChange = { controlsExpanded = it },
        )
        HorizontalDivider()

        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        TimeAxis(zoom = state.zoom, scrollState = timelineScroll)

        // Changing the sort or the filters rebuilds the list under a scroll position that
        // no longer means anything - leaving the reader looking at the tail of a shorter
        // list and concluding the filter did not work.
        val listState = rememberLazyListState()
        LaunchedEffect(state.sortOrder, state.roles, state.categories) {
            listState.scrollToItem(0)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .transformable(transformState),
        ) {
            items(state.rows, key = { it.spec.id }) { row ->
                ChartRowView(row = row, zoom = state.zoom, scrollState = timelineScroll)
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
    }
}

@Composable
private fun ControlPanel(
    state: ChartUiState,
    viewModel: ChartViewModel,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    Surface(tonalElevation = 2.dp) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AimTarget(
                    aimX = state.aimX,
                    aimZ = state.aimZ,
                    onAimChange = viewModel::setAim,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Distance ${state.distance.roundToInt()} m",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Slider(
                        value = state.distance,
                        onValueChange = viewModel::setDistance,
                        valueRange = 0f..60f,
                    )
                    Text(
                        text = "Aim ${"%.2f".format(state.aimX)}, ${"%.2f".format(state.aimZ)} m" +
                            "  ·  ${state.rows.size} weapons  ·  ${state.lastComputeMillis} ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            ChipSection(title = "Sort by") {
                SortOrder.entries.forEach { order ->
                    Chip(
                        label = order.label,
                        selected = state.sortOrder == order,
                        onClick = { viewModel.setSortOrder(order) },
                    )
                }
            }

            // Modifiers stay on screen: they are what the chart is for, and burying them
            // behind a fold makes it easy to forget one is on and misread every bar.
            ChipSection(title = "Modifiers") {
                ModifierToggles.forEach { toggle ->
                    Chip(
                        label = toggle.label,
                        selected = toggle.isOn(state.modifiers),
                        onClick = {
                            viewModel.setModifiers(
                                toggle.set(state.modifiers, !toggle.isOn(state.modifiers)),
                            )
                        },
                    )
                }
            }

            // Role and weapon-type filters are set once and left alone, so they can fold.
            val hiddenFilters = (HeroRole.entries.size - state.roles.size) +
                (WeaponCategory.entries.size - state.categories.size)
            TextButton(
                onClick = { onExpandedChange(!expanded) },
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                )
                Text(
                    text = buildString {
                        append("Filters")
                        if (hiddenFilters > 0) append("  ·  $hiddenFilters hidden")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }

            if (expanded) {
                ChipSection(title = "Roles and weapon types") {
                    HeroRole.entries.forEach { role ->
                        Chip(
                            label = role.label,
                            selected = role in state.roles,
                            onClick = { viewModel.toggleRole(role) },
                        )
                    }
                    WeaponCategory.entries.forEach { category ->
                        Chip(
                            label = category.label,
                            selected = category in state.categories,
                            onClick = { viewModel.toggleCategory(category) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChipSection(title: String, content: @Composable FlowRowScope.() -> Unit) {
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
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        // The default selected container barely reads against a dark surface, and these
        // change every number on screen.
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}

/** Second-by-second ruler above the rows, scrolling in lockstep with them. */
@Composable
private fun TimeAxis(zoom: Float, scrollState: androidx.compose.foundation.ScrollState) {
    val textMeasurer = rememberTextMeasurer()
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = TextStyle(fontSize = 9.sp, color = tickColor)

    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = "time (s)",
            style = MaterialTheme.typography.labelSmall,
            color = tickColor,
            modifier = Modifier
                .width(LabelColumnWidth)
                .padding(start = 8.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
        ) {
            Canvas(
                modifier = Modifier
                    .width(chartWidthDp(zoom).dp)
                    .height(20.dp),
            ) {
                val seconds = Simulator.MAX_TIME.toInt()
                // Label every second when there is room, otherwise thin them out.
                val labelStep = if (zoom >= 0.8f) 1 else if (zoom >= 0.5f) 2 else 5
                for (second in 0..seconds) {
                    val x = timeToDp(second.toDouble(), zoom).dp.toPx()
                    val major = second % labelStep == 0
                    drawLine(
                        color = tickColor.copy(alpha = if (major) 0.7f else 0.3f),
                        start = Offset(x, size.height - (if (major) 7.dp.toPx() else 4.dp.toPx())),
                        end = Offset(x, size.height),
                        strokeWidth = 1.dp.toPx(),
                    )
                    if (major) {
                        val text = textMeasurer.measure(second.toString(), labelStyle)
                        drawText(
                            textLayoutResult = text,
                            topLeft = Offset(x - text.size.width / 2f, 0f),
                        )
                    }
                }
                drawLine(
                    color = tickColor.copy(alpha = 0.5f),
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            }
        }
    }
}
