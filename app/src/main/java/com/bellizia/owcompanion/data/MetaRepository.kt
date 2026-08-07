package com.bellizia.owcompanion.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

/**
 * Ban, pick and win rates, straight from Blizzard.
 *
 * `overwatch.blizzard.com/rates` is the game's own published figures, and it puts the whole
 * table into an `allrows` attribute as JSON rather than fetching it from an API, so a plain
 * GET and one attribute read is the whole job. There is no key and nothing to sign in to.
 * `robots.txt` disallows only the login and navbar paths.
 *
 * Nothing here is cached to disk. These numbers describe this week's game, and a stale copy
 * of them is worse than none: the rest of the app is comfortable being a snapshot because a
 * patch from 2019 stays a patch from 2019, but a ban rate from last month is just wrong.
 */
class MetaRepository {

    /** One hero's row. Rates are percentages, 0-100. */
    data class HeroRate(
        val slug: String,
        val name: String,
        val role: String,
        val subrole: String,
        val portrait: String?,
        val ban: Double,
        val pick: Double,
        val win: Double,
    )

    data class Filters(
        val region: String = "Europe",
        val tier: String = "All",
        val input: String = "PC",
        /** "1" is Competitive, the only queue with a ban phase; "0" is Quick Play. */
        val queue: String = "1",
        val role: String = "All",
        val map: String = "all-maps",
    )

    sealed interface Result {
        data class Loaded(val heroes: List<HeroRate>) : Result
        data class Failed(val cause: String) : Result
    }

    suspend fun rates(filters: Filters): Result = withContext(Dispatchers.IO) {
        runCatching { parse(fetch(url(filters))) }.fold(
            onSuccess = { heroes ->
                if (heroes.isEmpty()) Result.Failed("the page carried no rates")
                else Result.Loaded(heroes)
            },
            onFailure = { Result.Failed(it.message ?: it.javaClass.simpleName) },
        )
    }

    private fun fetch(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            // The default Java user agent is served a different page; ask as a browser would.
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept-Language", "en-US,en;q=0.9")
        }
        return try {
            if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    @Serializable
    private data class Row(
        val id: String = "",
        val cells: Cells = Cells(),
        val hero: HeroInfo = HeroInfo(),
    )

    @Serializable
    private data class Cells(
        val name: String = "",
        val winrate: Double = 0.0,
        val pickrate: Double = 0.0,
        val banrate: Double = 0.0,
    )

    @Serializable
    private data class HeroInfo(
        val name: String = "",
        val portrait: String? = null,
        val role: String = "",
        @SerialName("subrole") val subrole: String = "",
    )

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0 Mobile Safari/537.36"

        private val json = Json { ignoreUnknownKeys = true }

        /** The rates table, before any HTML entity decoding. */
        private val ALL_ROWS = Regex("""allrows="([^"]*)"""")

        fun url(filters: Filters): String = buildString {
            append("https://overwatch.blizzard.com/en-us/rates/")
            append("?input=").append(filters.input)
            append("&map=").append(filters.map)
            append("&region=").append(filters.region)
            append("&role=").append(filters.role)
            append("&rq=").append(filters.queue)
            append("&tier=").append(filters.tier)
        }

        internal fun parse(html: String): List<HeroRate> {
            val raw = ALL_ROWS.find(html)?.groupValues?.get(1) ?: return emptyList()
            val rows = runCatching {
                json.decodeFromString(ListSerializer(Row.serializer()), unescape(raw))
            }.getOrElse { return emptyList() }
            return rows.map { row ->
                HeroRate(
                    slug = row.id,
                    name = row.cells.name.ifBlank { row.hero.name }.ifBlank { row.id },
                    role = row.hero.role.lowercase(),
                    subrole = row.hero.subrole,
                    portrait = row.hero.portrait,
                    ban = row.cells.banrate,
                    pick = row.cells.pickrate,
                    win = row.cells.winrate,
                )
            }
        }

        /**
         * The JSON lives inside an HTML attribute, so every quote arrives as `&quot;`. Only
         * the five predefined entities can appear there; anything else is a literal.
         */
        internal fun unescape(value: String): String = value
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
    }
}
