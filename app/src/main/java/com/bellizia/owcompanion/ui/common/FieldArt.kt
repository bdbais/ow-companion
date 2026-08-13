package com.bellizia.owcompanion.ui.common

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import java.io.ByteArrayOutputStream
import java.util.zip.Inflater
import org.json.JSONObject

/**
 * The drawings the panel is painted with, packed into one file.
 *
 * They are kept out of `res/drawable` deliberately. Fifteen separate drawables would each
 * get a name in the resource table and a thumbnail in any tool that opens an APK; packed,
 * they are one asset. The bytes are also masked, so a scanner hunting for image headers
 * walks past the file. That is obfuscation and not protection - the key is four lines
 * below - and it only has to survive idle curiosity.
 *
 * Built by tools/make_field_art.py from the sheets in dataset/field.
 */
internal class FieldArt private constructor(
    val sheet: ImageBitmap,
    private val boxes: Map<String, Box>,
) {

    data class Box(val x: Int, val y: Int, val width: Int, val height: Int) {
        val offset: IntOffset get() = IntOffset(x, y)
        val size: IntSize get() = IntSize(width, height)
    }

    /**
     * Where a named piece sits in the sheet.
     *
     * An unknown name gives back null rather than throwing: a missing piece should leave a
     * gap in a panel, not take the app down.
     */
    operator fun get(name: String): Box? = boxes[name]

    companion object {
        private const val ASSET = "field.bin"
        private val KEY = byteArrayOf(0x5a, 0xa5.toByte(), 0x3c, 0xc3.toByte(), 0x69, 0x96.toByte())

        /** Loaded once and shared: the sheet is a megabyte of pixels once decoded. */
        @Volatile
        private var cached: FieldArt? = null

        fun load(context: Context): FieldArt? {
            cached?.let { return it }
            return synchronized(this) {
                cached ?: read(context)?.also { cached = it }
            }
        }

        private fun read(context: Context): FieldArt? = runCatching {
            val masked = context.assets.open(ASSET).use { it.readBytes() }
            val packed = ByteArray(masked.size) { index ->
                (masked[index].toInt() xor KEY[index % KEY.size].toInt()).toByte()
            }

            val body = inflate(packed)
            val headerLength = ((body[0].toInt() and 0xFF) shl 24) or
                ((body[1].toInt() and 0xFF) shl 16) or
                ((body[2].toInt() and 0xFF) shl 8) or
                (body[3].toInt() and 0xFF)

            val header = String(body, 4, headerLength, Charsets.UTF_8)
            val imageStart = 4 + headerLength
            val bitmap = BitmapFactory.decodeByteArray(body, imageStart, body.size - imageStart)
                ?: return@runCatching null

            val json = JSONObject(header)
            val boxes = buildMap {
                json.keys().forEach { name ->
                    val array = json.getJSONArray(name)
                    put(
                        name,
                        Box(array.getInt(0), array.getInt(1), array.getInt(2), array.getInt(3)),
                    )
                }
            }
            FieldArt(bitmap.asImageBitmap(), boxes)
        }.getOrNull()

        private fun inflate(packed: ByteArray): ByteArray {
            val inflater = Inflater()
            inflater.setInput(packed)
            val out = ByteArrayOutputStream(packed.size * 4)
            val buffer = ByteArray(16 * 1024)
            try {
                while (!inflater.finished()) {
                    val read = inflater.inflate(buffer)
                    if (read == 0 && inflater.needsInput()) break
                    out.write(buffer, 0, read)
                }
            } finally {
                inflater.end()
            }
            return out.toByteArray()
        }
    }
}
