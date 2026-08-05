package com.bellizia.owcompanion.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.bellizia.owcompanion.sim.CircleHitBox
import com.bellizia.owcompanion.sim.Enemy
import com.bellizia.owcompanion.sim.RectHitBox

/** Metres of target space shown horizontally and vertically. */
private const val ViewWidthMetres = 2.6f
private const val ViewHeightMetres = 2.8f

private val TargetWidth = 120.dp
private val TargetHeight = 130.dp

/**
 * The target being shot at, with a draggable crosshair.
 *
 * Where you aim is half the answer to "how much damage does this weapon do": the same
 * shotgun that shreds at centre mass whiffs most of its pellets aimed at the head. Dragging
 * the crosshair re-runs the whole chart against the new aim point.
 */
@Composable
fun AimTarget(
    aimX: Float,
    aimZ: Float,
    onAimChange: (x: Float, z: Float) -> Unit,
    modifier: Modifier = Modifier,
    enemy: Enemy = Enemy.roadhog(),
) {
    val currentOnAimChange by rememberUpdatedState(onAimChange)
    val bodyColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
    val headColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.30f)
    val crosshairColor = MaterialTheme.colorScheme.primary

    Canvas(
        modifier = modifier
            .width(TargetWidth)
            .height(TargetHeight)
            .pointerInput(Unit) {
                fun report(position: Offset) {
                    val metresPerPxX = ViewWidthMetres / size.width
                    val metresPerPxY = ViewHeightMetres / size.height
                    val x = (position.x - size.width / 2f) * metresPerPxX
                    val z = (size.height - position.y) * metresPerPxY
                    currentOnAimChange(
                        x.coerceIn(-ViewWidthMetres / 2, ViewWidthMetres / 2),
                        z.coerceIn(0f, ViewHeightMetres),
                    )
                }
                detectDragGestures { change, _ ->
                    change.consume()
                    report(change.position)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { position ->
                    val metresPerPxX = ViewWidthMetres / size.width
                    val metresPerPxY = ViewHeightMetres / size.height
                    currentOnAimChange(
                        ((position.x - size.width / 2f) * metresPerPxX)
                            .coerceIn(-ViewWidthMetres / 2, ViewWidthMetres / 2),
                        ((size.height - position.y) * metresPerPxY)
                            .coerceIn(0f, ViewHeightMetres),
                    )
                }
            },
    ) {
        val pxPerMetreX = size.width / ViewWidthMetres
        val pxPerMetreY = size.height / ViewHeightMetres

        fun toX(metres: Float) = size.width / 2f + metres * pxPerMetreX
        fun toY(metres: Float) = size.height - metres * pxPerMetreY

        (enemy.body as? RectHitBox)?.let { body ->
            drawRect(
                color = bodyColor,
                topLeft = Offset(
                    x = toX((body.centerX - body.width / 2).toFloat()),
                    y = toY((body.centerZ + body.height / 2).toFloat()),
                ),
                size = Size(
                    width = body.width.toFloat() * pxPerMetreX,
                    height = body.height.toFloat() * pxPerMetreY,
                ),
            )
        }

        (enemy.head as? CircleHitBox)?.let { head ->
            drawCircle(
                color = headColor,
                radius = head.radius.toFloat() * pxPerMetreX,
                center = Offset(toX(head.centerX.toFloat()), toY(head.centerZ.toFloat())),
            )
        }

        val cx = toX(aimX)
        val cy = toY(aimZ)
        val arm = 7.dp.toPx()
        drawCircle(
            color = crosshairColor,
            radius = 4.dp.toPx(),
            center = Offset(cx, cy),
            style = Stroke(width = 1.5.dp.toPx()),
        )
        drawLine(crosshairColor, Offset(cx - arm, cy), Offset(cx + arm, cy), 1.5.dp.toPx())
        drawLine(crosshairColor, Offset(cx, cy - arm), Offset(cx, cy + arm), 1.5.dp.toPx())
        drawLine(
            color = Color.Gray.copy(alpha = 0.4f),
            start = Offset(0f, size.height),
            end = Offset(size.width, size.height),
            strokeWidth = 1.dp.toPx(),
        )
    }
}
