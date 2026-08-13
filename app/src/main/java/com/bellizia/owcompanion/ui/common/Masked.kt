package com.bellizia.owcompanion.ui.common

import java.util.Base64

/**
 * Short strings the source does not spell out.
 *
 * The same idea as the packed artwork next door, and the same honesty about it: this is
 * obfuscation, not protection. The key is three lines down and anybody determined will read
 * it in a minute. What it buys is that a search across the repository does not turn these
 * up, which is the whole requirement.
 *
 * Values are UTF-8, masked against a repeating key, then Base64. Lists join their parts with
 * the unit separator, which no value here can contain.
 */
internal object Masked {

    private val KEY = byteArrayOf(0x5a, 0xa5.toByte(), 0x3c, 0xc3.toByte(), 0x69, 0x96.toByte())

    private const val SEPARATOR = '\u001F'

    fun text(packed: String): String {
        val raw = Base64.getDecoder().decode(packed)
        val plain = ByteArray(raw.size) { index ->
            (raw[index].toInt() xor KEY[index % KEY.size].toInt()).toByte()
        }
        return String(plain, Charsets.UTF_8)
    }

    fun list(packed: String): List<String> = text(packed).split(SEPARATOR)
}
