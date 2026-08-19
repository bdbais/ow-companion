package com.bellizia.owcompanion.ui.board

import com.bellizia.owcompanion.ui.common.exportName
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What an exported plan or strip is called.
 *
 * The rule is small enough to look obviously right and was wrong for five languages: it kept
 * `[A-Za-z0-9-]` and threw away everything else, so a plan named in Japanese, Korean,
 * Chinese, Russian or Arabic came out as a row of dashes. The app is translated into all of
 * them, and naming a plan in your own language is the first thing anybody would do.
 *
 * This used to restate the exporter's expression instead of calling it, because the function
 * around it wanted a Context and a cache directory. The rule now lives on its own - the
 * comic exporter needs it too - so these assertions run against the shipping code rather
 * than a copy of it that could quietly drift.
 */
class ExportNameTest {

    @Test
    fun `a plain name comes through`() {
        assertEquals("Busan-attack", exportName("Busan attack"))
    }

    @Test
    fun `every script the app speaks survives`() {
        assertEquals("釜山の攻め", exportName("釜山の攻め"))
        assertEquals("Атака-на-Пусан", exportName("Атака на Пусан"))
        assertEquals("هجوم-بوسان", exportName("هجوم بوسان"))
        assertEquals("부산-공격", exportName("부산 공격"))
    }

    @Test
    fun `accents are letters too`() {
        // The old rule turned this into "Difesa-Parais-" and nobody would have noticed.
        assertEquals("Difesa-Paraíso", exportName("Difesa Paraíso"))
    }

    @Test
    fun `a name that is all punctuation falls back`() {
        assertEquals("board", exportName("!!!"))
        assertEquals("board", exportName("   "))
        assertEquals("board", exportName(""))
    }

    @Test
    fun `the caller chooses what to fall back to`() {
        // The comic exporter passes "strip", so an untitled strip is not called "board".
        assertEquals("strip", exportName("", fallback = "strip"))
        assertEquals("strip", exportName("???", fallback = "strip"))
    }

    @Test
    fun `dashes do not pile up at the ends`() {
        // A share sheet showing "-plan-.pdf" reads like a bug in the app rather than a name.
        assertEquals("plan", exportName(" plan "))
        assertEquals("a-b", exportName("a / b"))
    }
}
