package com.bellizia.owcompanion.data

import android.content.Context
import com.bellizia.owcompanion.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

/**
 * Tells the reader when a newer build has been published, and gets out of the way.
 *
 * The app is not on the Play Store, so nothing updates it. Someone who installed in March
 * has no way of learning that the numbers were wrong and have since been fixed unless the
 * app says so.
 *
 * Three rules keep this from becoming a nuisance. It never blocks anything: the check runs
 * in the background and any failure - no network, rate limit, GitHub down - is silence. It
 * only ever offers a link; installing stays the reader's business. And once a version has
 * been dismissed it is not mentioned again, so ignoring it costs one tap forever rather than
 * one tap per launch.
 */
class ReleaseChecker(private val context: Context) {

    data class Release(
        val version: String,
        val url: String,
        /**
         * Whether this one cannot be installed over what is already here.
         *
         * True only when Android will refuse the update outright - a changed signing key -
         * so the app can say "uninstall first" instead of leaving the reader staring at a
         * bare "App not installed" with no idea why.
         */
        val needsReinstall: Boolean = false,
    )

    /** The newer release, or null when there is nothing worth saying. */
    suspend fun newerRelease(): Release? = withContext(Dispatchers.IO) {
        runCatching {
            val latest = fetchLatest() ?: return@runCatching null
            val tag = latest.tag.ifBlank { return@runCatching null }
            when {
                !isNewer(tag, BuildConfig.VERSION_NAME) -> null
                tag == dismissed() -> null
                else -> Release(
                    version = normalise(tag),
                    url = trustedUrl(latest.url),
                    needsReinstall = requiresReinstall(latest.body),
                )
            }
        }.getOrNull()
    }

    /**
     * How many times the published APKs have been downloaded, across every release.
     *
     * GitHub counts a download per file fetched from a release, which is not the same as a
     * person and not the same as an install: one reader who tries three versions counts
     * three, and a mirror that fetches the file counts too. The About screen says "downloads"
     * rather than "players" for that reason.
     *
     * Cached for six hours. Unauthenticated GitHub allows sixty calls an hour from one
     * address, and a number that changes by the minute is not worth spending them on.
     */
    suspend fun downloads(): Int? = withContext(Dispatchers.IO) {
        val store = prefs()
        val cached = store.getInt(KEY_DOWNLOADS, -1).takeIf { it >= 0 }
        val age = System.currentTimeMillis() - store.getLong(KEY_DOWNLOADS_AT, 0L)
        if (cached != null && age in 0 until CACHE_MILLIS) return@withContext cached

        val fetched = runCatching { fetchDownloads() }.getOrNull()
            ?: return@withContext cached
        store.edit()
            .putInt(KEY_DOWNLOADS, fetched)
            .putLong(KEY_DOWNLOADS_AT, System.currentTimeMillis())
            .apply()
        fetched
    }

    private fun fetchDownloads(): Int? {
        val connection = (URL(ALL_RELEASES).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "ow-companion")
        }
        val body = try {
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.use { it.readTextCapped(MAX_BODY_BYTES) }
        } finally {
            connection.disconnect()
        }
        return json.decodeFromString(ListSerializer(ReleaseAssets.serializer()), body)
            .filterNot { it.draft }
            .sumOf { release -> release.assets.sumOf { it.downloads } }
    }

    fun dismiss(version: String) {
        // Stored with whatever shape the tag had, since that is what the comparison sees.
        prefs().edit().putString(KEY_DISMISSED, "v${normalise(version)}").apply()
    }

    private fun dismissed(): String? = prefs().getString(KEY_DISMISSED, null)

    private fun prefs() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Serializable
    private data class ReleaseAssets(
        val assets: List<Asset> = emptyList(),
        val draft: Boolean = false,
    )

    @Serializable
    private data class Asset(@SerialName("download_count") val downloads: Int = 0)

    @Serializable
    private data class LatestRelease(
        @SerialName("tag_name") val tag: String = "",
        @SerialName("html_url") val url: String = "",
        val body: String = "",
        val draft: Boolean = false,
        val prerelease: Boolean = false,
    )

    private fun fetchLatest(): LatestRelease? {
        val connection = (URL(API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "ow-companion")
        }
        val body = try {
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.use { it.readTextCapped(MAX_BODY_BYTES) }
        } finally {
            connection.disconnect()
        }
        val release = json.decodeFromString(LatestRelease.serializer(), body)
        return release.takeUnless { it.draft || it.prerelease }
    }

    companion object {
        private const val PREFS = "releases"
        private const val KEY_DISMISSED = "dismissed_version"
        private const val KEY_DOWNLOADS = "downloads_total"
        private const val KEY_DOWNLOADS_AT = "downloads_fetched_at"
        private const val CACHE_MILLIS = 6 * 60 * 60 * 1000L
        private const val REPO = "https://github.com/bdbais/ow-companion"
        const val RELEASES = "$REPO/releases"

        /**
         * Two release lists with a hundred entries each fit in a fraction of this; anything
         * bigger is not a release list.
         */
        private const val MAX_BODY_BYTES = 2_000_000

        /**
         * The banner opens this project's own pages and nothing else.
         *
         * The address arrives in the API response. Today GitHub only ever puts the release's
         * own page there, but the banner is a UI element that says "an update exists - tap
         * to get it", and what it opens should be pinned by this app rather than by whatever
         * the response contains. Anything unexpected falls back to the releases page, which
         * is always right.
         */
        internal fun trustedUrl(candidate: String): String =
            candidate.takeIf { it == REPO || it.startsWith("$REPO/") } ?: RELEASES
        private const val API =
            "https://api.github.com/repos/bdbais/ow-companion/releases/latest"

        /**
         * Every release in one call. A hundred is far more than this project will have for
         * years, and asking for one page keeps the count to a single request.
         */
        private const val ALL_RELEASES =
            "https://api.github.com/repos/bdbais/ow-companion/releases?per_page=100"

        private val json = Json { ignoreUnknownKeys = true }

        /**
         * What a release says when it cannot be installed over the previous one.
         *
         * An HTML comment, so GitHub renders the notes without it and nobody reads a stray
         * token where the changelog should be. Only its presence is ever used: no text from
         * the release is shown or acted on, which keeps a field anyone with push access can
         * edit from deciding anything but this one flag.
         */
        internal const val REINSTALL_MARKER = "<!-- reinstall -->"

        /**
         * Whether a release says it cannot be installed over the previous one.
         *
         * Whitespace-tolerant inside the comment, because the notes are written by hand and
         * a stray space should not be the difference between a warning and a reader meeting
         * "App not installed" with no explanation. Anything else in the body is ignored.
         */
        internal fun requiresReinstall(body: String): Boolean =
            REINSTALL_PATTERN.containsMatchIn(body)

        private val REINSTALL_PATTERN = Regex("<!--\\s*reinstall\\s*-->", RegexOption.IGNORE_CASE)

        internal fun normalise(tag: String) = tag.trim().removePrefix("v").removePrefix("V")

        /**
         * Compares dotted versions numerically, so 0.10.0 beats 0.9.0 where a string
         * comparison would not. Anything unparseable sorts as zero rather than throwing:
         * a badly named tag should mean no prompt, not a crash on launch.
         */
        internal fun isNewer(candidate: String, installed: String): Boolean {
            val left = parts(candidate)
            val right = parts(installed)
            for (index in 0 until maxOf(left.size, right.size)) {
                val a = left.getOrElse(index) { 0 }
                val b = right.getOrElse(index) { 0 }
                if (a != b) return a > b
            }
            return false
        }

        private fun parts(version: String): List<Int> =
            normalise(version).split(".", "-", "+")
                .map { piece -> piece.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
    }
}
