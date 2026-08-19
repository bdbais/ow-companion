package com.bellizia.owcompanion.ui.comics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Which address the comics link opens.
 *
 * The ten supported locales were measured, not guessed: each returns 200 from
 * overwatch.blizzard.com, while zh-cn, uk-ua, sv-se, ar-sa and tr-tr answer 307 and redirect
 * away. That is the same ten the game is published in, which is why the list matches
 * `tools/fetch_names.py` exactly - and this test fails if the two ever drift, because
 * sending a Turkish reader to a redirect is worse than sending them to English on purpose.
 */
class OfficialComicsTest {

    @Test
    fun `the link opens the comics, not the whole gallery`() {
        // Without the fragment the page lists everything, and everything is 470 images
        // against 130 comics. Verified in a browser: with it, the comics tab is selected
        // and no other kind of card is on screen.
        assertTrue(OfficialComics.gallery(Locale.ITALIAN).endsWith("#tab=comics"))
    }

    @Test
    fun `a published language gets its own gallery`() {
        assertEquals(
            "https://overwatch.blizzard.com/it-it/media/#tab=comics",
            OfficialComics.gallery(Locale.ITALIAN),
        )
        assertEquals(
            "https://overwatch.blizzard.com/pt-br/media/#tab=comics",
            OfficialComics.gallery(Locale.forLanguageTag("pt-BR")),
        )
        assertEquals(
            "https://overwatch.blizzard.com/pl-pl/media/#tab=comics",
            OfficialComics.gallery(Locale.forLanguageTag("pl")),
        )
    }

    @Test
    fun `the two Chinese scripts are not the same language`() {
        // Traditional is published; Simplified is not, because the game is not operated
        // there. Telling them apart is the whole reason NamesRepository.key exists.
        assertEquals(
            "https://overwatch.blizzard.com/zh-tw/media/#tab=comics",
            OfficialComics.gallery(Locale.forLanguageTag("zh-TW")),
        )
        assertEquals(
            "https://overwatch.blizzard.com/en-us/media/#tab=comics",
            OfficialComics.gallery(Locale.forLanguageTag("zh-CN")),
        )
    }

    @Test
    fun `the five unpublished languages fall back rather than redirect`() {
        listOf("uk", "sv", "ar", "tr", "zh-CN").forEach { tag ->
            val locale = Locale.forLanguageTag(tag)
            assertFalse("$tag should not claim to be published", OfficialComics.published(locale))
            assertEquals(
                "$tag should fall back to English",
                "https://overwatch.blizzard.com/en-us/media/#tab=comics",
                OfficialComics.gallery(locale),
            )
        }
    }

    @Test
    fun `every published language is marked as published`() {
        listOf("it", "es", "pt-BR", "fr", "de", "ja", "ko", "zh-TW", "ru", "pl").forEach { tag ->
            assertTrue(tag, OfficialComics.published(Locale.forLanguageTag(tag)))
        }
    }

    @Test
    fun `English is a real answer, not a missing one`() {
        // English is not in the table - it is the fallback - so this pins that a reader in
        // English still gets a working address rather than an empty string.
        assertEquals(
            "https://overwatch.blizzard.com/en-us/media/#tab=comics",
            OfficialComics.gallery(Locale.ENGLISH),
        )
    }
}
