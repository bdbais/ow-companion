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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.bellizia.owcompanion.R
import com.bellizia.owcompanion.sim.CircleHitBox
import com.bellizia.owcompanion.sim.Enemy
import com.bellizia.owcompanion.sim.RectHitBox
import kotlin.math.roundToInt

// Where the figure sits in target space, taken from the original chart: 3.3 m tall, its
// left edge 1.55 m left of the target's centreline, its feet 0.6 m below the hitbox floor.
private const val FigureHeightMetres = 3.3f
private const val FigureLeftMetres = -1.55f
private const val FigureBottomMetres = -0.6f

// View bounds, chosen to frame the whole figure with a little air around it.
private const val ViewMinX = -1.8f
private const val ViewMaxX = 1.9f
private const val ViewMinZ = -0.7f
private const val ViewMaxZ = 2.9f

private val TargetWidth = 124.dp
private val TargetHeight = 128.dp

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
    val figure = ImageBitmap.imageResource(R.drawable.roadhog_figure)
    val hitboxColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f)
    val crosshairColor = MaterialTheme.colorScheme.primary
    val groundColor = MaterialTheme.colorScheme.outline

    fun reportAim(position: Offset, size: IntSize) {
        val x = ViewMinX + position.x / size.width * (ViewMaxX - ViewMinX)
        val z = ViewMaxZ - position.y / size.height * (ViewMaxZ - ViewMinZ)
        currentOnAimChange(x.coerceIn(ViewMinX, ViewMaxX), z.coerceIn(ViewMinZ, ViewMaxZ))
    }

    Canvas(
        modifier = modifier
            .width(TargetWidth)
            .height(TargetHeight)
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    reportAim(change.position, size)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { position -> reportAim(position, size) }
            },
    ) {
        fun toX(metres: Float) = (metres - ViewMinX) / (ViewMaxX - ViewMinX) * size.width
        fun toY(metres: Float) = (ViewMaxZ - metres) / (ViewMaxZ - ViewMinZ) * size.height

        val figureWidthMetres = FigureHeightMetres * figure.width / figure.height
        val figureLeft = toX(FigureLeftMetres)
        val figureTop = toY(FigureBottomMetres + FigureHeightMetres)
        drawImage(
            image = figure,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(figure.width, figure.height),
            dstOffset = IntOffset(figureLeft.roundToInt(), figureTop.roundToInt()),
            dstSize = IntSize(
                width = (toX(FigureLeftMetres + figureWidthMetres) - figureLeft).roundToInt(),
                height = (toY(FigureBottomMetres) - figureTop).roundToInt(),
            ),
        )

        // The hitboxes the simulation actually tests against, outlined over the figure so
        // it is obvious that the head is a small circle and the body a generous rectangle.
        (enemy.body as? RectHitBox)?.let { body ->
            val left = toX((body.centerX - body.width / 2).toFloat())
            val top = toY((body.centerZ + body.height / 2).toFloat())
            drawRect(
                color = hitboxColor,
                topLeft = Offset(left, top),
                size = Size(
                    width = toX((body.centerX + body.width / 2).toFloat()) - left,
                    height = toY((body.centerZ - body.height / 2).toFloat()) - top,
                ),
                style = Stroke(width = 1.dp.toPx()),
            )
        }

        (enemy.head as? CircleHitBox)?.let { head ->
            val centre = Offset(toX(head.centerX.toFloat()), toY(head.centerZ.toFloat()))
            val radius = toX(head.radius.toFloat()) - toX(0f)
            drawCircle(
                color = hitboxColor,
                radius = radius,
                center = centre,
                style = Stroke(width = 1.dp.toPx()),
            )
        }

        drawLine(
            color = groundColor,
            start = Offset(0f, toY(0f)),
            end = Offset(size.width, toY(0f)),
            strokeWidth = 1.dp.toPx(),
        )

        val cx = toX(aimX)
        val cy = toY(aimZ)
        val arm = 8.dp.toPx()
        drawCircle(
            color = crosshairColor,
            radius = 4.dp.toPx(),
            center = Offset(cx, cy),
            style = Stroke(width = 1.5.dp.toPx()),
        )
        drawLine(crosshairColor, Offset(cx - arm, cy), Offset(cx + arm, cy), 1.5.dp.toPx())
        drawLine(crosshairColor, Offset(cx, cy - arm), Offset(cx, cy + arm), 1.5.dp.toPx())
    }
}
