package com.bellizia.owcompanion.ui.common

import android.content.Context
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private val BACKDROP = Color(0xFF0B0F1A)
private val GRID = Color(0xFF16203A)
private val OWN_TINT = Color(0xFFFF6FA5)
private val OWN_TRIM = Color(0xFF7FDBFF)
private val MARKER_TINT = Color(0xFF8C9BB5)
private val HEAVY_TINT = Color(0xFFE8734A)
private val PRIMARY_TINT = Color(0xFFFFE066)
private val SECONDARY_TINT = Color(0xFF7FDBFF)
private val INCOMING_TINT = Color(0xFFFF5C5C)

@Composable
internal fun FieldPanel(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val model = remember { FieldModel(seed = System.nanoTime()) }

    var dx by remember { mutableStateOf(0f) }
    var dy by remember { mutableStateOf(0f) }
    var primary by remember { mutableStateOf(false) }
    var secondary by remember { mutableStateOf(false) }
    var charging by remember { mutableStateOf(false) }
    var frame by remember { mutableIntStateOf(0) }
    var entering by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        var previous = 0L
        while (model.stage != Stage.Finished) {
            withFrameNanos { now ->
                // Capped, so a stall cannot teleport everything through everything else.
                val dt = if (previous == 0L) {
                    0f
                } else {
                    ((now - previous) / 1_000_000_000f).coerceIn(0f, 0.05f)
                }
                previous = now
                model.step(Controls(dx, dy, primary, secondary, charging), dt)
                // One-shot buttons: holding them down must not fire every frame.
                secondary = false
                charging = false
                frame += 1
            }
        }
        entering = true
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BACKDROP)
                .padding(8.dp),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Readout(model = model, frame = frame)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 6.dp),
                ) {
                    Playfield(model = model, frame = frame, modifier = Modifier.fillMaxSize())
                }

                Pad(
                    onMove = { x, y -> dx = x; dy = y },
                    onPrimary = { primary = it },
                    onSecondary = { secondary = true },
                    onCharge = { charging = true },
                    model = model,
                    frame = frame,
                )
            }

            if (entering) {
                Ledger(
                    context = context,
                    score = model.tally,
                    tier = model.tier,
                    onDone = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun Readout(model: FieldModel, frame: Int) {
    @Suppress("UNUSED_EXPRESSION") frame
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Mono("SCORE", 11)
            Mono("%07d".format(model.tally), 20)
        }
        model.heavyLabel?.let {
            Mono(it, 12, tint = HEAVY_TINT, modifier = Modifier.padding(horizontal = 6.dp))
        }
        Column(horizontalAlignment = Alignment.End) {
            Mono("LEVEL", 11)
            Mono("%02d".format(model.tier), 20)
        }
    }
}

@Composable
private fun Mono(
    text: String,
    size: Int,
    tint: Color = Color(0xFFCFE3FF),
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = tint,
        fontSize = size.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 1.sp,
        modifier = modifier,
    )
}

@Composable
private fun Playfield(model: FieldModel, frame: Int, modifier: Modifier = Modifier) {
    @Suppress("UNUSED_EXPRESSION") frame
    Canvas(modifier = modifier) {
        val scale = minOf(size.width / FIELD_W, size.height / FIELD_H)
        val originX = (size.width - FIELD_W * scale) / 2f
        val originY = (size.height - FIELD_H * scale) / 2f
        fun px(x: Float) = originX + x * scale
        fun py(y: Float) = originY + y * scale

        drawRect(
            color = GRID.copy(alpha = 0.35f),
            topLeft = Offset(px(0f), py(0f)),
            size = Size(FIELD_W * scale, FIELD_H * scale),
        )
        // A scrolling starfield, cheap enough to compute rather than store.
        for (row in 0..24) {
            val y = (row * 7f + (frame * 0.9f) % 7f) % FIELD_H
            drawRect(
                color = GRID,
                topLeft = Offset(px((row * 37f) % FIELD_W), py(y)),
                size = Size(scale, scale),
            )
        }

        for (ripple in model.ripples) {
            val grow = ripple.age / 0.45f
            drawCircle(
                color = Color(0xFFFFC857).copy(alpha = (1f - grow).coerceIn(0f, 1f)),
                radius = ripple.radius * grow * scale,
                center = Offset(px(ripple.x), py(ripple.y)),
            )
        }

        for (marker in model.markers) {
            val tint = if (marker.heavy) HEAVY_TINT else MARKER_TINT
            val shade = if (marker.flash > 0f) Color.White else tint
            val side = marker.radius * scale
            drawRect(
                color = shade,
                topLeft = Offset(px(marker.x) - side, py(marker.y) - side * 0.7f),
                size = Size(side * 2, side * 1.4f),
            )
            drawRect(
                color = shade.copy(alpha = 0.75f),
                topLeft = Offset(px(marker.x) - side * 0.5f, py(marker.y) + side * 0.7f),
                size = Size(side, side * 0.5f),
            )
            if (marker.heavy) {
                val width = side * 2
                drawRect(
                    color = Color(0xFF3A2A2A),
                    topLeft = Offset(px(marker.x) - side, py(marker.y) - side * 1.5f),
                    size = Size(width, scale * 1.6f),
                )
                drawRect(
                    color = Color(0xFFFF4D4D),
                    topLeft = Offset(px(marker.x) - side, py(marker.y) - side * 1.5f),
                    size = Size(width * marker.health / marker.maxHealth.toFloat(), scale * 1.6f),
                )
            }
        }

        for (mote in model.motes) {
            val tint = when (mote.kind) {
                Trace.Primary -> PRIMARY_TINT
                Trace.Secondary -> SECONDARY_TINT
                Trace.Incoming -> INCOMING_TINT
            }
            drawRect(
                color = tint,
                topLeft = Offset(px(mote.x) - mote.radius * scale, py(mote.y) - mote.radius * scale * 1.6f),
                size = Size(mote.radius * 2 * scale, mote.radius * 3.2f * scale),
            )
        }

        if (!model.flickering()) {
            val x = px(model.ownX)
            val y = py(model.ownY)
            val unit = scale * 1.4f
            drawRect(OWN_TINT, Offset(x - unit * 3, y - unit), Size(unit * 6, unit * 3))
            drawRect(OWN_TRIM, Offset(x - unit, y - unit * 3), Size(unit * 2, unit * 2))
            drawRect(OWN_TINT, Offset(x - unit * 4.5f, y - unit * 0.5f), Size(unit * 1.5f, unit * 3))
            drawRect(OWN_TINT, Offset(x + unit * 3, y - unit * 0.5f), Size(unit * 1.5f, unit * 3))
        }
    }
}

@Composable
private fun Pad(
    onMove: (Float, Float) -> Unit,
    onPrimary: (Boolean) -> Unit,
    onSecondary: () -> Unit,
    onCharge: () -> Unit,
    model: FieldModel,
    frame: Int,
) {
    @Suppress("UNUSED_EXPRESSION") frame
    Row(
        modifier = Modifier.fillMaxWidth().height(150.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Three buttons on the left.
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            HoldButton("FIRE", Color(0xFFFFE066), onPress = onPrimary)
            TapButton(
                label = "MISSILES",
                tint = Color(0xFF7FDBFF),
                ready = model.secondaryReady,
                onTap = onSecondary,
            )
            TapButton(
                label = "ULTIMATE",
                tint = Color(0xFFB388FF),
                ready = model.charge,
                onTap = onCharge,
            )
        }

        // Arrows on the right.
        Box(
            modifier = Modifier.weight(1f).aspectRatio(1f),
            contentAlignment = Alignment.Center,
        ) {
            Stick(onMove = onMove)
        }
    }
}

@Composable
private fun HoldButton(label: String, tint: Color, onPress: (Boolean) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(tint.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onPress(true)
                        tryAwaitRelease()
                        onPress(false)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Mono(label, 14, tint = tint)
    }
}

@Composable
private fun TapButton(label: String, tint: Color, ready: Float, onTap: () -> Unit) {
    val armed = ready >= 1f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(tint.copy(alpha = if (armed) 0.28f else 0.08f), RoundedCornerShape(8.dp))
            .pointerInput(armed) {
                detectTapGestures { if (armed) onTap() }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                color = tint.copy(alpha = 0.22f),
                size = Size(size.width * ready.coerceIn(0f, 1f), size.height),
            )
        }
        Mono(label, 13, tint = if (armed) tint else tint.copy(alpha = 0.5f))
    }
}

/**
 * A four-way stick: the touch offset from the centre gives the direction, which is steadier
 * under a thumb than four separate keys.
 */
@Composable
private fun Stick(onMove: (Float, Float) -> Unit) {
    var knob by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1B2740), CircleShape)
            .pointerInput(Unit) {
                val half = size.width / 2f
                detectDragGestures(
                    onDragStart = { start ->
                        knob = start - Offset(half, half)
                        onMove(knob.x, knob.y)
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        knob += amount
                        onMove(knob.x, knob.y)
                    },
                    onDragEnd = {
                        knob = Offset.Zero
                        onMove(0f, 0f)
                    },
                    onDragCancel = {
                        knob = Offset.Zero
                        onMove(0f, 0f)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val middle = Offset(size.width / 2f, size.height / 2f)
            val reach = size.minDimension * 0.28f
            val length = kotlin.math.hypot(knob.x, knob.y).coerceAtLeast(1f)
            val clamped = if (length > reach) Offset(knob.x / length * reach, knob.y / length * reach) else knob
            drawCircle(Color(0xFF2C3E63), radius = size.minDimension * 0.18f, center = middle + clamped)
            listOf(
                Offset(0f, -1f), Offset(0f, 1f), Offset(-1f, 0f), Offset(1f, 0f),
            ).forEach { direction ->
                drawCircle(
                    color = Color(0xFF44598C),
                    radius = size.minDimension * 0.035f,
                    center = middle + Offset(direction.x * reach * 1.5f, direction.y * reach * 1.5f),
                )
            }
        }
    }
}

/** The three-letter table, kept in preferences. */
@Composable
private fun Ledger(context: Context, score: Int, tier: Int, onDone: () -> Unit) {
    var initials by remember { mutableStateOf("AAA") }
    var slot by remember { mutableIntStateOf(0) }
    var saved by remember { mutableStateOf(false) }
    val table = remember(saved) { readTable(context) }

    AlertDialog(
        onDismissRequest = onDone,
        confirmButton = {
            TextButton(
                onClick = {
                    if (!saved) {
                        writeTable(context, initials, score, tier)
                        saved = true
                    } else {
                        onDone()
                    }
                },
            ) { Text(if (saved) "Done" else "Enter") }
        },
        title = { Mono("GAME OVER", 18, tint = Color(0xFFFF6FA5)) },
        text = {
            Column {
                Mono("SCORE  %07d".format(score), 14)
                Mono("LEVEL  %02d".format(tier), 14, modifier = Modifier.padding(bottom = 10.dp))

                if (!saved) {
                    Mono("ENTER YOUR INITIALS", 12)
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        repeat(3) { index ->
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(
                                        if (index == slot) {
                                            Color(0xFF2C3E63)
                                        } else {
                                            Color(0xFF1B2740)
                                        },
                                        RoundedCornerShape(6.dp),
                                    )
                                    .pointerInput(index) {
                                        detectTapGestures(
                                            onTap = { slot = index },
                                            onLongPress = { slot = index },
                                        )
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Mono(initials[index].toString(), 22)
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(onClick = { initials = shift(initials, slot, -1) }) {
                            Mono("<", 18)
                        }
                        TextButton(onClick = { initials = shift(initials, slot, 1) }) {
                            Mono(">", 18)
                        }
                        TextButton(onClick = { slot = (slot + 1) % 3 }) { Mono("NEXT", 14) }
                    }
                } else {
                    Mono("HIGH SCORES", 13, tint = Color(0xFFFFC857))
                    table.forEachIndexed { index, row ->
                        Mono(
                            "%d %s %07d  L%02d".format(index + 1, row.name, row.score, row.tier),
                            14,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        },
    )
}

private fun shift(text: String, index: Int, by: Int): String {
    val letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 "
    val at = letters.indexOf(text[index]).coerceAtLeast(0)
    val next = ((at + by) % letters.length + letters.length) % letters.length
    return text.substring(0, index) + letters[next] + text.substring(index + 1)
}

internal data class Row(val name: String, val score: Int, val tier: Int)

private const val STORE = "field"
private const val KEY = "table"

internal fun readTable(context: Context): List<Row> =
    context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        .getString(KEY, "")
        .orEmpty()
        .split('|')
        .filter { it.isNotBlank() }
        .mapNotNull { entry ->
            val parts = entry.split(',')
            if (parts.size != 3) return@mapNotNull null
            Row(parts[0], parts[1].toIntOrNull() ?: return@mapNotNull null, parts[2].toIntOrNull() ?: 1)
        }
        .sortedByDescending { it.score }
        .take(8)

internal fun writeTable(context: Context, name: String, score: Int, tier: Int) {
    val rows = (readTable(context) + Row(name, score, tier))
        .sortedByDescending { it.score }
        .take(8)
    context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY, rows.joinToString("|") { "${it.name},${it.score},${it.tier}" })
        .apply()
}
