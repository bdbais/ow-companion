package com.bellizia.owcompanion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

/**
 * The three guards on what arrives over the network.
 *
 * None of these can be shown off in a screenshot, because each one exists for a day nobody
 * wants: the update banner told to open somewhere else, a dataset manifest edited to fetch
 * from a host that is not ours, a response that never stops arriving. The tests are the only
 * place their behaviour is visible, which is exactly why they are pinned.
 */
class UpdateSafetyTest {

    @Test
    fun `the update banner only opens this project's own pages`() {
        val own = "https://github.com/bdbais/ow-companion/releases/tag/v1.12.0"
        assertEquals(own, ReleaseChecker.trustedUrl(own))
        assertEquals(
            "https://github.com/bdbais/ow-companion",
            ReleaseChecker.trustedUrl("https://github.com/bdbais/ow-companion"),
        )

        // Anything else - another site, another repo, a prefix trick, or nothing at all -
        // falls back to the releases page, which is always right.
        listOf(
            "https://example.com/download.apk",
            "https://github.com/somebody-else/ow-companion/releases",
            "https://github.com/bdbais/ow-companion.evil.example.com/x",
            "http://github.com/bdbais/ow-companion/releases",
            "",
        ).forEach { candidate ->
            assertEquals(candidate, ReleaseChecker.RELEASES, ReleaseChecker.trustedUrl(candidate))
        }
    }

    @Test
    fun `the dataset manifest names files, it does not choose servers`() {
        val base = "https://raw.githubusercontent.com/bdbais/ow-companion/dataset-published"

        // What every published manifest has ever contained.
        assertEquals("$base/weapons.json", DatasetUpdater.resolveRelative(base, "weapons.json"))
        assertEquals("$base/wiki.json", DatasetUpdater.resolveRelative(base, "/wiki.json"))

        listOf(
            "https://evil.example.com/weapons.json",
            "http://evil.example.com/weapons.json",
            "ftp://evil.example.com/weapons.json",
        ).forEach { absolute ->
            assertThrows(IllegalArgumentException::class.java) {
                DatasetUpdater.resolveRelative(base, absolute)
            }
        }
    }

    @Test
    fun `a response within the cap arrives whole`() {
        val body = "già più velocità — 釜山"
        val read = ByteArrayInputStream(body.toByteArray()).readTextCapped(1_000)
        assertEquals(body, read)
    }

    @Test
    fun `a response past the cap is refused, not stored`() {
        val oversized = ByteArray(64 * 1024)
        assertThrows(IOException::class.java) {
            ByteArrayInputStream(oversized).readTextCapped(32 * 1024)
        }
    }

    @Test
    fun `the cap is a ceiling, not a truncation`() {
        // Exactly at the limit is fine: the cap exists to stop runaway responses, and a
        // dataset that legitimately grows to the boundary should still arrive.
        val exact = ByteArray(32 * 1024) { 'a'.code.toByte() }
        val read = ByteArrayInputStream(exact).readTextCapped(32 * 1024)
        assertEquals(32 * 1024, read.length)
    }
}
