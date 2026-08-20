package com.bellizia.owcompanion.ui.comics

import com.bellizia.owcompanion.data.AiArt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What this app is willing to ask an image generator for.
 *
 * Two rules, and both are about what the app must never do rather than what it does. It must
 * never send a reader's own words to a generator, because an app that pipes free text into an
 * image model generates whatever anybody asks, under this app's name. And it must never ask
 * for an Overwatch character or a real map, because a machine's guess at somebody else's
 * copyrighted hero is exactly what this project avoids everywhere else.
 *
 * Neither is visible on screen, so the tests are where they live.
 */
class AiPromptTest {

    private val everyPrompt: List<String>
        get() = AiArt.Pose.entries.map { it.prompt } + AiArt.Scene.entries.map { it.prompt }

    @Test
    fun `no prompt names a hero, a map or the game`() {
        // A sample across the roster and the map pool, plus the game itself. If a prompt
        // ever starts naming these, this is the test that says so.
        val forbidden = listOf(
            "overwatch", "blizzard", "tracer", "reinhardt", "mercy", "genji", "ana",
            "widowmaker", "kiriko", "hanamura", "king's row", "ilios", "busan",
        )
        // Whole words, not substrings: "ana" inside "banana" is not a hero, and a test that
        // fires on that is a test somebody eventually deletes instead of reading.
        everyPrompt.forEach { prompt ->
            val words = prompt.lowercase().split(Regex("[^a-z']+")).toSet()
            forbidden.forEach { name ->
                val parts = name.split(" ")
                val present = if (parts.size == 1) {
                    parts[0] in words
                } else {
                    prompt.lowercase().contains(name)
                }
                assertFalse("$name appears in: $prompt", present)
            }
        }
    }

    @Test
    fun `figures are silhouettes of nobody in particular`() {
        AiArt.Pose.entries.forEach { pose ->
            val prompt = pose.prompt.lowercase()
            assertTrue(pose.name, prompt.contains("silhouette"))
            assertTrue(pose.name, prompt.contains("plain white background"))
            // The two failures worth asking against by name: a second person wandering in,
            // and the panel of scenery that defeats the cut-out.
            assertTrue(pose.name, prompt.contains("no second figure"))
            assertTrue(pose.name, prompt.contains("no panel"))
        }
    }

    @Test
    fun `scenes are empty places, so a panel is not already populated`() {
        AiArt.Scene.entries.forEach { scene ->
            val prompt = scene.prompt.lowercase()
            assertTrue(scene.name, prompt.contains("empty"))
            assertTrue(scene.name, prompt.contains("no people"))
            assertTrue(scene.name, prompt.contains("no text"))
        }
    }

    @Test
    fun `every pose and place is a distinct request`() {
        // A duplicated phrase would mean two buttons that quietly do the same thing.
        val prompts = everyPrompt
        assertEquals(prompts.size, prompts.toSet().size)
    }

    @Test
    fun `the credit names where the pictures come from`() {
        // The sheet tells people which service is drawing for them; this keeps that honest
        // if the service is ever changed.
        assertTrue(AiArt.CREDIT.isNotBlank())
    }
}
