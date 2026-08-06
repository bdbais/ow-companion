package com.bellizia.owcompanion.ui.wiki

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bellizia.owcompanion.data.model.HeroWiki
import com.bellizia.owcompanion.data.model.PatchEntry
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** A single stat's recorded values over time. */
data class StatSeries(
    val stat: String,
    /** Chronological, oldest first. */
    val points: List<StatPoint>,
) {
    val changes: Int get() = points.size - 1
    val first: Double get() = points.first().value
    val last: Double get() = points.last().value
}

data class StatPoint(val date: LocalDate, val value: Double)

private fun parseDate(text: String): LocalDate? = try {
    LocalDate.parse(text)
} catch (_: DateTimeParseException) {
    null
}

// "Damage reduction" and "damage taken" are defence, not output; plotting them next to
// weapon damage would make a hero look buffed when they were made tankier. Multipliers and
// percentages are ratios - a critical multiplier of 2 drawn on an axis that reaches 200
// is a flat line along the bottom that tells nobody anything.
private val NOT_DAMAGE_OUTPUT = listOf(
    "reduction", "taken", "resistance", "mitigat", "multiplier", "percent", "%",
)

/**
 * Every damage figure the patch notes ever moved, as a series per stat.
 *
 * These are the values the notes actually state, not a reconstruction: each change says
 * "from A to B", so a series is the chain of those readings with the earliest `from` as its
 * starting point. Stats the notes never touched are simply absent - the app cannot know a
 * number that was never written down.
 */
fun damageSeries(hero: HeroWiki): List<StatSeries> {
    val grouped = mutableMapOf<String, MutableList<Pair<LocalDate, Pair<Double, Double>>>>()

    for (patch in hero.patches) {
        val date = parseDate(patch.date) ?: continue
        for (stat in patch.stats) {
            val name = stat.stat.lowercase()
            if (!name.contains("damage")) continue
            if (NOT_DAMAGE_OUTPUT.any { name.contains(it) }) continue
            if (stat.unit == "%" || stat.unit == "x") continue
            grouped.getOrPut(stat.stat) { mutableListOf() }
                .add(date to (stat.from to stat.to))
        }
    }

    return grouped
        .mapNotNull { (stat, entries) ->
            val sorted = entries.sortedBy { it.first }
            if (sorted.isEmpty()) return@mapNotNull null
            val points = buildList {
                add(StatPoint(sorted.first().first, sorted.first().second.first))
                sorted.forEach { (date, change) -> add(StatPoint(date, change.second)) }
            }
            StatSeries(stat, points)
        }
        .filter { it.changes >= 2 }
        .sortedByDescending { it.changes }
}

/**
 * How a hero's damage numbers moved over the years, and every balance note behind them.
 *
 * [abilityFilter] narrows the history to one ability. Ten years of notes for a hero like
 * Roadhog runs to nearly two hundred lines, and usually the question is about one gun or
 * one cooldown rather than about all of them.
 */
fun LazyListScope.patchTimeline(
    hero: HeroWiki,
    color: Color,
    abilityFilter: String?,
    onAbilityFilterChange: (String?) -> Unit,
) {
    val series = damageSeries(hero)
    val abilities = hero.patches
        .flatMap { patch -> patch.changes.map { it.ability } }
        .filter { it.isNotBlank() }
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .map { it.key }

    val patches = if (abilityFilter == null) {
        hero.patches
    } else {
        hero.patches
            .map { patch ->
                patch.copy(changes = patch.changes.filter { it.ability == abilityFilter })
            }
            .filter { it.changes.isNotEmpty() }
    }

    item {
        Text(
            text = "Damage over time",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 12.dp, top = 16.dp, bottom = 2.dp),
        )
    }

    if (series.isEmpty()) {
        item {
            Text(
                text = "The patch notes never restated a damage value for this hero more " +
                    "than once, so there is nothing to plot.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    } else {
        item { DamageChart(series = series.take(3), color = color) }
    }

    item {
        Column {
            Text(
                text = "Balance history",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 12.dp, top = 16.dp, bottom = 4.dp),
            )
            AbilityFilterRow(
                abilities = abilities,
                selected = abilityFilter,
                color = color,
                onSelect = onAbilityFilterChange,
            )
        }
    }

    // Keyed by position: a hero can have two entries on the same date, one per game mode.
    itemsIndexed(patches, key = { index, patch -> "${patch.date}-${patch.mode}-$index" }) {
        _, patch ->
        PatchCard(patch = patch, color = color)
    }

    if (patches.isEmpty()) {
        item {
            Text(
                text = "No recorded changes for this ability.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

@Composable
private fun AbilityFilterRow(
    abilities: List<String>,
    selected: String?,
    color: Color,
    onSelect: (String?) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            AbilityChip("All", selected == null, color) { onSelect(null) }
        }
        items(abilities) { ability ->
            AbilityChip(ability, selected == ability, color) {
                onSelect(if (selected == ability) null else ability)
            }
        }
    }
}

@Composable
private fun AbilityChip(
    label: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = if (selected) MaterialTheme.colorScheme.onPrimary else color,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary else color.copy(alpha = 0.14f),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun DamageChart(series: List<StatSeries>, color: Color) {
    val textMeasurer = rememberTextMeasurer()
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
    val labelStyle = TextStyle(
        fontSize = 9.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    val allPoints = series.flatMap { it.points }
    val minDay = allPoints.minOf { it.date.toEpochDay() }
    val maxDay = allPoints.maxOf { it.date.toEpochDay() }
    val minValue = 0.0
    val maxValue = allPoints.maxOf { it.value }.coerceAtLeast(1.0)
    val daySpan = (maxDay - minDay).coerceAtLeast(1)

    Column(modifier = Modifier.padding(horizontal = 12.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .padding(top = 6.dp),
        ) {
            val left = 34.dp.toPx()
            val bottom = size.height - 16.dp.toPx()
            val plotWidth = size.width - left
            val plotHeight = bottom

            fun x(date: LocalDate) =
                left + (date.toEpochDay() - minDay).toFloat() / daySpan * plotWidth

            fun y(value: Double) =
                (bottom - (value - minValue) / (maxValue - minValue) * plotHeight).toFloat()

            // Horizontal guides at 0, half and full scale.
            listOf(0.0, maxValue / 2, maxValue).forEach { value ->
                val yy = y(value)
                drawLine(gridColor, Offset(left, yy), Offset(size.width, yy), 1.dp.toPx())
                val label = textMeasurer.measure("%.0f".format(value), labelStyle)
                drawText(label, topLeft = Offset(0f, yy - label.size.height / 2f))
            }

            series.forEachIndexed { index, entry ->
                // A balance change takes effect on its patch day and holds until the next
                // one, so the line steps rather than slopes.
                val path = Path()
                entry.points.forEachIndexed { pointIndex, point ->
                    val px = x(point.date)
                    val py = y(point.value)
                    if (pointIndex == 0) {
                        path.moveTo(px, py)
                    } else {
                        path.lineTo(px, y(entry.points[pointIndex - 1].value))
                        path.lineTo(px, py)
                    }
                }
                // Hold the latest value out to today.
                path.lineTo(size.width, y(entry.points.last().value))

                drawPath(
                    path = path,
                    color = color.copy(alpha = 1f - index * 0.3f),
                    style = Stroke(width = (2.5f - index * 0.6f).dp.toPx()),
                )
                entry.points.forEach { point ->
                    drawCircle(
                        color = color.copy(alpha = 1f - index * 0.3f),
                        radius = 2.5.dp.toPx(),
                        center = Offset(x(point.date), y(point.value)),
                    )
                }
            }

            val startLabel = textMeasurer.measure(
                LocalDate.ofEpochDay(minDay).year.toString(),
                labelStyle,
            )
            val endLabel = textMeasurer.measure(
                LocalDate.ofEpochDay(maxDay).year.toString(),
                labelStyle,
            )
            drawText(startLabel, topLeft = Offset(left, bottom + 2.dp.toPx()))
            drawText(
                endLabel,
                topLeft = Offset(size.width - endLabel.size.width, bottom + 2.dp.toPx()),
            )
        }

        series.forEachIndexed { index, entry ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 14.dp, height = 3.dp)
                        .background(color.copy(alpha = 1f - index * 0.3f)),
                )
                Text(
                    text = "${entry.stat}: ${trim(entry.first)} → ${trim(entry.last)}" +
                        "  (${entry.changes} changes)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun PatchCard(patch: PatchEntry, color: Color) {
    var expanded by remember { mutableStateOf(false) }
    val buffs = patch.stats.count { it.direction == "buff" }
    val nerfs = patch.stats.count { it.direction == "nerf" }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .clickable { expanded = !expanded }
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = patch.date,
                style = MaterialTheme.typography.labelLarge,
                color = color,
            )
            if (patch.mode != "owpvp") {
                Badge(text = patch.mode.removePrefix("ow"), color = MaterialTheme.colorScheme.outline)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                if (buffs > 0) Badge("$buffs buff", BuffGreen)
                if (nerfs > 0) Badge("$nerfs nerf", NerfRed)
            }
        }

        val shown = if (expanded) patch.changes else patch.changes.take(2)
        shown.forEach { change ->
            Text(
                text = change.ability,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(text = change.text, style = MaterialTheme.typography.bodySmall)
        }
        if (!expanded && patch.changes.size > 2) {
            Text(
                text = "+${patch.changes.size - 2} more",
                style = MaterialTheme.typography.labelSmall,
                color = color,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (expanded) {
            patch.comments.forEach { comment ->
                Text(
                    text = comment,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .padding(start = 6.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

private val BuffGreen = Color(0xFF7BC96F)
private val NerfRed = Color(0xFFE0645C)

private fun trim(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else "%.2f".format(value)
