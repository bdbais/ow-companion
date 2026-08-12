package com.bellizia.owcompanion.ui.common

import kotlin.math.max
import kotlin.random.Random

/**
 * A shooting range, stepped by elapsed seconds.
 *
 * Silhouettes rise, hold, and drop again. Hit one before it goes and it counts; let it go
 * and the streak breaks. That is the whole game, and the whole game is the point - it is
 * over in ninety seconds and it wants nothing from you but timing.
 *
 * Nothing here touches Android or Compose, so the result does not depend on the screen or
 * the frame rate and a whole round can be played out in a test.
 */
internal class RangeModel(seed: Long = 0L, val shooter: Shooter = Shooter.Rifle) {

    /**
     * Who is holding the gun.
     *
     * Three ways of being good at this, so the same range asks different things. The heavy
     * one hits hardest and punishes a miss hardest; the quick one forgives.
     */
    enum class Shooter(
        /** Score for a clean hit. */
        val worth: Int,
        /** Seconds the round is frozen after a miss. */
        val penalty: Float,
        /** How much of a silhouette counts as hit; a wider gun is easier to land. */
        val forgiveness: Float,
    ) {
        /** Slow, heavy, unforgiving. */
        Rifle(worth = 300, penalty = 0.55f, forgiveness = 0.9f),

        /** The middle: quicker, cheaper, steadier. */
        Repeater(worth = 150, penalty = 0.3f, forgiveness = 1.15f),

        /** Forgiving and fast, and worth the least. */
        Dart(worth = 100, penalty = 0.15f, forgiveness = 1.45f),
    }

    /** One silhouette, somewhere on the boards. */
    data class Mark(
        val id: Int,
        /** 0 to 1 across the range. */
        val x: Float,
        val y: Float,
        val radius: Float,
        /** Seconds it has been up. */
        var age: Float = 0f,
        /** Seconds it will stay up. */
        val life: Float,
        /** Which hero shape it wears; only the drawing cares. */
        val shape: Int,
        /** A friendly silhouette costs you for shooting it, which is the whole trick. */
        val friendly: Boolean = false,
        var struck: Boolean = false,
        /** Seconds left of its hit flash. */
        var flash: Float = 0f,
    )

    enum class Stage { Running, Finished }

    private val random = Random(seed)
    private var nextId = 0
    private var spawnIn = 0.6f
    private var frozen = 0f

    var stage: Stage = Stage.Running
        private set

    val marks = mutableListOf<Mark>()

    var score: Int = 0
        private set

    /** Consecutive hits. Every fifth one doubles what the next is worth. */
    var streak: Int = 0
        private set

    var best: Int = 0
        private set

    var missed: Int = 0
        private set

    var remaining: Float = ROUND_SECONDS
        private set

    /** Which board is showing, which is only ever a backdrop. */
    val stand: Int get() = ((ROUND_SECONDS - remaining) / (ROUND_SECONDS / STANDS)).toInt()
        .coerceIn(0, STANDS - 1)

    /** The multiplier a hit is worth right now. */
    val multiplier: Int get() = 1 + streak / 5

    fun step(dt: Float) {
        if (stage == Stage.Finished) return

        remaining -= dt
        if (remaining <= 0f) {
            remaining = 0f
            stage = Stage.Finished
            return
        }

        // A miss costs time rather than a life: the round is the budget, and standing still
        // for half a second is the punishment.
        if (frozen > 0f) {
            frozen = max(0f, frozen - dt)
            return
        }

        marks.forEach { mark ->
            mark.age += dt
            if (mark.flash > 0f) mark.flash = max(0f, mark.flash - dt)
        }
        // A silhouette that drops unhit breaks the streak. Friendlies are the exception:
        // letting one go is the correct play, so it costs nothing.
        marks.removeAll { mark ->
            val gone = mark.age >= mark.life
            if (gone && !mark.struck && !mark.friendly) {
                missed += 1
                streak = 0
            }
            gone || (mark.struck && mark.flash <= 0f)
        }

        spawnIn -= dt
        if (spawnIn <= 0f) release()
    }

    /**
     * A tap at this point, in the same 0-to-1 space the marks live in.
     *
     * @return the mark hit, or null for a miss - which the caller may want to draw.
     */
    fun shoot(x: Float, y: Float): Mark? {
        if (stage == Stage.Finished || frozen > 0f) return null

        val hit = marks.firstOrNull { mark ->
            if (mark.struck) return@firstOrNull false
            val reach = mark.radius * shooter.forgiveness
            val dx = mark.x - x
            val dy = mark.y - y
            dx * dx + dy * dy <= reach * reach
        }

        if (hit == null) {
            streak = 0
            frozen = shooter.penalty
            return null
        }

        hit.struck = true
        hit.flash = FLASH_SECONDS

        if (hit.friendly) {
            // Shooting one of your own is worse than missing: it takes the score back.
            score = max(0, score - shooter.worth)
            streak = 0
            frozen = shooter.penalty
            return hit
        }

        score += shooter.worth * multiplier
        streak += 1
        best = max(best, streak)
        return hit
    }

    private fun release() {
        // The range speeds up as it goes, and holds each silhouette for less.
        val progress = 1f - remaining / ROUND_SECONDS
        spawnIn = (0.85f - progress * 0.45f).coerceAtLeast(0.22f)
        val life = (1.45f - progress * 0.65f).coerceAtLeast(0.55f)

        // Friendlies arrive once the range is worth paying attention to, and never as the
        // first thing anyone sees.
        val friendly = progress > 0.15f && random.nextFloat() < FRIENDLY_SHARE

        marks += Mark(
            id = nextId++,
            x = 0.1f + random.nextFloat() * 0.8f,
            y = 0.18f + random.nextFloat() * 0.62f,
            radius = 0.075f + random.nextFloat() * 0.03f,
            life = life,
            shape = random.nextInt(SHAPES),
            friendly = friendly,
        )
    }

    companion object {
        const val ROUND_SECONDS = 90f
        const val FLASH_SECONDS = 0.22f

        /** How many backdrops a round moves through. */
        const val STANDS = 5

        /** Distinct silhouettes to draw. */
        const val SHAPES = 6

        private const val FRIENDLY_SHARE = 0.18f
    }
}
