package com.bellizia.owcompanion.ui.common

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

// A daylight range rather than the other one's phosphor screen: two hidden corners of the
// same app should not look like the same room.
private val SKY_TOP = Color(0xFF2C6EA8)
private val SKY_LOW = Color(0xFF8FC5E8)
private val GRASS = Color(0xFF3F7A34)
private val BOARD = Color(0xFF2A2E38)
private val TARGET = Color(0xFF1D2029)
private val TARGET_EDGE = Color(0xFFE8A33D)
private val FRIEND = Color(0xFF2F6FBF)
private val HIT = Color(0xFFFFF3C4)
private val INK = Color(0xFF10131A)

/**
 * The range itself.
 *
 * Tap the silhouettes before they drop. Blue ones are your own side and cost you, which is
 * the only rule worth learning and the only reason the game is not just reflexes.
 *
 * The shapes are drawn rather than stored: a handful of primitives per silhouette costs
 * nothing, keeps this corner of the app free of art nobody else needs, and means the whole
 * thing weighs a few kilobytes.
 */
@Composable
internal fun RangePanel(shooter: RangeModel.Shooter, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val model = remember(shooter) { RangeModel(seed = System.nanoTime(), shooter = shooter) }
    val sounds = remember { RangeSounds(context) }
    DisposableEffect(Unit) { onDispose { sounds.release() } }

    var frame by remember { mutableIntStateOf(0) }
    var over by remember { mutableStateOf(false) }
    var splash by remember { mutableStateOf<Offset?>(null) }

    LaunchedEffect(Unit) {
        var previous = 0L
        while (model.stage != RangeModel.Stage.Finished) {
            withFrameNanos { now ->
                val dt = if (previous == 0L) 0f else ((now - previous) / 1_000_000_000f).coerceIn(0f, 0.05f)
                previous = now
                model.step(dt)
                frame += 1
            }
        }
        over = true
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(INK).padding(6.dp)) {
            Column(modifier = Modifier.fillMaxSize()) {
                Readout(model)
                Box(modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 6.dp)) {
                    Range(
                        model = model,
                        frame = frame,
                        splash = splash,
                        onShoot = { x, y ->
                            val hit = model.shoot(x, y)
                            splash = if (hit == null) Offset(x, y) else null
                            when {
                                hit == null -> sounds.miss()
                                hit.friendly -> sounds.wrong()
                                else -> sounds.hit()
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            if (over) {
                Ledger(context = context, model = model, onDone = onDismiss)
            }
        }
    }
}

@Composable
private fun Readout(model: RangeModel) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Mono("SCORE", 11, TARGET_EDGE)
            Mono("%07d".format(model.score), 20, HIT)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Mono("STREAK", 11, TARGET_EDGE)
            Mono(
                if (model.multiplier > 1) "${model.streak}  x${model.multiplier}" else "${model.streak}",
                18,
                if (model.multiplier > 1) TARGET_EDGE else HIT,
            )
        }
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Mono("TIME", 11, TARGET_EDGE)
            Mono("%02d".format(model.remaining.toInt()), 20, HIT)
        }
    }
}

@Composable
private fun Range(
    model: RangeModel,
    frame: Int,
    splash: Offset?,
    onShoot: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures { tap ->
                onShoot(tap.x / size.width, tap.y / size.height)
            }
        },
    ) {
        val w = size.width
        val h = size.height

        // Sky, ground, and a row of boards that changes as the round moves on. The stand
        // is scenery: it never affects where anything appears.
        drawRect(SKY_TOP, size = Size(w, h))
        drawRect(SKY_LOW, topLeft = Offset(0f, h * 0.30f), size = Size(w, h * 0.55f))
        drawRect(GRASS, topLeft = Offset(0f, h * 0.85f), size = Size(w, h * 0.15f))

        val stand = model.stand
        repeat(7) { index ->
            val bw = w / 9f
            val bh = h * (0.10f + ((index + stand) % 4) * 0.045f)
            drawRect(
                color = BOARD.copy(alpha = 0.35f),
                topLeft = Offset(w * 0.04f + index * (w * 0.13f), h * 0.85f - bh),
                size = Size(bw, bh),
            )
        }

        model.marks.forEach { mark ->
            val cx = mark.x * w
            val cy = mark.y * h
            val r = mark.radius * minOf(w, h) * 1.6f
            val rising = (mark.age / 0.18f).coerceIn(0f, 1f)
            val body = if (mark.struck) HIT else if (mark.friendly) FRIEND else TARGET
            val edge = if (mark.friendly) HIT else TARGET_EDGE

            // Rising out of the boards rather than blinking in, so the eye can follow it.
            val lift = (1f - rising) * r
            silhouette(cx, cy + lift, r, mark.shape, body, edge, mark.struck)
        }

        splash?.let { miss ->
            drawCircle(
                color = HIT.copy(alpha = 0.5f),
                radius = minOf(w, h) * 0.02f,
                center = Offset(miss.x * w, miss.y * h),
                style = Stroke(width = 3f),
            )
        }
    }
}

/**
 * One silhouette, built from primitives.
 *
 * Six outlines, distinguished by the shape on top rather than by any likeness: a visor, a
 * hat, a horn. Recognisable enough to be worth shooting, generic enough to be nobody.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.silhouette(
    cx: Float,
    cy: Float,
    r: Float,
    shape: Int,
    body: Color,
    edge: Color,
    struck: Boolean,
) {
    // Shoulders.
    drawRect(body, topLeft = Offset(cx - r * 0.8f, cy), size = Size(r * 1.6f, r * 1.1f))
    // Head.
    drawCircle(body, radius = r * 0.52f, center = Offset(cx, cy - r * 0.25f))

    when (shape % RangeModel.SHAPES) {
        0 -> drawRect(edge, Offset(cx - r * 0.55f, cy - r * 0.36f), Size(r * 1.1f, r * 0.17f))
        1 -> drawRect(edge, Offset(cx - r * 0.85f, cy - r * 0.62f), Size(r * 1.7f, r * 0.14f))
        2 -> drawCircle(edge, radius = r * 0.18f, center = Offset(cx + r * 0.22f, cy - r * 0.3f))
        3 -> drawRect(edge, Offset(cx - r * 0.1f, cy - r * 1.05f), Size(r * 0.2f, r * 0.45f))
        4 -> drawRect(edge, Offset(cx - r * 0.62f, cy - r * 0.95f), Size(r * 0.2f, r * 0.4f))
        else -> drawCircle(edge, radius = r * 0.5f, center = Offset(cx, cy - r * 0.25f), style = Stroke(3f))
    }

    if (struck) {
        drawCircle(HIT.copy(alpha = 0.6f), radius = r * 1.1f, center = Offset(cx, cy), style = Stroke(4f))
    }
}

@Composable
private fun Ledger(context: Context, model: RangeModel, onDone: () -> Unit) {
    val store = remember { context.getSharedPreferences(STORE, Context.MODE_PRIVATE) }
    val key = "best_${model.shooter.name}"
    val previous = remember { store.getInt(key, 0) }
    val beaten = model.score > previous
    LaunchedEffect(Unit) { if (beaten) store.edit().putInt(key, model.score).apply() }

    AlertDialog(
        onDismissRequest = onDone,
        confirmButton = { TextButton(onClick = onDone) { Mono("DONE", 14, TARGET_EDGE) } },
        title = { Mono("ROUND OVER", 18, INK) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Mono("SCORE   %07d".format(model.score), 14, INK)
                Mono("BEST STREAK   %02d".format(model.best), 13, INK)
                Mono("DROPPED   %02d".format(model.missed), 13, INK)
                Mono(
                    if (beaten) "A NEW BEST" else "BEST   %07d".format(previous),
                    13,
                    if (beaten) TARGET_EDGE else INK,
                )
            }
        },
    )
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
    )
}

/**
 * The noises.
 *
 * Synthesised rather than shipped: a handful of tones from the system generator needs no
 * asset, no licence and no file anyone has to notice.
 */
internal class RangeSounds(context: Context) {
    private val tone = runCatching {
        ToneGenerator(AudioManager.STREAM_MUSIC, 70)
    }.getOrNull()

    fun hit() = play(ToneGenerator.TONE_PROP_BEEP, 90)
    fun miss() = play(ToneGenerator.TONE_PROP_NACK, 70)
    fun wrong() = play(ToneGenerator.TONE_CDMA_ABBR_ALERT, 220)

    /** The one the search field plays: two notes, roughly a complaint. */
    fun quack() {
        play(ToneGenerator.TONE_CDMA_PIP, 90)
    }

    private fun play(type: Int, millis: Int) {
        runCatching { tone?.startTone(type, millis) }
    }

    fun release() {
        runCatching { tone?.release() }
    }

    private companion object {
        const val STORE = "range"
    }
}

private const val STORE = "range"
