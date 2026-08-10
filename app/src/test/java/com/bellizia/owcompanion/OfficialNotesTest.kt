package com.bellizia.owcompanion

import com.bellizia.owcompanion.data.OfficialNotes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialNotesTest {

    @Test
    fun `the address carries the language and the month`() {
        assertEquals(
            "https://overwatch.blizzard.com/it-it/news/patch-notes/live/2026/07",
            OfficialNotes.urlFor("2026-07-14", "it"),
        )
        assertEquals(
            "https://overwatch.blizzard.com/ja-jp/news/patch-notes/live/2016/05",
            OfficialNotes.urlFor("2016-05-24", "ja"),
        )
    }

    @Test
    fun `a language Blizzard does not translate goes to English, deliberately`() {
        // Swedish, Turkish, Arabic and Ukrainian all serve the English page. Asking for them
        // by name would look like it worked and show English under the wrong label.
        listOf("sv", "tr", "ar", "uk").forEach { language ->
            assertFalse(language, OfficialNotes.translated(language))
            assertTrue(
                language,
                OfficialNotes.urlFor("2026-07-14", language)!!.contains("/en-us/"),
            )
        }
        listOf("it", "de", "ja", "zh").forEach { assertTrue(it, OfficialNotes.translated(it)) }
    }

    @Test
    fun `a date it cannot read yields no link rather than a broken one`() {
        assertNull(OfficialNotes.urlFor("", "it"))
        assertNull(OfficialNotes.urlFor("2026", "it"))
        assertNull(OfficialNotes.urlFor("soon-ish", "it"))
    }
}
