package com.bellizia.owcompanion.ui.comics

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.bellizia.owcompanion.ui.common.FrameVideo
import com.bellizia.owcompanion.ui.common.exportName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * A strip you can send to somebody: one picture, one page, or a short clip.
 *
 * PNG is what actually gets shared - it drops into any chat as an image and needs no reader.
 * PDF is the same layout for printing. The video is the same panels held a few seconds each,
 * which is what "animated" means here: the reading is paced for you, so it plays in a chat
 * window where nobody opens attachments.
 *
 * All three go through [ComicPainter], so the three files always show the same strip.
 */
object ComicExport {

    /** Space around the panels and between them, in the same units as a panel. */
    private const val MARGIN = 40
    private const val GUTTER = 28

    /** How long a panel is held in the video. Long enough to read a couple of balloons. */
    private const val PANEL_SECONDS = 3

    private const val PAPER = 0xFFE8E2D2.toInt()

    suspend fun toPng(context: Context, strip: Strip): Uri = withContext(Dispatchers.IO) {
        val bitmap = Bitmap.createBitmap(sheetWidth(), sheetHeight(strip), Bitmap.Config.ARGB_8888)
        drawSheet(context, Canvas(bitmap), strip)
        val file = outputFile(context, strip, "png")
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        bitmap.recycle()
        share(context, file)
    }

    /**
     * One page holding the whole strip, whatever length that is.
     *
     * Not A4. A comic is as long as it is, and slicing a six-panel strip across two portrait
     * pages puts a page break in the middle of a conversation. PDF pages can be any size, so
     * the page is simply the shape of the strip.
     */
    suspend fun toPdf(context: Context, strip: Strip): Uri = withContext(Dispatchers.IO) {
        val document = PdfDocument()
        val info = PdfDocument.PageInfo
            .Builder(sheetWidth(), sheetHeight(strip), 1)
            .create()
        val page = document.startPage(info)
        drawSheet(context, page.canvas, strip)
        document.finishPage(page)

        val file = outputFile(context, strip, "pdf")
        FileOutputStream(file).use(document::writeTo)
        document.close()
        share(context, file)
    }

    suspend fun toVideo(context: Context, strip: Strip): Uri = withContext(Dispatchers.IO) {
        val file = outputFile(context, strip, "mp4")
        FrameVideo.write(
            file = file,
            width = ComicPainter.WIDTH,
            height = ComicPainter.HEIGHT,
            count = strip.panels.size,
            secondsEach = PANEL_SECONDS,
        ) { canvas, index ->
            ComicPainter.draw(
                context,
                canvas,
                strip.panels[index],
                ComicPainter.WIDTH,
                ComicPainter.HEIGHT,
            )
        }
        share(context, file)
    }

    private fun sheetWidth() = ComicPainter.WIDTH + MARGIN * 2

    private fun sheetHeight(strip: Strip): Int {
        val count = strip.panels.size.coerceAtLeast(1)
        return MARGIN * 2 + count * ComicPainter.HEIGHT + (count - 1) * GUTTER
    }

    /**
     * The panels in a single column.
     *
     * A column rather than a grid because a strip is read on a phone, where a two-by-two
     * grid means every panel is half the width and the dialogue stops being legible. It also
     * means the layout does not change shape at four panels, so a strip looks like itself
     * whether it is two panels long or nine.
     */
    private fun drawSheet(context: Context, canvas: Canvas, strip: Strip) {
        canvas.drawColor(PAPER)
        strip.panels.forEachIndexed { index, panel ->
            val top = MARGIN + index * (ComicPainter.HEIGHT + GUTTER)
            val saved = canvas.save()
            canvas.translate(MARGIN.toFloat(), top.toFloat())
            canvas.clipRect(0, 0, ComicPainter.WIDTH, ComicPainter.HEIGHT)
            ComicPainter.draw(context, canvas, panel, ComicPainter.WIDTH, ComicPainter.HEIGHT)
            canvas.restoreToCount(saved)
        }
        // Nothing else on the sheet: no title, no watermark, no app name. Somebody sharing a
        // strip is sharing their joke, not an advert for where they made it.
    }

    private fun outputFile(context: Context, strip: Strip, extension: String): File {
        val directory = File(context.cacheDir, "comics").apply { mkdirs() }
        return File(directory, "${exportName(strip.name, fallback = "strip")}.$extension")
    }

    private fun share(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)
}
