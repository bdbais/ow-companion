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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import com.bellizia.owcompanion.R
import com.bellizia.owcompanion.data.WikiRepository
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bellizia.owcompanion.sim.Simulator
import kotlin.math.max

/**
 * Width of the fixed label column that stays put while the timeline scrolls.
 *
 * Wide enough for the longest hero name - Wrecking Ball - alongside its damage figure.
 * Truncating to "Wrec..." to buy a little more timeline is a bad trade: the timeline
 * scrolls and can be zoomed, while a clipped name is simply gone.
 */
val LabelColumnWidth = 176.dp

/** Left gutter before t=0, in dp, shared by the rows and the time axis. */
internal const val ChartPaddingLeft = 8f

/** Minimum row height in dp, so short-lived weapons still get a legible strip. */
private const val MinRowHeight = 46f

/** A perked weapon needs a third line to say which perk, and three lines need the room. */
private const val PerkRowHeight = 60f

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
    distance: Float,
    modifier: Modifier = Modifier,
) {
    val heroColor = parseHeroColor(row.hero?.color, MaterialTheme.colorScheme.primary)
    val floor = if (row.spec.perk != null) PerkRowHeight else MinRowHeight
    val rowHeight = max(floor, row.train.height.toFloat())

    // A weapon that cannot reach the target draws nothing and reports zero, which reads as
    // missing data rather than as the answer. Quick melee at five metres is the case people
    // hit first. The row stays - taking it away would look like the weapon does not exist -
    // but goes grey and says why.
    val range = row.spec.damage.maxRange
    val outOfRange = range != null && distance > range
    val color = if (outOfRange) MaterialTheme.colorScheme.onSurfaceVariant else heroColor

    Row(
        modifier = modifier.height((rowHeight + 4f).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .width(LabelColumnWidth)
                .padding(horizontal = 8.dp),
        ) {
            // The rate shares the hero's line rather than taking one of its own: a perked
            // weapon needs a fourth line to say which perk, and the column is only tall
            // enough for three - so the dps was the one that fell off the bottom.
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The face rather than a coloured dot: at a glance a row is found by the
                // hero on it, and the colour is still carried by the bars and the figure.
                AsyncImage(
                    model = WikiRepository.imageUri(row.hero?.portrait),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = if (outOfRange) 0.4f else 1f,
                    modifier = Modifier.size(24.dp).clip(CircleShape).background(color),
                )
                Text(
                    text = row.spec.hero,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (outOfRange) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 6.dp),
                )
                Text(
                    text = if (outOfRange) {
                        stringResource(R.string.chart_out_of_range)
                    } else {
                        row.train.dps.formatted(1)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (outOfRange) FontWeight.Normal else FontWeight.Bold,
                    color = color,
                    maxLines = 1,
                )
            }
            Text(
                text = row.spec.baseWeapon ?: row.spec.name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // A perked weapon is true of some players and not others, and the label column
            // is too narrow to carry that inside the name without eliding it away.
            row.spec.perk?.let { perk ->
                Text(
                    text = stringResource(R.string.chart_with_perk, perk),
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
