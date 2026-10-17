package com.bellizia.owcompanion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    private val sha = "516afee8cb83da47160de209d79a1e50746fba1a4a5ba4f9d420eb1cebaf0b08"
    private val own = "https://github.com/bdbais/ow-companion/releases/download/v1.13.0/ow-companion-v1.13.0.apk"

    private fun file(
        url: String = own,
        name: String = "ow-companion-v1.13.0.apk",
        size: Long = 37_212_609,
        digest: String? = "sha256:$sha",
    ) = ReleaseChecker.ReleaseFile(name = name, url = url, size = size, digest = digest)

    @Test
    fun `the app downloads an update only from this project's own releases`() {
        assertEquals(ReleaseChecker.Apk(own, 37_212_609, sha), ReleaseChecker.apkOf(listOf(file())))
        // Same file name, someone else's repository.
        assertNull(ReleaseChecker.apkOf(listOf(file(url = own.replace("bdbais", "attacker")))))
        // The repository's address as a prefix of a longer host.
        assertNull(ReleaseChecker.apkOf(listOf(file(url = "https://github.com/bdbais/ow-companion.evil/x.apk"))))
        assertNull(ReleaseChecker.apkOf(listOf(file(url = own.replace("https", "http")))))
    }

    @Test
    fun `without a stated hash there is nothing to check the download against`() {
        assertNull(ReleaseChecker.apkOf(listOf(file(digest = null))))
        assertNull(ReleaseChecker.apkOf(listOf(file(digest = "md5:0123"))))
        assertNull(ReleaseChecker.apkOf(listOf(file(digest = "sha256:short"))))
    }

    @Test
    fun `two APKs or none means the release page, not a guess`() {
        assertNull(ReleaseChecker.apkOf(emptyList()))
        assertNull(ReleaseChecker.apkOf(listOf(file(), file(name = "other.apk"))))
        // Notes or checksums beside the one APK do not make it ambiguous.
        assertEquals(sha, ReleaseChecker.apkOf(listOf(file(), file(name = "SHA256SUMS.txt")))?.sha256)
        assertNull(ReleaseChecker.apkOf(listOf(file(size = 0))))
        assertNull(ReleaseChecker.apkOf(listOf(file(size = ReleaseChecker.MAX_APK_BYTES + 1))))
    }
}
