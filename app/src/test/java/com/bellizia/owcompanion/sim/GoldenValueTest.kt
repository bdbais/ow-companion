package com.bellizia.owcompanion.sim

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Holds this Kotlin engine to the behaviour of the original JavaScript implementation.
 *
 * `tools/js_oracle/generate_golden.js` runs upstream's own code over a grid of distances,
 * crosshair placements and modifier combinations and records what it produces. The two
 * implementations share no code, so agreement here is real evidence the port is faithful.
 *
 * Weapons with spread are stochastic and cannot be compared exactly. They are split into
 * two kinds of assertion:
 *  - quantities that do not depend on the RNG (damage per pellet, shot timing, spread
 *    radius, shot count) must match to floating-point precision;
 *  - sampled quantities are averaged over the same number of trials and must agree within
 *    a few standard errors of the recorded mean.
 */
class GoldenValueTest {

    // --- golden file model --------------------------------------------------------------

    @Serializable
    private data class Golden(val configs: List<GoldenConfig>, val weapons: List<GoldenWeapon>)

    @Serializable
    private data class GoldenConfig(
        val name: String,
        val distance: Double,
        val x: Double,
        val z: Double,
        val mods: List<String>,
    )

    @Serializable
    private data class GoldenWeapon(
        val name: String,
        val hero: String,
        val mousebutton: String? = null,
        val cases: List<GoldenCase>,
    )

    @Serializable
    private data class GoldenCase(
        val config: String,
        val deterministic: Boolean,
        val basicDmg: Double,
        val hitDmg: Double,
        val critDmg: Double? = null,
        val pellets: Double? = null,
        val shotCount: Int,
        val shotTimes: List<Double>,
        val shotRadii: List<Double>,
        val shotDurations: List<Double?>,
        val trials: Int,
        val dps: Double,
        val dpsStdErr: Double,
        val accuracy: Double,
        val accuracyStdErr: Double,
        val critAccuracy: Double,
        val critAccuracyStdErr: Double,
        val rhkt: Double? = null,
        val rhktInfiniteRatio: Double,
    )

    private val json = Json { ignoreUnknownKeys = true }

    private fun <T> load(resource: String, deserialize: (String) -> T): T {
        val text = checkNotNull(javaClass.getResourceAsStream(resource)) {
            "missing test resource $resource - run the generators in tools/js_oracle/"
        }.bufferedReader().readText()
        return deserialize(text)
    }

    private val weaponSet: WeaponSet =
        load("/weapons-2020.json") { json.decodeFromString(WeaponSet.serializer(), it) }

    private val golden: Golden =
        load("/golden-2020.json") { json.decodeFromString(Golden.serializer(), it) }

    private fun modifiersOf(names: List<String>) = Modifiers(
        armor = "armor" in names,
        nanoboostDefence = "nanoboost_def" in names,
        takeABreather = "take_a_breather" in names,
        fortify = "fortify" in names,
        damageBoost = "damage_boost" in names,
        supercharger = "supercharger" in names,
        nanoboostOffence = "nanoboost_off" in names,
        amplificationMatrix = "ampl_matrix" in names,
        discord = "discord" in names,
    )

    /** Relative comparison, with an absolute floor so values near zero stay comparable. */
    private fun assertClose(label: String, expected: Double, actual: Double, relative: Double = 1e-9) {
        val tolerance = relative * abs(expected) + 1e-9
        assertTrue(
            "$label: expected $expected but was $actual (tolerance $tolerance)",
            abs(expected - actual) <= tolerance,
        )
    }

    /**
     * Both sides are means of `trials` samples, so their difference has roughly sqrt(2)
     * times the recorded standard error. Six of those is a ~2e-9 false-failure rate per
     * assertion, which keeps a suite of this size quiet without hiding a real regression.
     */
    private fun assertWithinSamplingError(
        label: String,
        expected: Double,
        actual: Double,
        stdErr: Double,
    ) {
        val tolerance = 6.0 * stdErr * sqrt(2.0) + 1e-6 * abs(expected) + 1e-9
        assertTrue(
            "$label: expected $expected but was $actual " +
                "(tolerance $tolerance, std err $stdErr)",
            abs(expected - actual) <= tolerance,
        )
    }

    @Test
    fun `every weapon in the golden file is present in the dataset`() {
        val datasetIds = weaponSet.weapons.map { it.id }.toSet()
        val goldenIds = golden.weapons.map {
            if (it.mousebutton == null) it.name else "${it.name} (${it.mousebutton})"
        }.toSet()
        assertEquals(goldenIds, datasetIds)
        assertEquals(46, weaponSet.weapons.size)
        assertEquals(32, weaponSet.heroes.size)
    }

    @Test
    fun `engine reproduces the reference implementation`() {
        val configs = golden.configs.associateBy { it.name }
        val simulator = Simulator()
        var comparisons = 0

        for (goldenWeapon in golden.weapons) {
            val spec = weaponSet.weapons.single {
                it.name == goldenWeapon.name && it.mousebutton == goldenWeapon.mousebutton
            }
            val model = WeaponModel(spec)

            for (case in goldenWeapon.cases) {
                val config = configs.getValue(case.config)
                val label = "${spec.id} @ ${case.config}"
                val crosshair = Crosshair(x = config.x, z = config.z, distance = config.distance)
                val modifiers = modifiersOf(config.mods)

                assertEquals(
                    "$label: determinism classification",
                    case.deterministic,
                    simulator.isDeterministic(model),
                )

                val train = simulator.simulateMean(
                    model = model,
                    crosshair = crosshair,
                    modifiers = modifiers,
                    trials = case.trials,
                )

                // RNG-independent: must match exactly.
                assertClose("$label basicDamage", case.basicDmg, train.basicDamage)
                assertClose("$label hitDamage", case.hitDmg, train.hitDamage)
                case.critDmg?.let { assertClose("$label critDamage", it, train.critDamage) }
                case.pellets?.let { assertClose("$label pellets", it, train.pellets) }
                assertEquals("$label shot count", case.shotCount, train.shots.size)

                case.shotTimes.forEachIndexed { index, expected ->
                    assertClose("$label shot[$index].time", expected, train.shots[index].time)
                }
                case.shotRadii.forEachIndexed { index, expected ->
                    assertClose("$label shot[$index].radius", expected, train.shots[index].radius)
                }
                case.shotDurations.forEachIndexed { index, expected ->
                    assertEquals(
                        "$label shot[$index].duration presence",
                        expected != null,
                        train.shots[index].duration != null,
                    )
                    if (expected != null) {
                        assertClose(
                            "$label shot[$index].duration",
                            expected,
                            train.shots[index].duration!!,
                        )
                    }
                }

                // Sampled: exact when deterministic, statistical otherwise.
                if (case.deterministic) {
                    assertClose("$label dps", case.dps, train.dps)
                    assertClose("$label accuracy", case.accuracy, train.accuracy)
                    assertClose("$label critAccuracy", case.critAccuracy, train.critAccuracy)
                } else {
                    assertWithinSamplingError("$label dps", case.dps, train.dps, case.dpsStdErr)
                    assertWithinSamplingError(
                        "$label accuracy", case.accuracy, train.accuracy, case.accuracyStdErr,
                    )
                    assertWithinSamplingError(
                        "$label critAccuracy",
                        case.critAccuracy,
                        train.critAccuracy,
                        case.critAccuracyStdErr,
                    )
                }

                // Time to kill is a nonlinear statistic, so it only gets a loose bound when
                // sampled; what matters most is agreeing on whether a kill happens at all.
                val expectedKill = case.rhkt
                if (expectedKill == null) {
                    assertTrue(
                        "$label expected no kill but was ${train.timeToKill}",
                        !train.timeToKill.isFinite(),
                    )
                } else if (case.rhktInfiniteRatio == 0.0) {
                    assertTrue(
                        "$label expected a kill but was infinite",
                        train.timeToKill.isFinite(),
                    )
                    if (case.deterministic) {
                        assertClose("$label timeToKill", expectedKill, train.timeToKill)
                    } else {
                        assertClose("$label timeToKill", expectedKill, train.timeToKill, 0.05)
                    }
                }

                comparisons++
            }
        }

        assertEquals("expected every weapon x config pair to be checked", 46 * 11, comparisons)
    }
}
