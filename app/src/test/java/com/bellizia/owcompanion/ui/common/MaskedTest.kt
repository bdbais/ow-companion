package com.bellizia.owcompanion.ui.common

import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The unmasking, checked on values that mean nothing.
 *
 * Worth a test because a wrong character in a packed constant does not fail the build and
 * does not throw: it quietly decodes to something else, and whatever depended on it simply
 * stops matching. The samples here are deliberately dull - the point is the arithmetic, and
 * a test is a poor place to write down what the real constants say.
 */
class MaskedTest {

    @Test
    fun `text comes back as it went in`() {
        assertEquals("hello", Masked.text("MsBQrwY="))
    }

    @Test
    fun `a list splits on the separator`() {
        assertEquals(listOf("one", "two", "three"), Masked.list("NctZ3B3hNbpIqxvzPw=="))
    }

    @Test
    fun `multi-byte characters survive the round trip`() {
        // Masking is per byte and UTF-8 is not one byte per character, so a key that is not
        // applied over the encoded bytes would corrupt exactly this case and nothing else.
        assertEquals("caffè", Masked.text("OcRapao+"))
    }

    @Test
    fun `a single value is a list of one`() {
        assertEquals(listOf("hello"), Masked.list("MsBQrwY="))
    }

    @Test
    fun `nothing readable is left in the packed form`() {
        // The whole point: the packed constant must not contain what it decodes to.
        assertTrue("MsBQrwY=".contains("hello").not())
    }

    /**
     * The constants that are actually in use, checked without writing down what they say.
     *
     * A mistyped character in any of them costs nothing at build time and throws nothing at
     * run time - it decodes to a different string, and whatever compared against it simply
     * stops matching, silently and forever. Comparing fingerprints catches that while
     * keeping this file as uninformative as the constants themselves.
     *
     * If one of these ever fails, the packed constant was edited, not the fingerprint.
     */
    @Test
    fun `the packed constants still say what they said`() {
        assertEquals(
            "2d2370db2447ff8cf4f3accd68c85aa119a9c893effd200a9b69176e9fc5eb98",
            fingerprint("PtBfqA=="),
        )
        assertEquals(
            "1702fc0bdbf8468d937c497a37a7828027752368a6d6b04521f249d3160cb8b7",
            fingerprint("DcxYrB77O85ZsXbXNMQjghr+Pw=="),
        )
        assertEquals(
            "008300462b2c75a2ca6283e030c0d199fc7d7df3ff3d4984c82a7604749f4df6",
            fingerprint("LspOoQP5KMsjsBD7N8BIsQg="),
        )
        assertEquals(
            "cd7dd52241747e442dcd59691bb223a0f2ef491cb9adce9729942c456973a607",
            fingerprint("A+dm"),
        )
    }

    /**
     * The packed label lists, checked for length as well as content.
     *
     * These are read by index, so a constant that decodes to one part too few does not
     * misbehave quietly the way a wrong trigger does - it throws, on screen, in the one
     * place nobody is watching. The count is the assertion that matters; the fingerprint
     * catches the subtler case where every part is present and one of them is wrong.
     */
    @Test
    fun `the packed label lists are whole`() {
        assertEquals(9, Masked.list(HOLD).size)
        assertEquals(
            "47f709f93be400958c0e8fc934170daf185fec6de7cf269066ba2e1652fb2cf7",
            fingerprint(HOLD),
        )

        assertEquals(10, Masked.list(RANGE).size)
        assertEquals(
            "5f2ae7b6da9a60ea809f3e5affa0454648b9b7c2770e6bd3d2f171459e953fe7",
            fingerprint(RANGE),
        )

        assertEquals(2, Masked.list(VAULT).size)
        assertEquals(
            "41948ddd435d88997a30fd14b27f0826dbb529cadeb07f2ad9f87596657dfe12",
            fingerprint(VAULT),
        )
    }

    private fun fingerprint(packed: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(Masked.text(packed).toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

    private companion object {
        // Copies of the constants the panels hold. Duplicated on purpose: each of those is
        // private to its own file, and widening it so a test can reach it would undo the
        // point of packing it in the first place.
        const val HOLD = "CeZugjmJDeRqhnbaE+t53CXTG/N53DrTFOEclyHTevJ9lSyJDu154yXfFOAcgTvZ" +
            "EeAc46sheoUZ81vyevJ9lSzFRfRpiirdRe15gj/PRfZwjD7fFOI="
        const val RANGE = "CeZzkSyJCfFuhijdRfF1jiyJHupyhnbEFfByh0nZDOBu3DrVFfd540m2f5ULp3bU" +
            "H/Zo4zrCCOB9iEm2eoAM8Q2JHvdzkznTHoUc40ymaMEjgknYH/IcgSzFDrp+hjrCeoUc5lmhPg=="
        const val VAULT = "FfV5jXbUG+Z3"
    }
}
