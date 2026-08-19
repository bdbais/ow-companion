package com.bellizia.owcompanion.ui.board

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.bellizia.owcompanion.data.WikiRepository
import com.bellizia.owcompanion.ui.common.FrameVideo
import com.bellizia.owcompanion.ui.common.exportName
import com.bellizia.owcompanion.ui.common.Portraits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Turns a board into something you can send to five other people.
 *
 * A PDF for reading - one page per phase, captions and all - and an MP4 for a chat window,
 * where nobody opens attachments. There is no GIF: Android has no encoder for one, hand
 * writing an LZW compressor to quantise a photograph to 256 colours would look worse than
 * the video, and both WhatsApp and Telegram treat a short silent MP4 as a GIF anyway.
 */
object BoardExport {

    private const val WIDTH = 1280
    private const val HEIGHT = 720
    private const val TOKEN = 76f

    /** How long each phase is held in the video. Long enough to read the caption. */
    private const val FRAME_SECONDS = 2

    suspend fun toPdf(context: Context, board: Board): Uri = withContext(Dispatchers.IO) {
        val document = PdfDocument()
        board.frames.forEachIndexed { index, frame ->
            val info = PdfDocument.PageInfo.Builder(WIDTH, HEIGHT, index + 1).create()
            val page = document.startPage(info)
            draw(context, page.canvas, board, frame, index)
            document.finishPage(page)
        }
        val file = outputFile(context, board, "pdf")
        FileOutputStream(file).use(document::writeTo)
        document.close()
        share(context, file)
    }

    /**
     * One clip, each phase held for a couple of seconds.
     *
     * The encoder lives in [FrameVideo] because the comic strip wants the identical thing.
     * What stays here is the only part that is about a board: which frame to draw.
     */
    suspend fun toVideo(context: Context, board: Board): Uri = withContext(Dispatchers.IO) {
        val file = outputFile(context, board, "mp4")
        FrameVideo.write(file, WIDTH, HEIGHT, board.frames.size, FRAME_SECONDS) { canvas, index ->
            draw(context, canvas, board, board.frames[index], index)
        }
        share(context, file)
    }

    /** The one drawing routine, so the page and the video always agree. */
    private fun draw(context: Context, canvas: Canvas, board: Board, frame: Frame, index: Int) {
        canvas.drawColor(AndroidColor.rgb(0x12, 0x1A, 0x26))

        board.background?.let { uri ->
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(uri)).use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }.getOrNull()?.let { image ->
                // Letterboxed rather than stretched: a map squashed to fit is a map nobody
                // recognises.
                val scale = minOf(WIDTH / image.width.toFloat(), HEIGHT / image.height.toFloat())
                val w = image.width * scale
                val h = image.height * scale
                canvas.drawBitmap(
                    image,
                    Rect(0, 0, image.width, image.height),
                    RectF((WIDTH - w) / 2, (HEIGHT - h) / 2, (WIDTH + w) / 2, (HEIGHT + h) / 2),
                    Paint(Paint.FILTER_BITMAP_FLAG),
                )
                image.recycle()
            }
        }

        // Arrows first, so a token always sits on top of the line that reaches it.
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 7f
            strokeCap = Paint.Cap.ROUND
        }
        frame.arrows.forEach { arrow ->
            stroke.color = if (arrow.side == Side.Ours) 0xFF4C8DF6.toInt() else 0xFFE0645C.toInt()
            val fromX = arrow.fromX * WIDTH
            val fromY = arrow.fromY * HEIGHT
            val toX = arrow.toX * WIDTH
            val toY = arrow.toY * HEIGHT
            canvas.drawLine(fromX, fromY, toX, toY, stroke)
            val dx = toX - fromX
            val dy = toY - fromY
            val length = kotlin.math.hypot(dx, dy)
            if (length >= 1f) {
                val ux = dx / length
                val uy = dy / length
                val head = minOf(28f, length / 2f)
                canvas.drawLine(
                    toX, toY,
                    toX - (ux * 0.87f - uy * 0.5f) * head,
                    toY - (uy * 0.87f + ux * 0.5f) * head,
                    stroke,
                )
                canvas.drawLine(
                    toX, toY,
                    toX - (ux * 0.87f + uy * 0.5f) * head,
                    toY - (uy * 0.87f - ux * 0.5f) * head,
                    stroke,
                )
            }
        }

        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        frame.tokens.forEach { token ->
            val cx = token.x * WIDTH
            val cy = token.y * HEIGHT
            Portraits.of(context, token.portrait)?.let { face ->
                val clip = canvas.save()
                canvas.clipRect(cx - TOKEN / 2, cy - TOKEN / 2, cx + TOKEN / 2, cy + TOKEN / 2)
                canvas.drawBitmap(
                    face,
                    Rect(0, 0, face.width, face.height),
                    RectF(cx - TOKEN / 2, cy - TOKEN / 2, cx + TOKEN / 2, cy + TOKEN / 2),
                    Paint(Paint.FILTER_BITMAP_FLAG),
                )
                canvas.restoreToCount(clip)
            }
            ring.color = if (token.side == Side.Ours) 0xFF4C8DF6.toInt() else 0xFFE0645C.toInt()
            canvas.drawCircle(cx, cy, TOKEN / 2, ring)
        }

        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.WHITE
            textSize = 34f
            isFakeBoldText = true
        }
        canvas.drawText("t${index + 1}", 28f, 52f, label)
        if (frame.caption.isNotBlank()) {
            label.isFakeBoldText = false
            label.textSize = 30f
            canvas.drawText(frame.caption, 96f, 52f, label)
        }
    }

    /** The exported file, named after the plan. The rule itself lives in [exportName]. */
    private fun outputFile(context: Context, board: Board, extension: String): File {
        val directory = File(context.cacheDir, "boards").apply { mkdirs() }
        return File(directory, "${exportName(board.name)}.$extension")
    }

    private fun share(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)
}
