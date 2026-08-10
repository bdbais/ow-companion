package com.bellizia.owcompanion.data

/**
 * Where Blizzard's own patch notes live, in the reader's language.
 *
 * The balance history in this app is parsed from the English wiki, so a player reading a
 * Japanese interface still reads English notes. Blizzard publish the same notes themselves,
 * translated, and the address carries the language - so the app can send someone to the
 * official wording rather than making them read a second language to understand a nerf.
 *
 * Ten of the fifteen languages this app speaks are genuinely translated. The other four are
 * not, and that is worth knowing rather than discovering: Turkish, Swedish, Arabic and
 * Ukrainian all return the English page, byte for byte identical to each other. Asking for
 * them would look like it worked and quietly show English under a Swedish label, so they
 * are sent to the English page deliberately instead.
 */
object OfficialNotes {

    private const val BASE = "https://overwatch.blizzard.com"
    private const val ENGLISH = "en-us"

    /**
     * Blizzard's locale for one of ours, or English where they publish no translation.
     *
     * Checked by fetching every one of these: the four missing ones each returned exactly
     * the same number of characters as the others, which is what gave them away.
     */
    private val LOCALES = mapOf(
        "it" to "it-it",
        "es" to "es-es",
        "pt" to "pt-br",
        "fr" to "fr-fr",
        "de" to "de-de",
        "pl" to "pl-pl",
        "ru" to "ru-ru",
        "ja" to "ja-jp",
        "ko" to "ko-kr",
        "zh" to "zh-tw",
        // Not translated by Blizzard: tr, sv, ar, uk. They fall through to English below.
    )

    /** Whether Blizzard publish these notes in the given language at all. */
    fun translated(language: String): Boolean = language.lowercase() in LOCALES

    /**
     * The page carrying a patch, which Blizzard organise by month rather than by patch.
     *
     * @param date an ISO date, as the dataset stores it.
     */
    fun urlFor(date: String, language: String): String? {
        val parts = date.split("-")
        if (parts.size < 2) return null
        val year = parts[0].takeIf { it.length == 4 && it.all(Char::isDigit) } ?: return null
        val month = parts[1].takeIf { it.length == 2 && it.all(Char::isDigit) } ?: return null
        val locale = LOCALES[language.lowercase()] ?: ENGLISH
        return "$BASE/$locale/news/patch-notes/live/$year/$month"
    }
}
