package com.bellizia.owcompanion.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * How every multi-select filter in the app responds to being tapped.
 *
 * One tap adds or removes that one option, which is what a row of checkboxes looks like it
 * should do. Two taps in quick succession narrow to that option alone, which is the thing
 * people actually want most of the time - "just show me the tanks" - and which a plain
 * toggle makes into four taps. Double-tapping something that is already on its own clears
 * the filter again.
 *
 * Two rules stop it producing a useless screen. Emptying the last option returns to
 * everything rather than showing nothing, because a filter matching nothing is never what
 * was meant. And the second tap is judged against the selection as it stood *before* the
 * first tap of the pair, so a double tap behaves the same however the first tap landed.
 */
object FilterSelection {

    /** One tap: plain add or remove, with empty folded back to everything. */
    fun <T> toggle(current: Set<T>, value: T, all: Set<T>): Set<T> {
        val next = if (value in current) current - value else current + value
        return next.ifEmpty { all }
    }

    /** Two taps: narrow to this one, or clear if it was already the only one. */
    fun <T> isolate(before: Set<T>, value: T, all: Set<T>): Set<T> =
        if (before == setOf(value)) all else setOf(value)
}

/**
 * Turns a stream of taps on one chip row into the selection those taps mean.
 *
 * The single-tap effect is applied immediately rather than waiting to see whether a second
 * tap is coming: a filter that hesitates for a quarter of a second on every tap feels
 * broken, and since the double-tap result replaces the selection outright, the brief
 * intermediate state does not survive to be noticed.
 */
class FilterTaps<T>(private val windowMillis: Long = DOUBLE_TAP_WINDOW) {

    private var lastValue: T? = null
    private var lastTapAt = 0L
    private var beforeBurst: Set<T> = emptySet()

    fun onTap(value: T, current: Set<T>, all: Set<T>, now: Long = System.currentTimeMillis()): Set<T> {
        val isSecondTap = value == lastValue && now - lastTapAt <= windowMillis
        if (!isSecondTap) beforeBurst = current
        lastValue = value
        lastTapAt = now

        return if (isSecondTap) {
            FilterSelection.isolate(beforeBurst, value, all)
        } else {
            FilterSelection.toggle(current, value, all)
        }
    }

    companion object {
        /** Android's own double-tap timeout, so it matches the rest of the system. */
        const val DOUBLE_TAP_WINDOW = 300L
    }
}

@Composable
fun <T> rememberFilterTaps(): FilterTaps<T> = remember { FilterTaps() }
