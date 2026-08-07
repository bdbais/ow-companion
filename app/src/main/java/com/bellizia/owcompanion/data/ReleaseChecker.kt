package com.bellizia.owcompanion.data

import android.content.Context
import com.bellizia.owcompanion.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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

    data class Release(val version: String, val url: String)

    /** The newer release, or null when there is nothing worth saying. */
    suspend fun newerRelease(): Release? = withContext(Dispatchers.IO) {
        runCatching {
            val latest = fetchLatest() ?: return@runCatching null
            val tag = latest.tag.ifBlank { return@runCatching null }
            when {
                !isNewer(tag, BuildConfig.VERSION_NAME) -> null
                tag == dismissed() -> null
                else -> Release(version = normalise(tag), url = latest.url.ifBlank { RELEASES })
            }
        }.getOrNull()
    }

    fun dismiss(version: String) {
        // Stored with whatever shape the tag had, since that is what the comparison sees.
        prefs().edit().putString(KEY_DISMISSED, "v${normalise(version)}").apply()
    }

    private fun dismissed(): String? = prefs().getString(KEY_DISMISSED, null)

    private fun prefs() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Serializable
    private data class LatestRelease(
        @SerialName("tag_name") val tag: String = "",
        @SerialName("html_url") val url: String = "",
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
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
        val release = json.decodeFromString(LatestRelease.serializer(), body)
        return release.takeUnless { it.draft || it.prerelease }
    }

    companion object {
        private const val PREFS = "releases"
        private const val KEY_DISMISSED = "dismissed_version"
        const val RELEASES = "https://github.com/bdbais/ow-companion/releases"
        private const val API =
            "https://api.github.com/repos/bdbais/ow-companion/releases/latest"

        private val json = Json { ignoreUnknownKeys = true }

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
