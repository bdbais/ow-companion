package com.bellizia.owcompanion.ui.board

/**
 * Finding the separate maps inside one picture.
 *
 * A control map's sheet is three diagrams side by side - Busan is Sanctuary, Downtown and
 * MEKA Base - with a plain gutter between them. Placing tokens on all three at once is
 * useless, so this works out where the gutters are and hands back one rectangle per panel.
 *
 * The method is deliberately dull: a column is "empty" when every pixel in it is close to
 * the same colour, a run of empty columns is a gutter, and what lies between two gutters is
 * a panel. It is pure arithmetic over a column summary, so it runs on a summary computed
 * once from the bitmap and can be tested without one.
 */
object Panels {

    /** A slice of the picture, as fractions of its width so it survives any scaling. */
    data class Panel(val start: Float, val end: Float) {
        val width: Float get() = end - start
    }

    /**
     * How uniform a column has to be before it counts as background.
     *
     * Generous, because a gutter is rarely perfectly flat: a faint gradient or a JPEG's
     * ringing should not stop it being read as empty.
     */
    private const val FLAT = 0.045f

    /** A gutter narrower than this is a line in the artwork, not a separation. */
    private const val MIN_GUTTER = 0.012f

    /** Anything narrower than this is a margin or a caption strip, not a map. */
    private const val MIN_PANEL = 0.12f

    /**
     * Splits a picture into panels from a per-column measure of how varied it is.
     *
     * @param variance one value per column, 0 for a column of a single colour and 1 for the
     *   most varied column in the picture.
     * @return the panels found, left to right. A picture that is one map comes back as one
     *   panel covering all of it, which is the answer that needs no special case.
     */
    fun split(variance: FloatArray): List<Panel> {
        if (variance.isEmpty()) return listOf(Panel(0f, 1f))

        val width = variance.size
        val flat = BooleanArray(width) { variance[it] <= FLAT }

        val panels = mutableListOf<Panel>()
        var start: Int? = null
        var index = 0
        while (index < width) {
            if (flat[index]) {
                // A run of flat columns: a gutter if it is wide enough to be one.
                var end = index
                while (end < width && flat[end]) end += 1
                val gutter = (end - index).toFloat() / width
                if (gutter >= MIN_GUTTER) {
                    start?.let { panels += Panel(it.toFloat() / width, index.toFloat() / width) }
                    start = null
                } else if (start == null) {
                    start = index
                }
                index = end
            } else {
                if (start == null) start = index
                index += 1
            }
        }
        start?.let { panels += Panel(it.toFloat() / width, 1f) }

        val kept = panels.filter { it.width >= MIN_PANEL }
        return kept.ifEmpty { listOf(Panel(0f, 1f)) }
    }
}
