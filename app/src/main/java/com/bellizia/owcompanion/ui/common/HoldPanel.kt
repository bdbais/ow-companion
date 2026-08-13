package com.bellizia.owcompanion.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

// Concrete and rust: a fortified position rather than a screen or a range.
private val GROUND = Color(0xFF23262B)
private val LANE = Color(0xFF3A3F47)
private val WALL = Color(0xFF14161A)
private val RUST = Color(0xFFE0762B)
private val STEEL = Color(0xFFB9C2CC)
private val WARN = Color(0xFFE04C4C)
private val COOL = Color(0xFF49B7D9)
private val PAPER = Color(0xFFF2ECE2)

/**
 * The defence.
 *
 * Pick an emplacement, tap somewhere off the lane, start the wave. Everything about it is
 * meant to be read at a glance: the lane is a road, the reach of a gun is a ring, and the
 * only number that matters is how many more can get past you.
 */
@Composable
internal fun HoldPanel(ground: Int, onDismiss: () -> Unit) {
    val model = remember(ground) { HoldModel(seed = System.nanoTime(), ground = ground) }
    var frame by remember { mutableIntStateOf(0) }
    var chosen by remember { mutableStateOf(HoldModel.Emplacement.Sentry) }

    LaunchedEffect(Unit) {
        var previous = 0L
        while (model.stage != HoldModel.Stage.Lost) {
            withFrameNanos { now ->
                val dt = if (previous == 0L) 0f else ((now - previous) / 1_000_000_000f).coerceIn(0f, 0.05f)
                previous = now
                model.step(dt)
                frame += 1
            }
        }
        frame += 1
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(WALL).padding(6.dp)) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Mono("SCRAP", 11, RUST)
                        Mono("%05d".format(model.scrap), 19, PAPER)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Mono("WAVE", 11, RUST)
                        Mono("%02d".format(model.wave), 19, PAPER)
                    }
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Mono("LINE", 11, RUST)
                        Mono("${model.integrity}", 19, if (model.integrity <= 2) WARN else PAPER)
                    }
                }

                Box(modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 6.dp)) {
                    Field(model, chosen, frame, Modifier.fillMaxSize())
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HoldModel.Emplacement.entries.forEach { kind ->
                        val affordable = model.scrap >= kind.cost
                        TextButton(onClick = { chosen = kind }) {
                            Mono(
                                "${kind.name.uppercase()} ${kind.cost}",
                                12,
                                when {
                                    kind == chosen -> RUST
                                    affordable -> PAPER
                                    else -> LANE
                                },
                            )
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onDismiss) { Mono("LEAVE", 13, STEEL) }
                    if (model.stage == HoldModel.Stage.Placing) {
                        TextButton(onClick = model::advance) { Mono("SEND THE WAVE", 14, RUST) }
                    }
                    if (model.stage == HoldModel.Stage.Lost) {
                        Mono("THE LINE BROKE  ·  %02d WAVES".format(model.wave), 14, WARN)
                    }
                }
            }
        }
    }
}

@Composable
private fun Field(
    model: HoldModel,
    chosen: HoldModel.Emplacement,
    frame: Int,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier.pointerInput(chosen) {
            detectTapGestures { tap ->
                model.place(chosen, tap.x / size.width, tap.y / size.height)
            }
        },
    ) {
        val w = size.width
        val h = size.height
        drawRect(GROUND, size = Size(w, h))

        // The lane, drawn thick so it plainly is one.
        val lane = model.path
        for (index in 0 until lane.size - 1) {
            val (ax, ay) = lane[index]
            val (bx, by) = lane[index + 1]
            drawLine(
                color = LANE,
                start = Offset(ax * w, ay * h),
                end = Offset(bx * w, by * h),
                strokeWidth = minOf(w, h) * 0.09f,
            )
        }
        // Where they come from, and where you cannot let them reach.
        drawCircle(COOL, radius = minOf(w, h) * 0.03f, center = Offset(lane.first().first * w, lane.first().second * h))
        drawCircle(WARN, radius = minOf(w, h) * 0.03f, center = Offset(lane.last().first * w, lane.last().second * h))

        model.guns.forEach { gun ->
            val cx = gun.x * w
            val cy = gun.y * h
            val tint = when (gun.kind) {
                HoldModel.Emplacement.Sentry -> RUST
                HoldModel.Emplacement.Emplaced -> STEEL
                HoldModel.Emplacement.Field -> COOL
            }
            drawCircle(
                color = tint.copy(alpha = 0.16f),
                radius = gun.kind.reach * minOf(w, h),
                center = Offset(cx, cy),
                style = Stroke(width = 1.5f),
            )
            val side = minOf(w, h) * 0.028f
            drawRect(tint, topLeft = Offset(cx - side, cy - side), size = Size(side * 2, side * 2))
            if (gun.flash > 0f) {
                drawCircle(PAPER, radius = side * 1.5f, center = Offset(cx, cy), style = Stroke(2f))
            }
        }

        model.walkers.forEach { walker ->
            val cx = walker.x * w
            val cy = walker.y * h
            val r = minOf(w, h) * (if (walker.heavy) 0.028f else 0.018f)
            val body = when {
                walker.flash > 0f -> PAPER
                walker.slowed > 0f -> COOL
                walker.heavy -> WARN
                else -> STEEL
            }
            drawCircle(body, radius = r, center = Offset(cx, cy))
            // A bar only while it is hurt, so a full field is not all bars.
            if (walker.health < walker.maxHealth) {
                val width = r * 2.4f
                drawRect(WALL, topLeft = Offset(cx - width / 2, cy - r * 2f), size = Size(width, r * 0.4f))
                drawRect(
                    color = RUST,
                    topLeft = Offset(cx - width / 2, cy - r * 2f),
                    size = Size(width * (walker.health / walker.maxHealth), r * 0.4f),
                )
            }
        }
    }
}

@Composable
private fun Mono(text: String, size: Int, tint: Color) {
    Text(
        text = text,
        color = tint,
        fontSize = size.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 1.sp,
        style = MaterialTheme.typography.labelLarge,
    )
}
