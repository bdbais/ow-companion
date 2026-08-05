package com.bellizia.owcompanion.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bellizia.owcompanion.sim.Simulator
import kotlin.math.max

/** Width of the fixed label column that stays put while the timeline scrolls. */
val LabelColumnWidth = 128.dp

/** Left gutter before t=0, in dp, shared by the rows and the time axis. */
internal const val ChartPaddingLeft = 8f

/** Minimum row height in dp, so short-lived weapons still get a legible strip. */
private const val MinRowHeight = 46f

/** Timeline width in dp at the given zoom. */
internal fun chartWidthDp(zoom: Float): Float =
    ChartPaddingLeft * 2 + Simulator.MAX_TIME.toFloat() * Simulator.TIMESCALE.toFloat() * zoom

internal fun timeToDp(time: Double, zoom: Float): Float =
    ChartPaddingLeft + (time * Simulator.TIMESCALE * zoom).toFloat()

fun parseHeroColor(hex: String?, fallback: Color): Color {
    if (hex == null || !hex.startsWith("#") || hex.length != 7) return fallback
    return runCatching {
        Color(
            red = hex.substring(1, 3).toInt(16) / 255f,
            green = hex.substring(3, 5).toInt(16) / 255f,
            blue = hex.substring(5, 7).toInt(16) / 255f,
        )
    }.getOrDefault(fallback)
}

/**
 * One weapon's firing sequence over time.
 *
 * Every shot is a rectangle whose **area** is the damage it deals, so a stream of chip
 * damage and one heavy hit take up the same amount of ink and can be compared at a glance.
 * Instantaneous shots are centred on the moment they land; beams and damage-over-time
 * effects stretch across the time they occupy.
 */
@Composable
fun ChartRowView(
    row: ChartRow,
    zoom: Float,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    val color = parseHeroColor(row.hero?.color, MaterialTheme.colorScheme.primary)
    val rowHeight = max(MinRowHeight, row.train.height.toFloat())

    Row(
        modifier = modifier.height((rowHeight + 4f).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .width(LabelColumnWidth)
                .padding(horizontal = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(color),
                )
                Text(
                    text = row.spec.hero,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            Text(
                text = row.spec.name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${row.train.dps.formatted(1)} dps",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = color,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
        ) {
            Canvas(
                modifier = Modifier
                    .width(chartWidthDp(zoom).dp)
                    .height(rowHeight.dp),
            ) {
                val midY = size.height / 2
                for (shot in row.train.shots) {
                    if (shot.damage <= 0.0 || shot.height <= 0.0) continue

                    // Zoom stretches the time axis; heights are left alone, so a shot's area
                    // is off by a constant factor when zoomed - the same one for every weapon.
                    val duration = shot.duration
                    val widthDp = if (duration != null) {
                        (duration * Simulator.TIMESCALE * zoom).toFloat()
                    } else {
                        shot.width.toFloat() * zoom
                    }
                    if (widthDp <= 0f) continue

                    val heightPx = shot.height.toFloat().dp.toPx()
                    val widthPx = widthDp.dp.toPx()
                    val centerX = timeToDp(shot.time, zoom).dp.toPx()
                    // A shot that occupies time starts when it lands; an instantaneous one
                    // is centred on that moment.
                    val left = if (duration != null) centerX else centerX - widthPx / 2

                    drawRect(
                        color = color,
                        topLeft = Offset(left, midY - heightPx / 2),
                        size = Size(widthPx, heightPx),
                    )
                }
            }
        }
    }
}

internal fun Double.formatted(decimals: Int): String =
    if (isFinite()) String.format("%.${decimals}f", this) else "∞"
