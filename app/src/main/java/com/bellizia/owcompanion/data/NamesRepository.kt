package com.bellizia.owcompanion.data

import android.content.Context
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * What the game calls things, in the language the reader has chosen.
 *
 * The dataset is built from the English wiki, so every hero, ability, perk and Stadium power
 * in it has exactly one name. A Korean player got a Korean interface wrapped around "Biotic
 * Rifle", which is the sort of half-translation that reads worse than no translation.
 *
 * Blizzard publish the roster in ten of the fifteen languages this app speaks, and those
 * names are the ones players actually use, so they are worth more than anything invented
 * here. The other five have no official source; `dataset/names-contributed.json` is where a
 * volunteer's work goes and it is merged in before the file is built.
 *
 * Lookup is by the English string, because that is the only identifier the dataset carries.
 * Anything absent falls back to English, which is the honest outcome for a language nobody
 * has finished: a page half in Swedish beats a page that refuses to load.
 */
class NamesRepository(private val context: Context) {

    suspend fun forLocale(locale: Locale): Map<String, String> {
        val language = key(locale)
        cached[language]?.let { return it }
        return withContext(Dispatchers.IO) {
            val found = read(language) ?: emptyMap()
            synchronized(cached) { cached[language] = found }
            found
        }
    }

    /**
     * One language out of the file, and only one.
     *
     * The whole thing is fifteen languages and eighteen thousand strings, and a phone shows
     * one of them. Parsing it all and keeping it would hold about fourteen times more than
     * is ever read - so the parse is thrown away and only the language in use survives.
     *
     * Re-parsed if the reader changes language, which costs a couple of megabytes of work
     * once, on a background thread, in exchange for not carrying them for the whole session.
     */
    private fun read(language: String): Map<String, String>? = runCatching {
        val all = json.decodeFromString(
            MapSerializer(String.serializer(), MapSerializer(String.serializer(), String.serializer())),
            context.assets.open(ASSET).bufferedReader().use { it.readText() },
        )
        all[language]
    }.getOrNull()

    companion object {
        private const val ASSET = "names.json"

        private val json = Json { ignoreUnknownKeys = true }

        private val cached = mutableMapOf<String, Map<String, String>>()

        /**
         * The key a locale is filed under.
         *
         * Chinese is the only one that needs more than the language: Traditional and
         * Simplified are different translations, and the region is what tells them apart.
         */
        fun key(locale: Locale): String = when (locale.language) {
            "zh" -> if (locale.country in TRADITIONAL) "zhTW" else "zhCN"
            else -> locale.language
        }

        private val TRADITIONAL = setOf("TW", "HK", "MO")
    }
}
