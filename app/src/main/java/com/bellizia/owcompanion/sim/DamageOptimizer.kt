package com.bellizia.owcompanion.sim

/** The best a weapon can do, and the conditions that get it there. */
data class DamagePeak(
    val spec: WeaponSpec,
    val dps: Double,
    /** What the same weapon does under the same conditions with no buffs at all. */
    val unbuffedDps: Double,
    /** Metres from the target. */
    val distance: Double,
    /** Height aimed at, in metres from the target's feet. */
    val aimZ: Double,
    /** Whether the winning aim point is the head. */
    val onHead: Boolean,
    val accuracy: Double,
    val critAccuracy: Double,
    val timeToKill: Double,
)

/**
 * Finds the distance and aim point at which a weapon deals the most damage per second.
 *
 * The answer is rarely obvious: a shotgun's spread cone tightens to nothing at point-blank
 * range but its falloff has not started, while a hitscan rifle wants the head and does not
 * care how far away it stands. Rather than reason about it per weapon, this sweeps the
 * range coarsely, then narrows around whatever won.
 *
 * Only amplifying modifiers are worth considering here — every one of them is strictly
 * positive, so the maximum always has all the allowed ones switched on, and there is
 * nothing to search over.
 */
class DamageOptimizer(
    private val simulator: Simulator = Simulator(),
    private val enemy: Enemy = Enemy.roadhog(),
) {
    fun bestFor(model: WeaponModel, modifiers: Modifiers): DamagePeak {
        val aimPoints = aimPoints()
        var best: Pair<Crosshair, ShotTrain>? = null

        fun consider(distance: Double) {
            for (aimZ in aimPoints) {
                val crosshair = Crosshair(x = 0.0, z = aimZ, distance = distance)
                val train = simulator.simulateMean(
                    model = model,
                    crosshair = crosshair,
                    modifiers = modifiers,
                    targetSamples = SEARCH_SAMPLES,
                    // This is a hunt for the maximum, so every weapon that can be wound
                    // up is wound up.
                    charge = FULL_CHARGE,
                )
                if (best == null || train.dps > best!!.second.dps) {
                    best = crosshair to train
                }
            }
        }

        CoarseDistances.forEach(::consider)

        // Narrow in on the winning range: falloff curves and range cutoffs make the peak a
        // sharp one, and the coarse grid can land either side of it.
        val coarseBest = best!!.first.distance
        val window = refineWindow(coarseBest)
        var step = window / REFINE_STEPS
        var centre = coarseBest
        repeat(REFINE_PASSES) {
            for (i in -REFINE_STEPS..REFINE_STEPS) {
                val distance = centre + i * step
                if (distance >= 0.0 && distance <= MaxDistance) consider(distance)
            }
            centre = best!!.first.distance
            step /= REFINE_STEPS
        }

        val (crosshair, train) = best!!
        // Re-measure the winner properly: the search runs at reduced precision, which is
        // fine for ranking but not for a number shown to the reader.
        val finalTrain =
            simulator.simulateMean(model, crosshair, modifiers, charge = FULL_CHARGE)
        val baseline =
            simulator.simulateMean(model, crosshair, Modifiers.NONE, charge = FULL_CHARGE)
        val headTop = (enemy.head as? CircleHitBox)?.let { it.centerZ + it.radius } ?: 0.0
        val headBottom = (enemy.head as? CircleHitBox)?.let { it.centerZ - it.radius } ?: 0.0

        return DamagePeak(
            spec = model.spec,
            dps = finalTrain.dps,
            unbuffedDps = baseline.dps,
            distance = crosshair.distance,
            aimZ = crosshair.z,
            onHead = crosshair.z in headBottom..headTop,
            accuracy = finalTrain.accuracy,
            critAccuracy = finalTrain.critAccuracy,
            timeToKill = finalTrain.timeToKill,
        )
    }

    /** Head centre, upper chest and centre mass: the three placements worth trying. */
    private fun aimPoints(): List<Double> {
        val head = (enemy.head as? CircleHitBox)?.centerZ ?: 2.0
        val body = (enemy.body as? RectHitBox)?.centerZ ?: 1.05
        return listOf(head, (head + body) / 2, body)
    }

    private fun refineWindow(distance: Double): Double {
        val index = CoarseDistances.indexOfFirst { it >= distance }.coerceAtLeast(0)
        val below = CoarseDistances.getOrElse(index - 1) { 0.0 }
        val above = CoarseDistances.getOrElse(index + 1) { MaxDistance }
        return (above - below) / 2
    }

    private companion object {
        const val MaxDistance = 60.0

        /**
         * Denser up close, where falloff curves start and spread cones are tight, and
         * sparser out past the range where most weapons have already given up.
         */
        val CoarseDistances = listOf(
            0.25, 0.5, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 8.0, 10.0, 12.0, 15.0,
            18.0, 22.0, 26.0, 30.0, 35.0, 40.0, 50.0, 60.0,
        )

        const val REFINE_STEPS = 3
        const val REFINE_PASSES = 2

        /** Lower precision than the chart: enough to rank, cheap enough to sweep. */
        const val SEARCH_SAMPLES = 3_000

        const val FULL_CHARGE = 1.0
    }
}
