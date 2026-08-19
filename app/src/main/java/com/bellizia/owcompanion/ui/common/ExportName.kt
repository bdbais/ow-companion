package com.bellizia.owcompanion.ui.common

/**
 * What an exported file is called, given whatever the reader named the thing.
 *
 * Letters and digits in any script survive; everything else becomes a dash, a run of dashes
 * collapses to one, and the ends are trimmed. The rule used to be `[^A-Za-z0-9-]` and threw
 * away the whole of a plan called 釜山の攻め or Атака на Пусан - the app speaks fifteen
 * languages and five of them write in none of those characters. Kotlin's letter test is
 * Unicode-aware, so it keeps what a filename can legally hold.
 *
 * A name that survives as nothing but dashes falls back to [fallback], which is what happens
 * to something titled entirely in punctuation and is better than a file called "-----".
 *
 * Lives here rather than inside either exporter because the tactics board and the comic
 * strip both need it, and because a rule this easy to get subtly wrong is worth one copy
 * that a test can call directly.
 */
fun exportName(raw: String, fallback: String = "board"): String =
    raw.map { if (it.isLetterOrDigit() || it == '-') it else '-' }
        .joinToString("")
        .replace(Regex("-{2,}"), "-")
        .trim('-')
        .ifBlank { fallback }
