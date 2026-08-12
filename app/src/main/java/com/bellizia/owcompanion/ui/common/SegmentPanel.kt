package com.bellizia.owcompanion.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.atan2
import kotlin.math.hypot

private const val LIMIT_SECONDS = 30

internal const val SEGMENTS = 8
private const val PIECES = SEGMENTS + 1
internal const val HUB = SEGMENTS

private const val INNER = 0.26f
private const val OUTER = 0.46f
private const val HUB_R = 0.16f

private val TINTS = listOf(
    Color(0xFFF99E1A), Color(0xFF43484C), Color(0xFF218FFE), Color(0xFF7DBA3F),
    Color(0xFFE61B23), Color(0xFF9B59B6), Color(0xFF00C4B4), Color(0xFFFFD166),
    Color(0xFFEE6C4D),
)

private enum class Step { Filling, Complete, Expired }

@Composable
internal fun SegmentPanel(onDismiss: () -> Unit) {
    val filled = remember { mutableStateListOf<Color?>().apply { repeat(PIECES) { add(null) } } }
    var step by remember { mutableStateOf(Step.Filling) }
    var remaining by remember { mutableIntStateOf(LIMIT_SECONDS) }

    LaunchedEffect(step) {
        if (step != Step.Filling) return@LaunchedEffect
        while (remaining > 0) {
            delay(1000)
            remaining -= 1
        }
        step = Step.Expired
    }

    if (step == Step.Complete) {
        FieldPanel(onDismiss = onDismiss)
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
        dismissButton = {
            if (step == Step.Expired) {
                TextButton(
                    onClick = {
                        filled.indices.forEach { filled[it] = null }
                        remaining = LIMIT_SECONDS
                        step = Step.Filling
                    },
                ) { Text("다시") }
            }
        },
        title = { Text("채워 넣기") },
        text = {
            Column {
                Text(
                    text = if (step == Step.Expired) {
                        "시간이 지났습니다. 조각은 그대로 있습니다."
                    } else {
                        "${LIMIT_SECONDS}초 안에 아홉 조각을 모두 누르세요."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LinearProgressIndicator(
                        progress = { remaining / LIMIT_SECONDS.toFloat() },
                        modifier = Modifier.weight(1f),
                    )
                    Text(text = "$remaining", style = MaterialTheme.typography.labelLarge)
                }

                Wheel(
                    filled = filled,
                    enabled = step == Step.Filling,
                    onFill = { piece ->
                        if (filled[piece] == null) {
                            filled[piece] = TINTS[piece % TINTS.size]
                            if (filled.all { it != null }) step = Step.Complete
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
        },
    )
}

@Composable
private fun Wheel(
    filled: List<Color?>,
    enabled: Boolean,
    onFill: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val outline = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { tap -> pieceAt(tap, size.width.toFloat())?.let(onFill) }
            },
    ) {
        val side = size.minDimension
        val middle = Offset(side / 2f, side / 2f)
        val band = side * (OUTER - INNER)
        val radius = side * INNER + band / 2f
        val stepAngle = 360f / SEGMENTS
        val sweep = stepAngle - 6f

        repeat(SEGMENTS) { index ->
            val start = index * stepAngle - 90f + 3f
            val topLeft = Offset(middle.x - radius, middle.y - radius)
            val box = Size(radius * 2, radius * 2)
            filled[index]?.let {
                drawArc(it, start, sweep, false, topLeft, box, style = Stroke(width = band))
            }
            drawArc(
                color = outline,
                startAngle = start,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = box,
                style = Stroke(width = band * 0.06f),
            )
        }

        filled[HUB]?.let { drawCircle(it, side * HUB_R, middle) }
        drawCircle(outline, side * HUB_R, middle, style = Stroke(width = side * 0.012f))
    }
}

/**
 * Which piece a tap landed on, in polar coordinates: the shape is a ring and a disc, so the
 * arithmetic is exact and needs no geometry library.
 */
internal fun pieceAt(tap: Offset, side: Float): Int? {
    val middle = side / 2f
    val dx = tap.x - middle
    val dy = tap.y - middle
    val distance = hypot(dx, dy)

    if (distance <= side * HUB_R) return HUB
    if (distance < side * INNER || distance > side * OUTER) return null

    // atan2 measures from the positive x axis and returns -180..180; the ring starts at
    // twelve o'clock, so rotate a quarter turn before bucketing.
    var degrees = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) + 90.0
    if (degrees < 0) degrees += 360.0
    return (degrees / (360.0 / SEGMENTS)).toInt() % SEGMENTS
}
