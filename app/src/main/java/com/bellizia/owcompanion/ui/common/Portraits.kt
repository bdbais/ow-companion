package com.bellizia.owcompanion.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * Hero faces decoded from the assets, once each.
 *
 * Fifty-three portraits at a few hundred kilobytes apiece is small enough to keep and far
 * too slow to decode per frame: the comic exporter draws every panel fifteen times a second
 * for the length of the clip, and re-reading the same PNG each time would dominate it.
 *
 * A name that is not there yields null rather than throwing. A missing portrait should cost
 * a blank square in one panel, not a failed export.
 */
object Portraits {

    private val cache = mutableMapOf<String, Bitmap?>()

    fun of(context: Context, name: String?): Bitmap? {
        if (name == null) return null
        return cache.getOrPut(name) {
            runCatching {
                context.assets.open("heroes/$name").use(BitmapFactory::decodeStream)
            }.getOrNull()
        }
    }
}
