package com.bellizia.owcompanion.ui.comics

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.bellizia.owcompanion.ui.common.Portraits
import kotlin.math.max
import kotlin.math.min

/**
 * Draws one panel. The only place a panel is ever drawn.
 *
 * The editor renders through this onto a Compose canvas and the exporter renders through it
 * onto a page and a video, so what you arrange on the phone is what comes out - down to
 * where a balloon's tail lands. The tactics board learned this the same way; a second
 * drawing routine is a second set of bugs that only shows up in the file you already sent
 * to five people.
 *
 * Everything is expressed against the panel's own width and height, never in fixed pixels,
 * because the editor draws it a few hundred pixels wide and the export draws it at 1200.
 */
object ComicPainter {

    /** Panel proportions, and the size the exporter uses. Landscape, like a comic tier. */
    const val WIDTH = 1200
    const val HEIGHT = 800

    private const val INK = 0xFF12131A.toInt()
    private const val PAPER = 0xFFF6F1E4.toInt()

    fun draw(context: Context, canvas: Canvas, panel: Panel, width: Int, height: Int) {
        val w = width.toFloat()
        val h = height.toFloat()

        canvas.drawColor(PAPER)
        background(context, canvas, panel, w, h)
        panel.actors.forEach { actor -> actor(context, canvas, actor, w, h) }
        panel.lines.forEach { line -> balloon(canvas, line, panel.actors, w, h) }

        // The border last, so nothing a reader placed near the edge paints over it.
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = h * 0.012f
            color = INK
        }
        val inset = edge.strokeWidth / 2
        canvas.drawRect(inset, inset, w - inset, h - inset, edge)
    }

    private fun background(context: Context, canvas: Canvas, panel: Panel, w: Float, h: Float) {
        val uri = panel.background
        if (uri == null) {
            // Speed lines rather than a flat rectangle: an empty panel still reads as a
            // comic panel, and a reader who never loads a picture still gets something that
            // looks deliberate.
            val ray = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x14000000
                strokeWidth = h * 0.004f
            }
            var x = -w
            while (x < w * 2) {
                canvas.drawLine(x, 0f, x + w * 0.35f, h, ray)
                x += w * 0.055f
            }
            return
        }
        val image = runCatching {
            context.contentResolver.openInputStream(Uri.parse(uri)).use(BitmapFactory::decodeStream)
        }.getOrNull() ?: return

        // Filled rather than letterboxed: a comic panel is a crop of a scene, and bars down
        // the sides would read as a mistake. The tactics board does the opposite on purpose,
        // because there a cropped map is a map you can no longer read positions off.
        val scale = max(w / image.width, h / image.height)
        val cw = w / scale
        val ch = h / scale
        val left = ((image.width - cw) / 2).toInt()
        val top = ((image.height - ch) / 2).toInt()
        canvas.drawBitmap(
            image,
            Rect(left, top, left + cw.toInt(), top + ch.toInt()),
            RectF(0f, 0f, w, h),
            Paint(Paint.FILTER_BITMAP_FLAG),
        )
        image.recycle()
    }

    private fun actor(context: Context, canvas: Canvas, actor: Actor, w: Float, h: Float) {
        if (actor.figure != null) {
            figure(canvas, actor, w, h)
            return
        }
        val face = Portraits.of(context, actor.portrait) ?: return
        val size = h * 0.42f * actor.scale
        val cx = actor.x * w
        val cy = actor.y * h
        val target = RectF(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2)

        val saved = canvas.save()
        // Clipped to the same oval the ring is drawn on. Without this the portrait's square
        // corners stick out past the ring, which reads as a sticker sitting on the panel
        // rather than a character standing in it.
        canvas.clipPath(Path().apply { addOval(target, Path.Direction.CW) })
        if (actor.flipped) {
            // Mirrored about the character's own centre, so flipping does not move them.
            canvas.concat(Matrix().apply { setScale(-1f, 1f, cx, cy) })
        }
        canvas.drawBitmap(
            face,
            Rect(0, 0, face.width, face.height),
            target,
            Paint(Paint.FILTER_BITMAP_FLAG),
        )
        canvas.restoreToCount(saved)

        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = h * 0.008f
            color = INK
        }
        canvas.drawOval(target, ring)
    }

    /**
     * A generated silhouette, drawn whole and standing on its position.
     *
     * No ring and no circle: it is already a shape rather than a photograph, and the
     * position is where its feet are rather than where its middle is, because that is how
     * anybody places a figure in a scene.
     */
    private fun figure(canvas: Canvas, actor: Actor, w: Float, h: Float) {
        val cut = Cutouts.of(actor.figure ?: return) ?: return
        val tall = h * 0.62f * actor.scale
        val wide = tall * cut.width / cut.height
        val cx = actor.x * w
        val feet = actor.y * h
        val target = RectF(cx - wide / 2, feet - tall, cx + wide / 2, feet)

        val saved = canvas.save()
        if (actor.flipped) {
            canvas.concat(Matrix().apply { setScale(-1f, 1f, cx, feet) })
        }
        canvas.drawBitmap(
            cut,
            Rect(0, 0, cut.width, cut.height),
            target,
            Paint(Paint.FILTER_BITMAP_FLAG),
        )
        canvas.restoreToCount(saved)
    }

    private fun balloon(canvas: Canvas, line: Line, actors: List<Actor>, w: Float, h: Float) {
        if (line.text.isBlank()) return

        val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK
            textSize = h * (if (line.kind == Balloon.Shout) 0.062f else 0.052f)
            isFakeBoldText = line.kind == Balloon.Shout
        }
        val maxText = (w * 0.44f).toInt()
        val layout = StaticLayout.Builder
            .obtain(line.text, 0, line.text.length, text, maxText)
            .setAlignment(
                if (line.kind == Balloon.Caption) Layout.Alignment.ALIGN_NORMAL
                else Layout.Alignment.ALIGN_CENTER,
            )
            .build()

        // The bubble hugs the text it actually got, not the width it was allowed.
        var widest = 0f
        for (i in 0 until layout.lineCount) widest = max(widest, layout.getLineWidth(i))
        val padX = h * 0.035f
        val padY = h * 0.028f
        val boxW = widest + padX * 2
        val boxH = layout.height + padY * 2

        // Kept inside the panel: a balloon dragged to the edge should stop, not be cut in
        // half by the border.
        val margin = h * 0.03f
        val cx = min(max(line.x * w, boxW / 2 + margin), w - boxW / 2 - margin)
        val cy = min(max(line.y * h, boxH / 2 + margin), h - boxH / 2 - margin)
        val box = RectF(cx - boxW / 2, cy - boxH / 2, cx + boxW / 2, cy + boxH / 2)

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PAPER }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = h * 0.008f
            color = INK
        }

        when (line.kind) {
            Balloon.Caption -> {
                canvas.drawRect(box, fill)
                canvas.drawRect(box, stroke)
            }
            Balloon.Shout -> {
                val spikes = burst(box)
                canvas.drawPath(spikes, fill)
                canvas.drawPath(spikes, stroke)
            }
            Balloon.Thought -> {
                val radius = boxH / 2
                canvas.drawRoundRect(box, radius, radius, fill)
                canvas.drawRoundRect(box, radius, radius, stroke)
                trailOfBubbles(canvas, box, aimedAt(box, actors, w), h, fill, stroke)
            }
            Balloon.Speech -> {
                val radius = h * 0.05f
                canvas.drawRoundRect(box, radius, radius, fill)
                val tail = tail(box, aimedAt(box, actors, w), h)
                // Filled before the outline and with the box redrawn over the seam, so the
                // tail joins the bubble instead of being a triangle stuck to it.
                canvas.drawPath(tail, fill)
                canvas.drawPath(tail, stroke)
                canvas.drawRoundRect(box, radius, radius, fill)
                canvas.drawRoundRect(box, radius, radius, stroke)
            }
        }

        val saved = canvas.save()
        canvas.translate(box.left + padX + (widest - layout.width) / 2, box.top + padY)
        layout.draw(canvas)
        canvas.restoreToCount(saved)
    }

    /**
     * Where along the bubble's underside the pointer starts, so that it aims at a speaker.
     *
     * The nearest character horizontally, expressed as a fraction across the bubble. With
     * nobody in the panel it stays in the middle, which is what a balloon over an empty
     * scene should do rather than lurch to one side.
     */
    private fun aimedAt(box: RectF, actors: List<Actor>, w: Float): Float {
        val speaker = actors.minByOrNull { kotlin.math.abs(it.x * w - box.centerX()) } ?: return 0.5f
        if (box.width() <= 0f) return 0.5f
        return (speaker.x * w - box.left) / box.width()
    }

    /** A pointer from the bubble's underside down towards whoever is speaking. */
    private fun tail(box: RectF, at: Float, h: Float): Path {
        val x = box.left + box.width() * at.coerceIn(0.15f, 0.85f)
        val spread = h * 0.045f
        val drop = h * 0.075f
        return Path().apply {
            moveTo(x - spread, box.bottom - h * 0.01f)
            lineTo(x + spread * 0.35f, box.bottom - h * 0.01f)
            lineTo(x - spread * 0.5f, box.bottom + drop)
            close()
        }
    }

    /** Three shrinking circles: the tail of a thought, which is not a pointer. */
    private fun trailOfBubbles(
        canvas: Canvas,
        box: RectF,
        at: Float,
        h: Float,
        fill: Paint,
        stroke: Paint,
    ) {
        val x = box.left + box.width() * at.coerceIn(0.15f, 0.85f)
        var y = box.bottom + h * 0.02f
        var radius = h * 0.022f
        repeat(3) {
            canvas.drawCircle(x, y, radius, fill)
            canvas.drawCircle(x, y, radius, stroke)
            y += radius * 2.1f
            radius *= 0.66f
        }
    }

    /** The jagged outline of a shout. */
    private fun burst(box: RectF): Path {
        val cx = box.centerX()
        val cy = box.centerY()
        val rx = box.width() / 2
        val ry = box.height() / 2
        val points = 18
        return Path().apply {
            for (i in 0 until points) {
                val angle = (Math.PI * 2 * i / points).toFloat()
                val reach = if (i % 2 == 0) 1.18f else 0.92f
                val x = cx + (Math.cos(angle.toDouble()).toFloat()) * rx * reach
                val y = cy + (Math.sin(angle.toDouble()).toFloat()) * ry * reach
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
    }

    /** Ink and paper, so the editor's chrome can match what it is drawing. */
    val paperColor: Int get() = PAPER
    val inkColor: Int get() = INK
}
