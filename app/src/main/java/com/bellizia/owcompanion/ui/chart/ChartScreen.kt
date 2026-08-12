@file:OptIn(ExperimentalLayoutApi::class)

package com.bellizia.owcompanion.ui.chart

import androidx.annotation.StringRes
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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.VerticalDivider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bellizia.owcompanion.R
import com.bellizia.owcompanion.ui.rememberFilterTaps
import com.bellizia.owcompanion.sim.Modifiers
import com.bellizia.owcompanion.sim.Simulator
import kotlin.math.roundToInt

private data class ModifierToggle(
    @StringRes val labelRes: Int,
    val isOn: (Modifiers) -> Boolean,
    val set: (Modifiers, Boolean) -> Modifiers,
)

private val ModifierToggles = listOf(
    ModifierToggle(R.string.modifier_armor, { it.armor }, { m, v -> m.copy(armor = v) }),
    ModifierToggle(R.string.modifier_damage_boost, { it.damageBoost }, { m, v -> m.copy(damageBoost = v) }),
    ModifierToggle(R.string.modifier_discord, { it.discord }, { m, v -> m.copy(discord = v) }),
    ModifierToggle(R.string.modifier_nano_damage, { it.nanoboostOffence }, { m, v -> m.copy(nanoboostOffence = v) }),
    ModifierToggle(R.string.modifier_nano_defence, { it.nanoboostDefence }, { m, v -> m.copy(nanoboostDefence = v) }),
    ModifierToggle(R.string.modifier_supercharger, { it.supercharger }, { m, v -> m.copy(supercharger = v) }),
    ModifierToggle(R.string.modifier_amplification_matrix, { it.amplificationMatrix }, { m, v -> m.copy(amplificationMatrix = v) }),
    ModifierToggle(R.string.modifier_fortify, { it.fortify }, { m, v -> m.copy(fortify = v) }),
    ModifierToggle(R.string.modifier_breather, { it.takeABreather }, { m, v -> m.copy(takeABreather = v) }),
    // The blocks the tanks hold up. They were missing entirely, which flattered every
    // weapon on the chart against the four heroes who spend a fight behind one.
    ModifierToggle(R.string.modifier_power_block, { it.powerBlock }, { m, v -> m.copy(powerBlock = v) }),
    ModifierToggle(R.string.modifier_nemesis_block, { it.nemesisBlock }, { m, v -> m.copy(nemesisBlock = v) }),
    ModifierToggle(R.string.modifier_spike_guard, { it.spikeGuard }, { m, v -> m.copy(spikeGuard = v) }),
    ModifierToggle(R.string.modifier_overrun, { it.overrun }, { m, v -> m.copy(overrun = v) }),
    ModifierToggle(R.string.modifier_cardiac, { it.cardiacOverdrive }, { m, v -> m.copy(cardiacOverdrive = v) }),
    // A state of the target rather than a buff on the shooter, which is why it sits with
    // the modifiers instead of inside the two weapons that care about it.
    ModifierToggle(R.string.modifier_burning, { it.burning }, { m, v -> m.copy(burning = v) }),
    ModifierToggle(R.string.modifier_kitsune_rush, { it.kitsuneRush }, { m, v -> m.copy(kitsuneRush = v) }),
)

@Composable
fun ChartScreen(
    modifier: Modifier = Modifier,
    viewModel: ChartViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val timelineScroll = rememberScrollState()
    var sections by rememberSaveable(stateSaver = OpenSectionsSaver) {
        mutableStateOf(OpenSections())
    }
    var controlsCollapsed by rememberSaveable { mutableStateOf(false) }
    val transformState = rememberTransformableState { zoomChange, _, _ ->
        viewModel.setZoom(state.zoom * zoomChange)
    }

    // On a wide screen the controls sit beside the chart instead of stacking above it: a
    // landscape phone has almost no vertical room, and stacking leaves two visible rows.
    val wide = LocalConfiguration.current.screenWidthDp >= WIDE_LAYOUT_DP

    if (wide) {
        Row(modifier = modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .width(SIDE_PANEL_WIDTH)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
            ) {
                ControlPanel(
                    state = state,
                    viewModel = viewModel,
                    sections = sections,
                    onSectionsChange = { sections = it },
                    onCollapse = null,
                )
            }
            VerticalDivider()
            Column(modifier = Modifier.weight(1f)) {
                ChartBody(state, timelineScroll, transformState)
            }
        }
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (controlsCollapsed) {
            CollapsedControlBar(
                state = state,
                viewModel = viewModel,
                onExpand = { controlsCollapsed = false },
            )
        } else {
            ControlPanel(
                state = state,
                viewModel = viewModel,
                sections = sections,
                onSectionsChange = { sections = it },
                onCollapse = { controlsCollapsed = true },
            )
        }
        HorizontalDivider()
        ChartBody(state, timelineScroll, transformState)
    }
}

@Composable
private fun ColumnScope.ChartBody(
    state: ChartUiState,
    timelineScroll: androidx.compose.foundation.ScrollState,
    transformState: androidx.compose.foundation.gestures.TransformableState,
) {
    if (state.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
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
            ChartRowView(
                row = row,
                zoom = state.zoom,
                scrollState = timelineScroll,
                distance = state.distance,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        }
    }
}

/** Below this the controls stack above the chart; at or above it they sit beside it. */
private const val WIDE_LAYOUT_DP = 600
private val SIDE_PANEL_WIDTH = 320.dp

/** Which control sections are open. Filters start closed; the rest start open. */
private data class OpenSections(
    val sort: Boolean = true,
    val modifiers: Boolean = true,
    val roles: Boolean = false,
    val categories: Boolean = false,
    val fireModes: Boolean = false,
)

private val OpenSectionsSaver = listSaver<OpenSections, Boolean>(
    save = { listOf(it.sort, it.modifiers, it.roles, it.categories, it.fireModes) },
    restore = { OpenSections(it[0], it[1], it[2], it[3], it[4]) },
)

@Composable
private fun ControlPanel(
    state: ChartUiState,
    viewModel: ChartViewModel,
    sections: OpenSections,
    onSectionsChange: (OpenSections) -> Unit,
    /** Null on a wide screen, where the panel has its own column and nothing to yield. */
    onCollapse: (() -> Unit)?,
) {
    // One tracker per chip row: a double tap means "just this one" only within the row
    // it was aimed at.
    val roleTaps = rememberFilterTaps<HeroRole>()
    val modeTaps = rememberFilterTaps<FireMode>()
    val typeTaps = rememberFilterTaps<WeaponCategory>()
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.chart_distance, state.distance.roundToInt()),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f),
                        )
                        if (onCollapse != null) {
                            IconButton(onClick = onCollapse) {
                                Icon(
                                    imageVector = Icons.Filled.UnfoldLess,
                                    contentDescription = stringResource(R.string.chart_collapse),
                                )
                            }
                        }
                    }
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

            // Zarya's damage scales with her charge, and no other weapon reads it - so the
            // slider only appears when one of hers is actually on screen.
            if (state.rows.anyChargeScaled()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.chart_energy, state.energy.roundToInt()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(130.dp),
                    )
                    Slider(
                        value = state.energy,
                        onValueChange = viewModel::setEnergy,
                        valueRange = 0f..100f,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Every section folds independently: a chart is worth more screen than the
            // controls that produced it, and which control you still need varies.
            CollapsibleSection(
                title = stringResource(R.string.chart_section_sort),
                summary = stringResource(state.sortOrder.labelRes),
                expanded = sections.sort,
                onExpandedChange = { onSectionsChange(sections.copy(sort = it)) },
            ) {
                SortOrder.entries.forEach { order ->
                    Chip(
                        label = stringResource(order.labelRes),
                        selected = state.sortOrder == order,
                        onClick = { viewModel.setSortOrder(order) },
                    )
                }
            }

            val activeModifiers = ModifierToggles.count { it.isOn(state.modifiers) }
            CollapsibleSection(
                title = stringResource(R.string.chart_section_modifiers),
                summary = if (activeModifiers > 0) stringResource(R.string.chart_summary_active, activeModifiers) else null,
                expanded = sections.modifiers,
                onExpandedChange = { onSectionsChange(sections.copy(modifiers = it)) },
            ) {
                ModifierToggles.forEach { toggle ->
                    Chip(
                        label = stringResource(toggle.labelRes),
                        selected = toggle.isOn(state.modifiers),
                        onClick = {
                            viewModel.setModifiers(
                                toggle.set(state.modifiers, !toggle.isOn(state.modifiers)),
                            )
                        },
                    )
                }
            }

            // Role and weapon type are two independent axes. Run together in one row they
            // read as a single list of eight related options, which is how a filter gets
            // misread - so they get a heading each.
            val hiddenRoles = HeroRole.entries.size - state.roles.size
            CollapsibleSection(
                title = stringResource(R.string.chart_section_role),
                summary = if (hiddenRoles > 0) stringResource(R.string.chart_summary_hidden, hiddenRoles) else null,
                expanded = sections.roles,
                onExpandedChange = { onSectionsChange(sections.copy(roles = it)) },
            ) {
                HeroRole.entries.forEach { role ->
                    Chip(
                        label = stringResource(role.labelRes),
                        selected = role in state.roles,
                        onClick = { viewModel.setRoles(roleTaps.onTap(role, state.roles, HeroRole.entries.toSet())) },
                    )
                }
            }

            // Next to the weapon-type filter because they answer the same shape of
            // question: this one is how a reader compares Ashe scoped against Ashe not.
            val hiddenModes = FireMode.entries.size - state.fireModes.size
            CollapsibleSection(
                title = stringResource(R.string.chart_section_fire_mode),
                summary = if (hiddenModes > 0) {
                    stringResource(R.string.chart_summary_hidden, hiddenModes)
                } else {
                    null
                },
                expanded = sections.fireModes,
                onExpandedChange = { onSectionsChange(sections.copy(fireModes = it)) },
            ) {
                FireMode.entries.forEach { mode ->
                    Chip(
                        label = stringResource(mode.labelRes),
                        selected = mode in state.fireModes,
                        onClick = { viewModel.setFireModes(modeTaps.onTap(mode, state.fireModes, FireMode.entries.toSet())) },
                    )
                }
            }

            val hiddenTypes = WeaponCategory.entries.size - state.categories.size
            CollapsibleSection(
                title = stringResource(R.string.chart_section_weapon_type),
                summary = if (hiddenTypes > 0) stringResource(R.string.chart_summary_hidden, hiddenTypes) else null,
                expanded = sections.categories,
                onExpandedChange = { onSectionsChange(sections.copy(categories = it)) },
            ) {
                WeaponCategory.entries.forEach { category ->
                    Chip(
                        label = stringResource(category.labelRes),
                        selected = category in state.categories,
                        onClick = { viewModel.setCategories(typeTaps.onTap(category, state.categories, WeaponCategory.entries.toSet())) },
                    )
                }
            }

            // Perked weapons appear as their own rows, named after the perk. Saying which
            // ones are here matters as much as drawing them: a chart that quietly modelled
            // half the perks would be less trustworthy than one that modelled none.
            Text(
                text = stringResource(R.string.chart_perks_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

/**
 * What is left of the controls when they are folded away, so the chart gets the screen.
 *
 * The distance slider stays: sweeping it is the one thing worth doing *while* watching the
 * rows reorder, and losing it would make the collapsed state useless rather than compact.
 */
@Composable
private fun CollapsedControlBar(
    state: ChartUiState,
    viewModel: ChartViewModel,
    onExpand: () -> Unit,
) {
    val activeModifiers = ModifierToggles.count { it.isOn(state.modifiers) }

    Surface(tonalElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${state.distance.roundToInt()} m",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.width(44.dp),
            )
            Slider(
                value = state.distance,
                onValueChange = viewModel::setDistance,
                valueRange = 0f..60f,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(state.sortOrder.labelRes) +
                    if (activeModifiers > 0) " · $activeModifiers" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp),
            )
            IconButton(onClick = onExpand) {
                Icon(
                    imageVector = Icons.Filled.UnfoldMore,
                    contentDescription = stringResource(R.string.chart_expand),
                )
            }
        }
    }
}

/**
 * A group of chips under a heading that folds away.
 *
 * The heading carries a summary of what is set inside, so a folded section never hides
 * something that is quietly changing the numbers.
 */
@Composable
private fun CollapsibleSection(
    title: String,
    summary: String?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable FlowRowScope.() -> Unit,
) {
    TextButton(
        onClick = { onExpandedChange(!expanded) },
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
    ) {
        Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = 4.dp),
        )
        if (summary != null) {
            Text(
                text = "  ·  $summary",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (expanded) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy((-4).dp),
            content = content,
        )
    }
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
            text = stringResource(R.string.chart_time_axis),
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
