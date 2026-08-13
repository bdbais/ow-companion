package com.bellizia.owcompanion.ui.common

import kotlin.math.hypot
import kotlin.random.Random

/**
 * Holding a point against machines that keep coming, stepped by elapsed seconds.
 *
 * Emplacements sit beside a path; walkers follow it; anything that reaches the far end
 * costs you. The whole loop is place, watch, place better - and the interesting decision is
 * never which emplacement is strongest but where the path doubles back on itself, because
 * that is where one gun covers two stretches.
 *
 * Nothing here touches Android or Compose, so a whole defence can be played out in a test.
 */
internal class HoldModel(seed: Long = 0L, val ground: Int = 0) {

    /** What can be put down, and what it costs. */
    enum class Emplacement(
        val cost: Int,
        val reach: Float,
        val hit: Float,
        /** Seconds between shots. */
        val interval: Float,
    ) {
        /** Cheap, short, quick. The one you buy first and keep buying. */
        Sentry(cost = 40, reach = 0.20f, hit = 9f, interval = 0.28f),

        /** Twice the reach and four times the punch, at four times the price. */
        Emplaced(cost = 160, reach = 0.30f, hit = 42f, interval = 0.85f),

        /** Slows everything it touches instead of killing it. */
        Field(cost = 90, reach = 0.24f, hit = 3f, interval = 0.5f),
    }

    data class Gun(
        val id: Int,
        val kind: Emplacement,
        val x: Float,
        val y: Float,
        var cooldown: Float = 0f,
        /** Seconds left of its muzzle flash. */
        var flash: Float = 0f,
    )

    data class Walker(
        val id: Int,
        /** How far along the path, 0 to 1. */
        var along: Float,
        var health: Float,
        val maxHealth: Float,
        val speed: Float,
        val heavy: Boolean,
        var slowed: Float = 0f,
        var flash: Float = 0f,
    ) {
        var x: Float = 0f
        var y: Float = 0f
    }

    enum class Stage { Placing, Running, Lost }

    private val random = Random(seed)
    private var nextId = 0
    private var releaseIn = 1.2f
    private var toRelease = 0

    /** The lane, as points the walkers cross in order. Themed per ground, and nothing more. */
    val path: List<Pair<Float, Float>> = LANES[ground % LANES.size]

    val guns = mutableListOf<Gun>()
    val walkers = mutableListOf<Walker>()

    var stage: Stage = Stage.Placing
        private set

    var scrap: Int = 120
        private set

    var wave: Int = 0
        private set

    /** How many may still get through before it is over. */
    var integrity: Int = 5
        private set

    var downed: Int = 0
        private set

    fun place(kind: Emplacement, x: Float, y: Float): Boolean {
        if (scrap < kind.cost) return false
        // Not on the lane itself, and not on top of another gun.
        if (distanceToPath(x, y) < LANE_WIDTH) return false
        if (guns.any { hypot((it.x - x).toDouble(), (it.y - y).toDouble()) < 0.06 }) return false

        guns += Gun(id = nextId++, kind = kind, x = x, y = y)
        scrap -= kind.cost
        return true
    }

    /** Starts the next wave. Between waves nothing walks, which is when you build. */
    fun advance() {
        if (stage == Stage.Lost) return
        wave += 1
        toRelease = 4 + wave * 2
        releaseIn = 0.4f
        stage = Stage.Running
    }

    fun step(dt: Float) {
        if (stage != Stage.Running) return

        guns.forEach { gun ->
            gun.cooldown -= dt
            if (gun.flash > 0f) gun.flash = (gun.flash - dt).coerceAtLeast(0f)
        }
        walkers.forEach { walker ->
            if (walker.flash > 0f) walker.flash = (walker.flash - dt).coerceAtLeast(0f)
            if (walker.slowed > 0f) walker.slowed = (walker.slowed - dt).coerceAtLeast(0f)

            val pace = if (walker.slowed > 0f) walker.speed * SLOW_FACTOR else walker.speed
            walker.along += pace * dt
            locate(walker)
        }

        fire(dt)

        // Anything that reaches the end costs a point of the line.
        walkers.removeAll { walker ->
            val through = walker.along >= 1f
            if (through) integrity -= 1
            through
        }
        walkers.removeAll { walker ->
            val dead = walker.health <= 0f
            if (dead) {
                downed += 1
                scrap += if (walker.heavy) 45 else 12
            }
            dead
        }

        if (integrity <= 0) {
            integrity = 0
            stage = Stage.Lost
            return
        }

        if (toRelease > 0) {
            releaseIn -= dt
            if (releaseIn <= 0f) release()
        } else if (walkers.isEmpty()) {
            // Wave cleared: back to building, and the pause is the reward.
            stage = Stage.Placing
        }
    }

    private fun fire(dt: Float) {
        guns.forEach { gun ->
            if (gun.cooldown > 0f) return@forEach
            val target = walkers
                .filter { hypot((it.x - gun.x).toDouble(), (it.y - gun.y).toDouble()) <= gun.kind.reach }
                // The one furthest along is the one about to cost you something.
                .maxByOrNull { it.along } ?: return@forEach

            target.health -= gun.kind.hit
            target.flash = FLASH
            if (gun.kind == Emplacement.Field) target.slowed = SLOW_SECONDS
            gun.cooldown = gun.kind.interval
            gun.flash = FLASH
        }
    }

    private fun release() {
        toRelease -= 1
        releaseIn = (0.9f - wave * 0.04f).coerceAtLeast(0.3f)
        // One in five is a heavy, from the third wave on.
        val heavy = wave >= 3 && random.nextFloat() < 0.2f
        val health = if (heavy) 120f + wave * 30f else 34f + wave * 11f
        val walker = Walker(
            id = nextId++,
            along = 0f,
            health = health,
            maxHealth = health,
            speed = (if (heavy) 0.035f else 0.055f) + wave * 0.002f,
            heavy = heavy,
        )
        locate(walker)
        walkers += walker
    }

    /** Puts a walker where its progress says it is, between two points of the lane. */
    private fun locate(walker: Walker) {
        val clamped = walker.along.coerceIn(0f, 1f)
        val span = 1f / (path.size - 1)
        val leg = (clamped / span).toInt().coerceIn(0, path.size - 2)
        val within = (clamped - leg * span) / span
        val (ax, ay) = path[leg]
        val (bx, by) = path[leg + 1]
        walker.x = ax + (bx - ax) * within
        walker.y = ay + (by - ay) * within
    }

    /** Distance from a point to the nearest leg of the lane, roughly enough to forbid it. */
    private fun distanceToPath(x: Float, y: Float): Float {
        var best = Float.MAX_VALUE
        for (index in 0 until path.size - 1) {
            val (ax, ay) = path[index]
            val (bx, by) = path[index + 1]
            // Sampled rather than solved: a dozen points a leg is plenty to keep a gun off
            // the road, and the arithmetic stays something a reader can check.
            repeat(12) { step ->
                val t = step / 11f
                val px = ax + (bx - ax) * t
                val py = ay + (by - ay) * t
                val d = hypot((px - x).toDouble(), (py - y).toDouble()).toFloat()
                if (d < best) best = d
            }
        }
        return best
    }

    private companion object {
        const val FLASH = 0.12f
        const val SLOW_SECONDS = 0.8f
        const val SLOW_FACTOR = 0.55f
        const val LANE_WIDTH = 0.09f

        /**
         * Three lanes, each a different shape of problem.
         *
         * The first doubles back so one gun covers two stretches; the second is long and
         * open and wants reach; the third is a spiral that rewards a single strong corner.
         */
        val LANES: List<List<Pair<Float, Float>>> = listOf(
            listOf(0f to 0.2f, 0.7f to 0.2f, 0.7f to 0.5f, 0.2f to 0.5f, 0.2f to 0.8f, 1f to 0.8f),
            listOf(0f to 0.5f, 0.3f to 0.15f, 0.6f to 0.85f, 0.85f to 0.3f, 1f to 0.5f),
            listOf(
                0f to 0.1f, 0.85f to 0.1f, 0.85f to 0.9f, 0.15f to 0.9f,
                0.15f to 0.35f, 0.6f to 0.35f, 0.6f to 0.65f, 1f to 0.65f,
            ),
        )
    }
}
