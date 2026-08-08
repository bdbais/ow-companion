package com.bellizia.owcompanion.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * A player's own career statistics, looked up by BattleTag.
 *
 * There is deliberately no sign-in here. Blizzard publish a career profile for anyone who
 * has set theirs to public, and OverFast reads it; a BattleTag is enough. That means the app
 * never asks for a password, never holds a token, and cannot see anything the player has not
 * already made public - which is a better arrangement for everyone than an account
 * connection would be.
 *
 * The consequence is worth saying plainly on screen rather than hiding: a private profile
 * returns nothing at all, and the fix is a setting in the game, not in this app.
 */
class PlayerRepository(private val context: Context) {

    /** One result from a name search. */
    @Serializable
    data class Hit(
        @SerialName("player_id") val id: String = "",
        val name: String = "",
        val avatar: String? = null,
        val title: String? = null,
        @SerialName("is_public") val isPublic: Boolean = false,
        @SerialName("last_updated_at") val lastUpdated: Long? = null,
        /** Filled in after the fact, so one "byz" can be told from another. */
        val games: Int? = null,
        val winrate: Double? = null,
    )

    @Serializable
    private data class SearchResponse(val total: Int = 0, val results: List<Hit> = emptyList())

    @Serializable
    private data class ProfileSummary(
        val username: String = "",
        val avatar: String? = null,
        val title: String? = null,
    )

    @Serializable
    private data class Profile(val summary: ProfileSummary = ProfileSummary())

    @Serializable
    data class Totals(
        val eliminations: Double = 0.0,
        val assists: Double = 0.0,
        val deaths: Double = 0.0,
        val damage: Double = 0.0,
        val healing: Double = 0.0,
    )

    @Serializable
    data class Block(
        @SerialName("games_played") val games: Int = 0,
        @SerialName("games_won") val won: Int = 0,
        @SerialName("games_lost") val lost: Int = 0,
        @SerialName("time_played") val seconds: Long = 0,
        val winrate: Double = 0.0,
        val kda: Double = 0.0,
        val total: Totals = Totals(),
        val average: Totals = Totals(),
    )

    @Serializable
    data class Summary(
        val general: Block? = null,
        val roles: Map<String, Block> = emptyMap(),
        val heroes: Map<String, Block> = emptyMap(),
    )

    sealed interface Result<out T> {
        data class Ok<T>(val value: T) : Result<T>
        /** The profile exists but Blizzard is not publishing it. */
        data object Private : Result<Nothing>
        data class Failed(val cause: String) : Result<Nothing>
    }

    /**
     * The whole BattleTag, if one was typed: `BYZ#2201` addresses a profile directly.
     *
     * Worth trying before the name search, which only turns up profiles Blizzard has chosen
     * to index and misses plenty that exist.
     */
    suspend fun lookup(tag: String): Result<Hit?> = withContext(Dispatchers.IO) {
        val slug = tag.trim().replace('#', '-')
        if (!TAG.matches(slug)) return@withContext Result.Ok(null)
        runCatching {
            val profile = json.decodeFromString(Profile.serializer(), fetch("$API/players/$slug"))
            Hit(
                id = slug,
                name = profile.summary.username.ifBlank { tag.substringBefore('#') },
                avatar = profile.summary.avatar,
                title = profile.summary.title,
                isPublic = true,
            )
        }.fold({ Result.Ok(it) }, { Result.Ok(null) })
    }

    suspend fun search(name: String): Result<List<Hit>> = withContext(Dispatchers.IO) {
        val typed = name.substringBefore('#').trim()
        if (typed.isBlank()) return@withContext Result.Ok(emptyList())
        runCatching {
            // The search matches case exactly, so someone typing their own BattleTag as
            // they see it in the game - BYZ - finds nothing while "byz" finds three. Both
            // are tried rather than making the reader guess which one the API wants.
            val hits = hitsFor(typed).ifEmpty {
                if (typed == typed.lowercase()) emptyList() else hitsFor(typed.lowercase())
            }
            // The name alone does not identify anyone: three different people are called
            // "byz" and the search does not return the number after the hash. Games played
            // and win rate do tell them apart, so they are fetched for each candidate.
            coroutineScope {
                hits.take(MAX_ENRICHED).map { hit ->
                    async {
                        val block = runCatching {
                            json.decodeFromString(
                                Summary.serializer(),
                                fetch("$API/players/${hit.id}/stats/summary"),
                            ).general
                        }.getOrNull()
                        hit.copy(games = block?.games, winrate = block?.winrate)
                    }
                }.awaitAll()
            }
        }.fold({ Result.Ok(it) }, { Result.Failed(it.message ?: "search failed") })
    }

    private fun hitsFor(query: String): List<Hit> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        return json.decodeFromString(
            SearchResponse.serializer(),
            fetch("$API/players?name=$encoded"),
        ).results
    }

    suspend fun summary(playerId: String): Result<Summary> = withContext(Dispatchers.IO) {
        runCatching {
            // The id already arrives percent-encoded from the search, so it is pasted in as
            // it came: encoding it again turns the %7C separator into %257C and 404s.
            val body = fetch("$API/players/$playerId/stats/summary")
            json.decodeFromString(Summary.serializer(), body)
        }.fold(
            onSuccess = { summary ->
                if (summary.general == null || summary.general.games == 0) Result.Private
                else Result.Ok(summary)
            },
            onFailure = { Result.Failed(it.message ?: "lookup failed") },
        )
    }

    /**
     * The accounts the reader has starred, most recently added first.
     *
     * Explicitly starred rather than silently collected: opening someone to look at their
     * numbers is not the same as wanting them on a list forever, and a list that fills
     * itself is a list nobody trusts. One person routinely has several worth keeping - a
     * main, a smurf, a friend they are coaching - and the search cannot tell three players
     * called "byz" apart, so finding the right one again is work worth doing once.
     */
    fun favourites(): List<Hit> = runCatching {
        json.decodeFromString(
            ListSerializer(Hit.serializer()),
            prefs().getString(KEY_SAVED, null) ?: return emptyList(),
        )
    }.getOrDefault(emptyList())

    fun addFavourite(hit: Hit) {
        val updated = listOf(hit) + favourites().filterNot { it.id == hit.id }
        write(updated.take(MAX_SAVED))
    }

    fun removeFavourite(hit: Hit) = write(favourites().filterNot { it.id == hit.id })

    private fun write(hits: List<Hit>) {
        prefs().edit()
            .putString(KEY_SAVED, json.encodeToString(ListSerializer(Hit.serializer()), hits))
            .apply()
    }

    private fun prefs() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun fetch(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", "ow-companion")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val API = "https://overfast-api.tekrop.fr"
        private const val PREFS = "player"
        private const val KEY_SAVED = "saved_players"

        /** Enough for a main, a couple of alts and a few friends. */
        private const val MAX_SAVED = 10

        /** Each one costs a request, and nobody scrolls past the first handful. */
        private const val MAX_ENRICHED = 6

        /** `Name-1234`, which is how the career profile is addressed. */
        private val TAG = Regex("""[^\s#-]{2,32}-\d{3,8}""")

        private val json = Json { ignoreUnknownKeys = true }

        /** `579942` seconds of Ana is not a fact anyone can read; `161 h` is. */
        fun hours(seconds: Long): String = when {
            seconds >= 3600 -> "${seconds / 3600} h"
            else -> "${seconds / 60} min"
        }
    }
}
