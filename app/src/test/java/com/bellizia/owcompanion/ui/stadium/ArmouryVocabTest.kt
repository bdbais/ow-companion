package com.bellizia.owcompanion.ui.stadium

import com.bellizia.owcompanion.sim.WeaponSet
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every English word the armoury takes from the dataset has somewhere to be translated to.
 *
 * An item row used to read "Health 10" beside "Comune" - one line, two languages - because
 * the row printed the raw dataset field while the rarity next to it went through the lookup.
 * The eleven stats in the armoury all had translated strings already; nothing was missing
 * except the call.
 *
 * This runs against the shipped dataset rather than a fixture, because the failure being
 * guarded against is a *dataset update* introducing a twelfth stat. A fixture would keep
 * passing while the screen quietly went back to English.
 */
class ArmouryVocabTest {

    private val weapons: WeaponSet = Json { ignoreUnknownKeys = true }
        .decodeFromString(WeaponSet.serializer(), dataset().readText())

    @Test
    fun `the dataset is actually loaded`() {
        // Without this, an empty parse would make every other test in here vacuously green.
        assertTrue("no armoury items parsed", weapons.stadiumItems.size > 50)
    }

    @Test
    fun `every armoury stat has a translation`() {
        val unknown = weapons.stadiumItems
            .flatMap { it.buffs }
            .map { it.stat }
            .distinct()
            .filter { vocabId(it) == null }
            .sorted()
        assertEquals("add these to vocabId in StadiumScreen.kt", emptyList<String>(), unknown)
    }

    @Test
    fun `every rarity and category has a translation`() {
        val unknown = weapons.stadiumItems
            .flatMap { listOf(it.rarity, it.category) }
            .filter { it.isNotBlank() }
            .distinct()
            .filter { vocabId(it) == null }
            .sorted()
        assertEquals("add these to vocabId in StadiumScreen.kt", emptyList<String>(), unknown)
    }

    @Test
    fun `capitalisation in the data does not decide the language`() {
        // One item spells its stat "shields" where the other twelve spell it "Shields". Both
        // are the same word and both should read the same on screen.
        assertEquals(vocabId("Shields"), vocabId("shields"))
    }

    private fun dataset(): File =
        listOf("src/main/assets/weapons.json", "app/src/main/assets/weapons.json")
            .map(::File)
            .firstOrNull(File::exists)
            ?: error("weapons.json not found from ${File(".").absolutePath}")
}
