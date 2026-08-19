package com.bellizia.owcompanion.ui.common

import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File

/**
 * A slideshow as an MP4: draw frame `n`, hold it, move on.
 *
 * Written once and used by both the tactics board and the comic strip, which want exactly
 * the same thing - a handful of still pictures, each held long enough to read, in a file a
 * chat window will play inline. There is no GIF because Android has no encoder for one, and
 * both WhatsApp and Telegram treat a short silent MP4 as a GIF anyway.
 *
 * Encoded a frame at a time rather than through a Surface: at seconds per picture there is
 * nothing to gain from the fast path, and this way the caller's own drawing code produces
 * the video, so a video and a printed page cannot drift apart.
 *
 * Blocking, and does its own bitmap allocation - call it off the main thread.
 */
object FrameVideo {

    const val FRAME_RATE = 15

    fun write(
        file: File,
        width: Int,
        height: Int,
        count: Int,
        secondsEach: Int,
        draw: (Canvas, Int) -> Unit,
    ) {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
            .apply {
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

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        repeat(count) { index ->
            draw(Canvas(bitmap), index)
            val yuv = toYuv420(bitmap, width, height)
            repeat(secondsEach * FRAME_RATE) {
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
    }

    /** ARGB to the planar YUV the encoder wants. */
    private fun toYuv420(bitmap: Bitmap, width: Int, height: Int): ByteArray {
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val out = ByteArray(width * height * 3 / 2)
        var uv = width * height
        for (y in 0 until height) {
            for (x in 0 until width) {
                val p = pixels[y * width + x]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                out[y * width + x] = ((66 * r + 129 * g + 25 * b + 128 shr 8) + 16).toByte()
                if (y % 2 == 0 && x % 2 == 0) {
                    out[uv++] = ((-38 * r - 74 * g + 112 * b + 128 shr 8) + 128).toByte()
                    out[uv++] = ((112 * r - 94 * g - 18 * b + 128 shr 8) + 128).toByte()
                }
            }
        }
        return out
    }
}
