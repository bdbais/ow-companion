package com.bellizia.owcompanion

import com.bellizia.owcompanion.data.MetaRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rates page is someone else's HTML and can change shape without warning. These tests
 * pin the two things that would break silently: that the table is read out of the `allrows`
 * attribute at all, and that a shape change yields an empty list - which the UI reports as
 * an error - rather than a list of zeroes presented as fact.
 */
class MetaRatesTest {

    private val page = """
        <blz-rates-table allrows="[{&quot;id&quot;:&quot;zarya&quot;,&quot;cells&quot;:
        {&quot;name&quot;:&quot;Zarya&quot;,&quot;winrate&quot;:49.4,&quot;pickrate&quot;:
        6.2,&quot;banrate&quot;:48.1},&quot;hero&quot;:{&quot;name&quot;:&quot;Zarya&quot;,
        &quot;portrait&quot;:&quot;https://example.invalid/z.png&quot;,&quot;subrole&quot;:
        &quot;bruiser&quot;,&quot;role&quot;:&quot;TANK&quot;}},{&quot;id&quot;:
        &quot;ana&quot;,&quot;cells&quot;:{&quot;name&quot;:&quot;Ana&quot;,
        &quot;winrate&quot;:47.7,&quot;pickrate&quot;:25.9,&quot;banrate&quot;:0},
        &quot;hero&quot;:{&quot;name&quot;:&quot;Ana&quot;,&quot;portrait&quot;:null,
        &quot;subrole&quot;:&quot;tactician&quot;,&quot;role&quot;:&quot;SUPPORT&quot;}}]">
        </blz-rates-table>
    """.trimIndent().replace("\n", "")

    @Test
    fun `reads rates out of the allrows attribute`() {
        val heroes = MetaRepository.parse(page)
        assertEquals(2, heroes.size)

        val zarya = heroes.first { it.slug == "zarya" }
        assertEquals("Zarya", zarya.name)
        assertEquals(48.1, zarya.ban, 1e-9)
        assertEquals(6.2, zarya.pick, 1e-9)
        assertEquals(49.4, zarya.win, 1e-9)
        assertEquals("tank", zarya.role)
        assertEquals("bruiser", zarya.subrole)
        assertEquals("https://example.invalid/z.png", zarya.portrait)

        // A hero nobody bans is a real zero, not a missing value.
        assertEquals(0.0, heroes.first { it.slug == "ana" }.ban, 1e-9)
    }

    @Test
    fun `a page without the attribute yields nothing rather than zeroes`() {
        assertTrue(MetaRepository.parse("<html><body>maintenance</body></html>").isEmpty())
        assertTrue(MetaRepository.parse("""<div allrows="not json at all">""").isEmpty())
    }

    @Test
    fun `the role is applied here, because the site ignores it`() {
        val heroes = MetaRepository.parse(page)
        // The page returns every hero whatever role is asked for, which is why picking one
        // used to leave the list untouched.
        assertEquals(2, heroes.size)

        assertEquals(listOf("Zarya"), MetaRepository.byRole(heroes, "tank").map { it.name })
        assertEquals(listOf("Ana"), MetaRepository.byRole(heroes, "support").map { it.name })
        assertTrue(MetaRepository.byRole(heroes, "damage").isEmpty())

        // "All" is not a role, and the case it arrives in is not worth depending on.
        assertEquals(2, MetaRepository.byRole(heroes, "All").size)
        assertEquals(1, MetaRepository.byRole(heroes, "TANK").size)
    }

    @Test
    fun `filters go into the query string the site expects`() {
        val url = MetaRepository.url(
            MetaRepository.Filters(region = "Asia", tier = "Master", queue = "1", role = "tank"),
        )
        assertTrue(url, url.startsWith("https://overwatch.blizzard.com/en-us/rates/"))
        assertTrue(url, url.contains("&region=Asia"))
        assertTrue(url, url.contains("&tier=Master"))
        assertTrue(url, url.contains("&rq=1"))
        assertTrue(url, url.contains("&role=tank"))
    }
}
