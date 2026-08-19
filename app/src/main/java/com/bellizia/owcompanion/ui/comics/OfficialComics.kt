package com.bellizia.owcompanion.ui.comics

import java.util.Locale
import com.bellizia.owcompanion.data.NamesRepository

/**
 * Where Blizzard keep the comics, in the reader's own language where there is one.
 *
 * The app does not host any of it. These are stories Blizzard publish and own, and the right
 * thing to do with them is send people to the source rather than copy it - which is also the
 * only reading of the Fan Content Policy that a fan project should be relying on.
 *
 * The gallery is the only address worth linking. There is no per-category landing page:
 * `/media/comics/` and `/media/comic/` both answer 404, and the filters on the gallery are
 * applied in the page rather than in the URL. Individual stories do have stable addresses,
 * but a bundled list of them is a list that goes stale, and a comics section that quietly
 * stops matching what Blizzard publish is worse than one that always opens the real thing.
 */
object OfficialComics {

    /**
     * The app's language key -> the locale Blizzard serve the gallery under.
     *
     * The same ten as `tools/fetch_names.py`, and not by coincidence: those are the
     * languages the game is published in. Checked rather than assumed - each of these
     * answers 200, while zh-cn, uk-ua, sv-se, ar-sa and tr-tr all redirect away, which is
     * why those five are absent and fall back to English.
     */
    private val LOCALES = mapOf(
        "it" to "it-it",
        "es" to "es-es",
        "pt" to "pt-br",
        "fr" to "fr-fr",
        "de" to "de-de",
        "ja" to "ja-jp",
        "ko" to "ko-kr",
        "zhTW" to "zh-tw",
        "ru" to "ru-ru",
        "pl" to "pl-pl",
    )

    private const val FALLBACK = "en-us"

    /** Whether Blizzard publish the stories in this reader's language at all. */
    fun published(locale: Locale): Boolean = NamesRepository.key(locale) in LOCALES

    fun gallery(locale: Locale): String {
        val tag = LOCALES[NamesRepository.key(locale)] ?: FALLBACK
        return "https://overwatch.blizzard.com/$tag/media/"
    }
}
