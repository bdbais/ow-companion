package com.bellizia.owcompanion.sim

import kotlin.math.tan

/**
 * Zoom sensitivity, for the setting Overwatch calls "Relative Aim Sensitivity While Zoomed".
 *
 * The setting is a percentage of the unscoped sensitivity, set per hero. At 100% the angular
 * sensitivity is unchanged, so a full turn takes the same distance of mouse either way - but
 * the world is magnified, so on the screen everything moves further and scoping feels
 * faster. Matching how it *looks* rather than how far it turns means lowering it, and there
 * are two defensible ways to do that.
 *
 * The arithmetic needs one number per scope: the field of view it zooms to. Blizzard do not
 * publish it. The two below were measured by players and can be checked against the figures
 * the community has settled on - 50.94 with a 103 view reproduces both 37.89% and 49.46%
 * exactly, which is why they are trusted here and why nothing else is guessed at.
 */
object Sensitivity {

    /** The widest the game's field of view slider goes, and what most players use. */
    const val DEFAULT_FOV = 103.0

    /** How a scoped view is matched to the unscoped one. */
    enum class Match {
        /**
         * Movement at the centre of the screen matches, following the perspective properly.
         * Known in the community as 0% monitor distance; 37.89% on Ana and Widowmaker.
         */
        Centre,

        /**
         * The plain ratio of the two fields of view. Cruder - it ignores that a screen is
         * flat - but it holds up better towards the edges. 49.46% on Ana and Widowmaker.
         */
        Ratio,
    }

    /**
     * A scope whose zoomed field of view is known.
     *
     * `fov` is the horizontal field of view while scoped, in degrees, at 16:9.
     */
    data class Scope(val hero: String, val weapon: String, val fov: Double)

    /**
     * Only the scopes whose field of view has actually been measured.
     *
     * Ashe is deliberately absent. Her iron sights magnify less, and the figures passed
     * around for her are estimates from the shape of the sight rather than measurements, so
     * she is a value to be typed in rather than one to be shipped as fact.
     */
    val KNOWN = listOf(
        Scope("Ana", "Biotic Rifle", 50.94),
        Scope("Widowmaker", "Widow's Kiss", 50.94),
    )

    private fun halfTan(degrees: Double): Double = tan(Math.toRadians(degrees / 2.0))

    /**
     * The percentage to put in the setting so that a scope matches the hip view.
     *
     * @param baseFov the field of view slider, 80 to 103.
     * @param zoomFov the field of view the scope zooms to.
     */
    fun relative(baseFov: Double, zoomFov: Double, match: Match): Double {
        require(baseFov > 0 && zoomFov > 0) { "a field of view has to be positive" }
        return when (match) {
            Match.Centre -> halfTan(zoomFov) / halfTan(baseFov) * 100.0
            Match.Ratio -> zoomFov / baseFov * 100.0
        }
    }

    /**
     * The setting that gives one scope the feel another already has.
     *
     * The field of view slider cancels out: two scopes compared against the same hip view
     * are compared against each other, so this holds whatever the slider is set to. That is
     * the useful case - a hero arrives, and the question is what makes them feel like the
     * one already in the hands.
     */
    fun convert(
        fromSens: Double,
        fromZoomFov: Double,
        toZoomFov: Double,
        match: Match,
    ): Double {
        require(fromZoomFov > 0 && toZoomFov > 0) { "a field of view has to be positive" }
        return when (match) {
            Match.Centre -> fromSens * halfTan(toZoomFov) / halfTan(fromZoomFov)
            Match.Ratio -> fromSens * toZoomFov / fromZoomFov
        }
    }

    /**
     * A zoomed field of view worked out from a magnification.
     *
     * Players quote scopes as "2x" or "1.5x", and mean the ratio of the two fields of view
     * rather than anything optical: Ana and Widowmaker are called 2x, and 103 / 50.94 is
     * 2.02. It is a way in for a scope nobody has measured, and no better than the figure
     * it is given.
     */
    fun fovFromMagnification(baseFov: Double, magnification: Double): Double {
        require(magnification > 0) { "a magnification has to be positive" }
        return baseFov / magnification
    }
}
