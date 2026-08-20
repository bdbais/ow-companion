package com.bellizia.owcompanion.ui.comics

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Turns a generated picture of a dark figure on a light ground into a cut-out.
 *
 * The generator returns JPEG and only JPEG - transparency was asked for three different ways
 * and refused each time - so the transparency has to be found here.
 *
 * ### Why it is a border test and not a threshold
 *
 * The obvious approach is "dark is the figure, light is the background". It fails on the
 * generations that matter: one probe came back with the subject standing in front of a large
 * dark grey panel, and a fixed threshold happily kept the panel, the floor and the figure as
 * one shape. What separates a usable picture from an unusable one is not how dark the
 * subject is, it is **whether the edge of the frame is clean**. So this measures the border
 * first and refuses everything else, rather than producing a confident mess.
 *
 * Roughly half of all generations are refused. That is the honest rate for a free service,
 * and it is why the workshop asks for several at once instead of one at a time.
 */
object Silhouette {

    /**
     * A cut-out, or null when this picture is not one.
     *
     * Null means "ask for another", not "something went wrong": a rejected frame is the
     * normal case, not an error worth reporting to anybody.
     */
    fun cut(source: Bitmap): Bitmap? {
        val width = source.width
        val height = source.height
        if (width < 64 || height < 64) return null

        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        // The frame's own edge, which is background wherever the generator behaved.
        //
        // Judged near its darkest rather than at it: a single stray dark pixel in one corner
        // is a compression artefact, not a background, and testing the true minimum threw
        // away pictures that were perfectly clean everywhere else. Measured on six
        // generations, the tenth percentile kept one the minimum rejected and let none
        // through that it should not have.
        val edge = IntArray(2 * (width + height))
        var at = 0
        forEachBorderIndex(width, height) { index -> edge[at++] = luminance(pixels[index]) }
        val sample = edge.copyOf(at).also { it.sort() }
        val darkestEdge = sample[sample.size / 10]
        if (darkestEdge < CLEAN_BORDER) return null

        // Halfway between the clean border and black: high enough to keep a soft edge, low
        // enough that off-white paper does not become part of the figure.
        val cutAt = darkestEdge / 2

        var kept = 0
        for (i in pixels.indices) {
            if (luminance(pixels[i]) < cutAt) {
                pixels[i] = INK
                kept++
            } else {
                pixels[i] = Color.TRANSPARENT
            }
        }

        // A frame that is nearly all figure is a picture of something else; one that is
        // nearly empty is a picture of nothing. Both were seen in testing.
        val covered = kept.toFloat() / pixels.size
        if (covered < MIN_COVER || covered > MAX_COVER) return null

        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private inline fun forEachBorderIndex(width: Int, height: Int, action: (Int) -> Unit) {
        for (x in 0 until width) {
            action(x)
            action((height - 1) * width + x)
        }
        for (y in 0 until height) {
            action(y * width)
            action(y * width + width - 1)
        }
    }

    /** Rec. 601 luma, which is what a silhouette's readability actually follows. */
    private fun luminance(pixel: Int): Int =
        (Color.red(pixel) * 77 + Color.green(pixel) * 151 + Color.blue(pixel) * 28) shr 8

    /** Below this the frame's edge is not background, and nothing here can be trusted. */
    internal const val CLEAN_BORDER = 150

    internal const val MIN_COVER = 0.03f
    internal const val MAX_COVER = 0.40f

    /** Not pure black: the panel's own ink, so a figure belongs to the drawing. */
    private val INK = Color.argb(255, 18, 19, 26)
}
