package com.bellizia.owcompanion.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.bellizia.owcompanion.data.NamesRepository

/**
 * The game's own word for something, wherever the app has the English one.
 *
 * Provided once around the whole app rather than looked up per screen, because the answer
 * depends only on the current locale and every screen that shows a hero, an ability, a perk
 * or a Stadium item wants the same map.
 */
val LocalNames: ProvidableCompositionLocal<Map<String, String>> =
    staticCompositionLocalOf { emptyMap() }

/**
 * Loads the names for whatever language is in force and hands them down.
 *
 * Keyed on the locale, so switching language in the picker reloads without a restart. Until
 * the file is read the map is empty, which shows English for a moment rather than nothing -
 * the right way round for a screen whose text would otherwise be blank.
 */
@Composable
fun ProvideNames(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]

    // Lint says this never assigns `value`; the next line is the assignment. It misses the
    // case where the right-hand side is a suspend call, which is the third time in this
    // codebase, and at error severity it fails the whole lint run.
    @Suppress("ProduceStateDoesNotAssignValue")
    val names by produceState(initialValue = emptyMap<String, String>(), locale) {
        value = NamesRepository(context).forLocale(locale)
    }

    CompositionLocalProvider(LocalNames provides names, content = content)
}

/** The translated form, or the English one when there is no translation for it. */
@Composable
fun localised(english: String?): String {
    val text = english.orEmpty()
    return LocalNames.current[text] ?: text
}
