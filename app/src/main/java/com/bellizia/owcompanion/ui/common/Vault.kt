package com.bellizia.owcompanion.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val PLATE = Color(0xFF20242B)
private val PLATE_DEEP = Color(0xFF14171C)
private val BRUSHED = Color(0xFF8E99A6)
private val BRIGHT = Color(0xFFD7DEE6)
private val WARM = Color(0xFFE0762B)
private val PALE_METAL = Color(0xFFF4F1EA)
private val GLOW = Color(0xFF49D9A8)

/**
 * A combination lock, of the kind bolted to a door somebody does not want opened.
 *
 * Nine letters around a ring and a three-armed index that turns between them one notch at a
 * time. Two arms are the warm colour and the middle one pale - the same three the game's
 * own mark uses, in the same order - and the bolt draws back when those arms are reading
 * the right letters.
 *
 * Stepped rather than dragged. A safe dial has detents, and a drag on a circle is a fight
 * with the finger: two buttons say plainly that there are nine positions and one of them is
 * the answer.
 */
@Composable
internal fun Vault(letters: List<Char>, onOpen: () -> Unit, onDismiss: () -> Unit) {
    var notch by remember(letters) { mutableIntStateOf(0) }
    val measurer = rememberTextMeasurer()

    val reading = remember(notch, letters) {
        ARMS.map { offset -> letters[(notch + offset).mod(letters.size)] }
    }
    val open = reading == ANSWER

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(PLATE_DEEP)) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                    val middle = Offset(size.width / 2f, size.height / 2f)
                    val r = minOf(size.width, size.height) * 0.42f

                    // The door: a plate, a bezel, and bolt heads around the frame.
                    drawCircle(PLATE, radius = r * 1.22f, center = middle)
                    drawCircle(
                        color = BRUSHED.copy(alpha = 0.55f),
                        radius = r * 1.22f,
                        center = middle,
                        style = Stroke(r * 0.05f),
                    )
                    drawCircle(PLATE_DEEP, radius = r * 1.05f, center = middle)
                    repeat(8) { index ->
                        val at = index * (2 * PI / 8).toFloat() + 0.39f
                        drawCircle(
                            color = BRUSHED.copy(alpha = 0.5f),
                            radius = r * 0.035f,
                            center = middle + Offset(cos(at) * r * 1.15f, sin(at) * r * 1.15f),
                        )
                    }

                    // Knurling on the rim, so it reads as something meant to be turned.
                    repeat(72) { index ->
                        val at = index * (2 * PI / 72).toFloat()
                        drawLine(
                            color = BRUSHED.copy(alpha = 0.35f),
                            start = middle + Offset(cos(at) * r * 0.98f, sin(at) * r * 0.98f),
                            end = middle + Offset(cos(at) * r * 1.04f, sin(at) * r * 1.04f),
                            strokeWidth = 2f,
                        )
                    }

                    // The letters, one per notch, upright so they can actually be read.
                    letters.forEachIndexed { index, letter ->
                        val at = index * (2 * PI / letters.size).toFloat() - (PI / 2).toFloat()
                        val lit = ARMS.any { (notch + it).mod(letters.size) == index }
                        val layout = measurer.measure(
                            text = letter.toString(),
                            style = TextStyle(
                                color = if (lit) PALE_METAL else BRUSHED,
                                fontSize = (r * 0.16f).toSp(),
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                            ),
                        )
                        val seat = middle + Offset(cos(at) * r * 0.80f, sin(at) * r * 0.80f)
                        drawText(
                            textLayoutResult = layout,
                            topLeft = Offset(
                                seat.x - layout.size.width / 2f,
                                seat.y - layout.size.height / 2f,
                            ),
                        )
                    }

                    // The index: three arms out of the hub, two warm and one pale.
                    ARMS.forEachIndexed { which, offset ->
                        val slot = (notch + offset).mod(letters.size)
                        val at = slot * (2 * PI / letters.size).toFloat() - (PI / 2).toFloat()
                        drawLine(
                            color = if (which == PALE_ARM) PALE_METAL else WARM,
                            start = middle,
                            end = middle + Offset(cos(at) * r * 0.62f, sin(at) * r * 0.62f),
                            strokeWidth = r * 0.075f,
                        )
                    }

                    // The hub, which becomes the handle once the bolt is back.
                    drawCircle(if (open) GLOW else PLATE, radius = r * 0.20f, center = middle)
                    drawCircle(
                        color = if (open) GLOW else BRUSHED,
                        radius = r * 0.20f,
                        center = middle,
                        style = Stroke(r * 0.03f),
                    )
                    if (open) drawCircle(GLOW.copy(alpha = 0.22f), radius = r * 0.34f, center = middle)
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Knob("<") { notch = (notch - 1).mod(letters.size) }
                    Text(
                        text = reading.joinToString("  "),
                        color = BRIGHT,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )
                    Knob(">") { notch = (notch + 1).mod(letters.size) }
                }

                if (open) {
                    Button(
                        onClick = onOpen,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GLOW,
                            contentColor = PLATE_DEEP,
                        ),
                        modifier = Modifier.padding(top = 20.dp),
                    ) {
                        Text("OPEN", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                }

                TextButton(onClick = onDismiss, modifier = Modifier.padding(top = 6.dp)) {
                    Text("BACK", color = BRUSHED, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun Knob(glyph: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = PLATE, contentColor = BRIGHT),
    ) {
        Text(glyph, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

/**
 * How far apart the three arms sit, in notches.
 *
 * Nine letters and arms three notches apart means every one of the nine positions is a
 * clean reading rather than something caught between two letters.
 */
private val ARMS = listOf(0, 3, 6)

/** Which arm is the pale one. */
private const val PALE_ARM = 1

/** Read off the arms in order: warm, pale, warm. */
private val ANSWER = listOf('Y', 'B', 'Z')

/** How many letters go round. */
private const val RING = 9

/**
 * The ring: shuffled every time, and always solvable exactly once.
 *
 * The answer is planted three notches apart at a random offset and the rest filled around
 * it, so one position opens the lock and it cannot be remembered as an angle from before.
 */
internal fun vaultLetters(seed: Long): List<Char> {
    val random = Random(seed)
    val ring = arrayOfNulls<Char>(RING)
    val offset = random.nextInt(RING)
    ANSWER.forEachIndexed { index, letter ->
        ring[(offset + ARMS[index]).mod(RING)] = letter
    }
    val filler = ('A'..'Z').filterNot { it in ANSWER }.shuffled(random)
    var next = 0
    for (index in ring.indices) {
        if (ring[index] == null) ring[index] = filler[next++]
    }
    return ring.map { requireNotNull(it) }
}
