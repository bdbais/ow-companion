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
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import androidx.core.content.FileProvider
import com.bellizia.owcompanion.data.WikiRepository
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
     * Encoded a frame at a time rather than through a Surface: at two seconds a phase there
     * is nothing to gain from the fast path, and this way the same drawing code produces the
     * video and the PDF, so they cannot drift apart.
     */
    suspend fun toVideo(context: Context, board: Board): Uri = withContext(Dispatchers.IO) {
        val file = outputFile(context, board, "mp4")
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, WIDTH, HEIGHT).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible,
            )
            setInteger(MediaFormat.KEY_BIT_RATE, 4_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track = -1
        var started = false
        val info = MediaCodec.BufferInfo()
        var presentation = 0L

        fun drain(endOfStream: Boolean) {
            while (true) {
                val index = codec.dequeueOutputBuffer(info, if (endOfStream) 10_000 else 0)
                when {
                    index == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!endOfStream) return
                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        track = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        started = true
                    }
                    index >= 0 -> {
                        val buffer = codec.getOutputBuffer(index)
                        if (buffer != null && info.size > 0 && started &&
                            info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0
                        ) {
                            muxer.writeSampleData(track, buffer, info)
                        }
                        codec.releaseOutputBuffer(index, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                    }
                }
            }
        }

        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        board.frames.forEachIndexed { index, frame ->
            draw(context, Canvas(bitmap), board, frame, index)
            val yuv = toYuv420(bitmap)
            repeat(FRAME_SECONDS * FRAME_RATE) {
                val input = codec.dequeueInputBuffer(10_000)
                if (input >= 0) {
                    codec.getInputBuffer(input)?.apply {
                        clear()
                        put(yuv)
                    }
                    codec.queueInputBuffer(input, 0, yuv.size, presentation, 0)
                    presentation += 1_000_000L / FRAME_RATE
                }
                drain(false)
            }
        }

        val input = codec.dequeueInputBuffer(10_000)
        if (input >= 0) {
            codec.queueInputBuffer(input, 0, 0, presentation, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        }
        drain(true)

        codec.stop()
        codec.release()
        if (started) muxer.stop()
        muxer.release()
        bitmap.recycle()

        share(context, file)
    }

    private const val FRAME_RATE = 15

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

        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        frame.tokens.forEach { token ->
            val cx = token.x * WIDTH
            val cy = token.y * HEIGHT
            portrait(context, token.portrait)?.let { face ->
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

    private val faces = mutableMapOf<String, Bitmap?>()

    private fun portrait(context: Context, name: String?): Bitmap? {
        if (name == null) return null
        return faces.getOrPut(name) {
            runCatching {
                context.assets.open("heroes/$name").use(BitmapFactory::decodeStream)
            }.getOrNull()
        }
    }

    /** ARGB to the planar YUV the encoder wants. */
    private fun toYuv420(bitmap: Bitmap): ByteArray {
        val pixels = IntArray(WIDTH * HEIGHT)
        bitmap.getPixels(pixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT)
        val out = ByteArray(WIDTH * HEIGHT * 3 / 2)
        var uv = WIDTH * HEIGHT
        for (y in 0 until HEIGHT) {
            for (x in 0 until WIDTH) {
                val p = pixels[y * WIDTH + x]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                out[y * WIDTH + x] = ((66 * r + 129 * g + 25 * b + 128 shr 8) + 16).toByte()
                if (y % 2 == 0 && x % 2 == 0) {
                    out[uv++] = ((-38 * r - 74 * g + 112 * b + 128 shr 8) + 128).toByte()
                    out[uv++] = ((112 * r - 94 * g - 18 * b + 128 shr 8) + 128).toByte()
                }
            }
        }
        return out
    }

    private fun outputFile(context: Context, board: Board, extension: String): File {
        val directory = File(context.cacheDir, "boards").apply { mkdirs() }
        val name = board.name.ifBlank { "board" }.replace(Regex("[^A-Za-z0-9-]"), "-")
        return File(directory, "$name.$extension")
    }

    private fun share(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)
}
