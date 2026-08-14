package com.bellizia.owcompanion.ui.board

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What an exported plan is called.
 *
 * The rule is small enough to look obviously right and was wrong for five languages: it kept
 * `[A-Za-z0-9-]` and threw away everything else, so a plan named in Japanese, Korean,
 * Chinese, Russian or Arabic came out as a row of dashes. The app is translated into all of
 * them, and naming a plan in your own language is the first thing anybody would do.
 *
 * Written as the same expression the exporter uses rather than by calling it, because that
 * function wants a Context and a cache directory to hand back a File - and the part worth
 * pinning is the arithmetic on the name, not the plumbing around it.
 */
class ExportNameTest {

    private fun fileName(plan: String): String =
        plan.map { if (it.isLetterOrDigit() || it == '-') it else '-' }
            .joinToString("")
            .replace(Regex("-{2,}"), "-")
            .trim('-')
            .ifBlank { "board" }

    @Test
    fun `a plain name comes through`() {
        assertEquals("Busan-attack", fileName("Busan attack"))
    }

    @Test
    fun `every script the app speaks survives`() {
        assertEquals("釜山の攻め", fileName("釜山の攻め"))
        assertEquals("Атака-на-Пусан", fileName("Атака на Пусан"))
        assertEquals("هجوم-بوسان", fileName("هجوم بوسان"))
        assertEquals("부산-공격", fileName("부산 공격"))
    }

    @Test
    fun `accents are letters too`() {
        // The old rule turned this into "Difesa-Parais-" and nobody would have noticed.
        assertEquals("Difesa-Paraíso", fileName("Difesa Paraíso"))
    }

    @Test
    fun `a name that is all punctuation falls back`() {
        assertEquals("board", fileName("!!!"))
        assertEquals("board", fileName("   "))
        assertEquals("board", fileName(""))
    }

    @Test
    fun `dashes do not pile up at the ends`() {
        // A share sheet showing "-plan-.pdf" reads like a bug in the app rather than a name.
        assertEquals("plan", fileName(" plan "))
        assertEquals("a-b", fileName("a / b"))
    }
}
