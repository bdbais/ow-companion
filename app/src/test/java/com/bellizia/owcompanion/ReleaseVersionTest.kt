package com.bellizia.owcompanion

import com.bellizia.owcompanion.data.ReleaseChecker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The update banner is the one piece of UI a reader cannot get rid of by being careful, so
 * the comparison behind it has to be right in both directions: never nagging about a version
 * they already have, and never staying quiet about one they do not.
 */
class ReleaseVersionTest {

    @Test
    fun `a higher version is offered`() {
        assertTrue(ReleaseChecker.isNewer("v0.2.0", "0.1.0"))
        assertTrue(ReleaseChecker.isNewer("0.1.1", "0.1.0"))
        assertTrue(ReleaseChecker.isNewer("v1.0.0", "0.9.9"))
    }

    @Test
    fun `the installed version and older ones are not`() {
        assertFalse(ReleaseChecker.isNewer("v0.2.0", "0.2.0"))
        assertFalse(ReleaseChecker.isNewer("0.1.0", "0.2.0"))
        assertFalse(ReleaseChecker.isNewer("v0.9.0", "0.10.0"))
    }

    @Test
    fun `numbers are compared as numbers, not as text`() {
        // The string comparison that "0.10.0" < "0.9.0" would be silently wrong forever.
        assertTrue(ReleaseChecker.isNewer("0.10.0", "0.9.0"))
        assertTrue(ReleaseChecker.isNewer("0.2.10", "0.2.9"))
    }

    @Test
    fun `an unparseable tag means no prompt rather than a crash`() {
        assertFalse(ReleaseChecker.isNewer("nightly", "0.2.0"))
        assertFalse(ReleaseChecker.isNewer("", "0.2.0"))
        // Shorter is not older: 1.0 and 1.0.0 are the same release.
        assertFalse(ReleaseChecker.isNewer("1.0", "1.0.0"))
    }
}
