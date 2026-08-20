package com.bellizia.owcompanion.data

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * Reads a response body, refusing to read past a ceiling.
 *
 * Every network read in this app used to be an unbounded `readText()`. The servers on the
 * other end are GitHub, a community-run API and Blizzard's site - none of them ours - and an
 * endpoint that is compromised, misconfigured, or simply having a bad day can answer with a
 * body of any size. Unbounded, that is an out-of-memory crash handed to whoever is on the
 * other side of the connection; capped, it is one failed refresh in an app that treats every
 * failed refresh as a non-event.
 *
 * The caps at the call sites are deliberately generous - several times the largest real
 * response ever seen from each source - because the point is to bound catastrophe, not to
 * break the app the day a dataset grows.
 */
internal fun InputStream.readTextCapped(maxBytes: Int): String {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        if (out.size() + read > maxBytes) {
            throw IOException("response exceeded $maxBytes bytes")
        }
        out.write(buffer, 0, read)
    }
    return out.toString("UTF-8")
}

/**
 * The same ceiling, for a response that is not text.
 *
 * Images arrive from a third-party generator over which this app has no control at all, so
 * the size of what it sends is exactly the sort of thing to bound before allocating it.
 */
internal fun InputStream.readBytesCapped(maxBytes: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        if (out.size() + read > maxBytes) {
            throw IOException("response exceeded $maxBytes bytes")
        }
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}
