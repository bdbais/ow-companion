package com.bellizia.owcompanion.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.bellizia.owcompanion.R
import com.bellizia.owcompanion.data.NamesRepository

/**
 * The game's own words, and a way back to the dataset's spelling.
 *
 * Two maps rather than one, because the strings arriving here do not all agree on case:
 * Blizzard's rates table shouts "D.MON" where the wiki writes "D.Mon", and an exact lookup
 * misses that, showing a shouted name on one screen and a translated one on every other.
 * The second map is keyed on the lowercase form and remembers the spelling the dataset uses,
 * so a name that is not translated still comes out looking like itself.
 */
class Names(private val exact: Map<String, String>) {

    private val loose: Map<String, Pair<String, String>> =
        exact.entries.associate { (english, translated) ->
            english.lowercase() to (english to translated)
        }

    operator fun get(text: String): String {
        exact[text]?.let { return it }
        val (canonical, translated) = loose[text.lowercase()] ?: return text
        // Translated where there is one; otherwise at least the spelling the app uses.
        return translated.ifBlank { canonical }
    }

    /** Whether this is a string the game itself names, rather than one the app composed. */
    fun knows(text: String): Boolean =
        exact.containsKey(text) || loose.containsKey(text.lowercase())

    companion object {
        val Empty = Names(emptyMap())
    }
}

/**
 * Provided once around the whole app rather than looked up per screen, because the answer
 * depends only on the current locale and every screen that shows a hero, an ability, a perk
 * or a Stadium item wants the same map.
 */
val LocalNames: ProvidableCompositionLocal<Names> = staticCompositionLocalOf { Names.Empty }

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
    val names by produceState(initialValue = Names.Empty, locale) {
        value = Names(NamesRepository(context).forLocale(locale))
    }

    CompositionLocalProvider(LocalNames provides names, content = content)
}

/**
 * The translated form, or the English one when there is no translation for it.
 *
 * One shape needs unpicking first. Nine weapons are called "<primary> Alt Fire", and that is
 * this app's name for them rather than the game's - an alternate fire has no name of its
 * own, so the primary's is borrowed and a suffix added. The whole string therefore matches
 * nothing official, while the half in front of it matches perfectly.
 */
@Composable
fun localised(english: String?): String {
    val text = english.orEmpty()
    val names = LocalNames.current
    if (text.endsWith(ALT_FIRE) && !names.knows(text)) {
        return stringResource(
            R.string.weapon_alt_fire,
            names[text.removeSuffix(ALT_FIRE)],
        )
    }
    return names[text]
}

/** The suffix the pipeline appends, and the only composed name worth unpicking. */
private const val ALT_FIRE = " Alt Fire"
