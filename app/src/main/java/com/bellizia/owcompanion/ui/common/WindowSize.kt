package com.bellizia.owcompanion.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize

/**
 * How much room there is, in the unit layouts are written in.
 *
 * Three screens choose a layout by width and one by which side is longer, and all four used
 * to read it off the configuration - which Compose deprecated, because a configuration
 * describes the display and a composable lives in a window that may be a fraction of it. On
 * a folding phone or a split screen those are different numbers, and the smaller one is the
 * one a layout has to fit in.
 *
 * Kept in one place so the four agree. They disagreed once already: a landscape check and a
 * width check that resolved differently on the same screen.
 */
@Composable
@ReadOnlyComposable
fun windowSize(): DpSize {
    val size = LocalWindowInfo.current.containerSize
    return with(LocalDensity.current) {
        DpSize(size.width.toDp(), size.height.toDp())
    }
}

/** Whether there is room to put two things side by side rather than one above the other. */
@Composable
@ReadOnlyComposable
fun isWide(threshold: Dp): Boolean = windowSize().width >= threshold

/** Whether the window is wider than it is tall, which is not the same as the device being. */
@Composable
@ReadOnlyComposable
fun isLandscape(): Boolean = windowSize().let { it.width > it.height }
