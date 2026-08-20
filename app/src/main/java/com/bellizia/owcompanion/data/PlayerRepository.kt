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
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.builtins.MapSerializer
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
        val competitive: Competitive? = null,
    )

    @Serializable
    private data class Profile(val summary: ProfileSummary = ProfileSummary())

    @Serializable
    private data class Competitive(val pc: Platform? = null, val console: Platform? = null)

    @Serializable
    private data class Platform(
        val season: Int? = null,
        val tank: RawRank? = null,
        val damage: RawRank? = null,
        val support: RawRank? = null,
        val open: RawRank? = null,
    )

    @Serializable
    private data class RawRank(
        val division: String = "",
        val tier: Int = 0,
        @SerialName("rank_icon") val rankIcon: String? = null,
        @SerialName("tier_icon") val tierIcon: String? = null,
    )

    /**
     * One competitive placement, the way the game shows it: a division and a tier within it.
     *
     * A role the player has not placed in this season is absent rather than shown at zero,
     * because "unplaced" and "bottom of bronze" are not the same thing.
     */
    data class Rank(
        val role: String,
        val division: String,
        val tier: Int,
        val icon: String?,
        val tierIcon: String?,
    )

    /** Placements for whichever platform the account actually plays on. */
    data class Ranks(val season: Int?, val console: Boolean, val roles: List<Rank>)

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
        /** No profile under that BattleTag: renamed, or saved with a typo. */
        data object Gone : Result<Nothing>
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
        }.fold({ Result.Ok(it) }, { if (it is NoSuchPlayer) Result.Gone else Result.Failed(it.message ?: "search failed") })
    }

    private fun hitsFor(query: String): List<Hit> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        return json.decodeFromString(
            SearchResponse.serializer(),
            fetch("$API/players?name=$encoded"),
        ).results
    }

    /**
     * The modes a career can be split by.
     *
     * Only these two. Blizzard's career profile itself separates quick play from
     * competitive and nothing else, so arcade and the rest are folded into quick play at
     * the source and there is nothing to ask for.
     */
    enum class Mode(val slug: String?) {
        Everything(null),
        QuickPlay("quickplay"),
        Competitive("competitive"),
    }

    @Serializable
    private data class RawStat(
        val key: String = "",
        val label: String = "",
        val value: Double = 0.0,
    )

    @Serializable
    private data class RawGroup(
        val category: String = "",
        val label: String = "",
        val stats: List<RawStat> = emptyList(),
    )

    /** One heading from the career profile, with the figures under it. */
    data class StatGroup(val label: String, val stats: List<Stat>)

    data class Stat(val label: String, val value: Double)

    /**
     * Everything the profile records about one hero: eighty-odd figures in seven groups,
     * including the ones the summary leaves out - accuracy, self healing, what each ability
     * did.
     *
     * The endpoint answers for every hero at once and insists on a queue, so "all queues"
     * asks for quick play: it is where nearly all of anyone's games are, and a blank screen
     * would be a worse answer than a slightly narrower one.
     */
    suspend fun heroStats(
        playerId: String,
        heroKey: String,
        mode: Mode = Mode.Everything,
    ): List<StatGroup> = withContext(Dispatchers.IO) {
        runCatching {
            val queue = mode.slug ?: Mode.QuickPlay.slug
            // hero= narrows it to the one asked for; without it the answer carries every
            // hero the account has ever touched.
            val body = fetch("$API/players/$playerId/stats?gamemode=$queue&platform=pc&hero=$heroKey")
            val all = json.decodeFromString(
                MapSerializer(String.serializer(), ListSerializer(RawGroup.serializer())),
                body,
            )
            all[heroKey].orEmpty().map { group ->
                StatGroup(
                    label = group.label.ifBlank { group.category },
                    stats = group.stats.map { Stat(it.label, it.value) },
                )
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Competitive placements per role, from the profile endpoint.
     *
     * Separate from the career figures because it is a different question: how far someone
     * has climbed this season, rather than what they have done overall. Blizzard publish it
     * per platform, and a console-only account has nothing under pc, so whichever side has
     * placements is the one shown.
     */
    suspend fun ranks(playerId: String): Ranks? = withContext(Dispatchers.IO) {
        runCatching {
            // /players/{id} wraps this in a "summary" key; /players/{id}/summary is the
            // same object on its own. Decoding the wrapper here quietly produced defaults
            // and no placements at all, because the parse succeeded against every field
            // being optional.
            val profile = json.decodeFromString(
                ProfileSummary.serializer(),
                fetch("$API/players/$playerId/summary"),
            )
            val competitive = profile.competitive ?: return@runCatching null

            fun placements(platform: Platform?): List<Rank> = listOfNotNull(
                platform?.tank?.let { it to "tank" },
                platform?.damage?.let { it to "damage" },
                platform?.support?.let { it to "support" },
                platform?.open?.let { it to "open" },
            ).map { (raw, role) ->
                Rank(role, raw.division, raw.tier, raw.rankIcon, raw.tierIcon)
            }

            val pc = placements(competitive.pc)
            val console = placements(competitive.console)
            when {
                pc.isNotEmpty() -> Ranks(competitive.pc?.season, false, pc)
                console.isNotEmpty() -> Ranks(competitive.console?.season, true, console)
                else -> null
            }
        }.getOrNull()
    }

    suspend fun summary(
        playerId: String,
        mode: Mode = Mode.Everything,
    ): Result<Summary> = withContext(Dispatchers.IO) {
        runCatching {
            // The id already arrives percent-encoded from the search, so it is pasted in as
            // it came: encoding it again turns the %7C separator into %257C and 404s.
            val query = mode.slug?.let { "?gamemode=$it" }.orEmpty()
            val body = fetch("$API/players/$playerId/stats/summary$query")
            json.decodeFromString(Summary.serializer(), body)
        }.fold(
            onSuccess = { summary ->
                if (summary.general == null || summary.general.games == 0) Result.Private
                else Result.Ok(summary)
            },
            onFailure = { if (it is NoSuchPlayer) Result.Gone else Result.Failed(it.message ?: "lookup failed") },
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
            val code = connection.responseCode
            // A 404 is not a failure to reach anything - it is an answer, and a different
            // one from the rest. The BattleTag is gone: renamed, or typed wrong once and
            // saved. Everything else is worth retrying; this is worth correcting, and the
            // screen can only say which if the two arrive differently.
            if (code == 404) throw NoSuchPlayer()
            if (code !in 200..299) error("HTTP $code")
            connection.inputStream.use { it.readTextCapped(8_000_000) }
        } finally {
            connection.disconnect()
        }
    }

    /** Thrown for the one status that means "no such player" rather than "not now". */
    class NoSuchPlayer : RuntimeException()

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
