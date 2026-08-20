package com.bellizia.owcompanion.ui.comics

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * Cut-out figures, decoded once each.
 *
 * The same reason [com.bellizia.owcompanion.ui.common.Portraits] exists, only sharper here:
 * the exporter draws every panel fifteen times a second for the length of a clip, and a
 * silhouette decoded per frame would dominate the encode. These are PNGs the app wrote
 * itself, so a file that fails to decode means it was deleted underneath us - null, and the
 * panel simply draws without it.
 */
object Cutouts {

    private val cache = mutableMapOf<String, Bitmap?>()

    fun of(path: String): Bitmap? = cache.getOrPut(path) {
        runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
    }

    /** Forgets one file, for when it is replaced or removed. */
    fun forget(path: String) {
        cache.remove(path)
    }
}
