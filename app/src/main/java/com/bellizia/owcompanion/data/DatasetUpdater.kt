package com.bellizia.owcompanion.data

import android.content.Context
import com.bellizia.owcompanion.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches a newer dataset when one is published.
 *
 * The app is offline-first: everything it needs is in the APK, and a failed or absent
 * update is a non-event rather than an error. A downloaded dataset lands in `filesDir` and
 * the repositories prefer it from the next launch, so a bad download can never corrupt the
 * bundled copy.
 */
class DatasetUpdater(private val context: Context) {

    @Serializable
    data class Manifest(
        val version: Int = 0,
        val weapons: String = "",
        val wiki: String = "",
    )

    sealed interface Result {
        /** No update server is configured for this build. */
        data object NotConfigured : Result

        data object UpToDate : Result

        data class Updated(val version: Int) : Result

        data class Failed(val reason: String) : Result
    }

    private val json = Json { ignoreUnknownKeys = true }

    val isConfigured: Boolean get() = BuildConfig.DATASET_UPDATE_URL.isNotBlank()

    suspend fun check(currentVersion: Int): Result = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext Result.NotConfigured

        val base = BuildConfig.DATASET_UPDATE_URL.trimEnd('/')
        try {
            val manifest = json.decodeFromString(
                Manifest.serializer(),
                get("$base/manifest.json"),
            )
            if (manifest.version <= currentVersion) return@withContext Result.UpToDate

            // Download to temporary files and only swap them in once both have arrived:
            // a dataset with new weapons and an old wiki would disagree with itself.
            val weapons = downloadToTemp(resolve(base, manifest.weapons), "weapons")
            val wiki = downloadToTemp(resolve(base, manifest.wiki), "wiki")

            weapons.renameTo(File(context.filesDir, DOWNLOADED_WEAPONS))
            wiki.renameTo(File(context.filesDir, DOWNLOADED_WIKI))
            File(context.filesDir, VERSION_MARKER).writeText(manifest.version.toString())

            Result.Updated(manifest.version)
        } catch (error: Exception) {
            Result.Failed(error.message ?: error::class.simpleName.orEmpty())
        }
    }

    private fun resolve(base: String, path: String): String =
        if (path.startsWith("http")) path else "$base/${path.trimStart('/')}"

    private fun get(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "OwCompanion/${BuildConfig.VERSION_NAME}")
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${connection.responseCode}")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadToTemp(url: String, name: String): File {
        val text = get(url)
        // A truncated download is still valid text; parsing it here is what catches that.
        json.parseToJsonElement(text)
        return File(context.cacheDir, "$name.download").apply { writeText(text) }
    }

    companion object {
        const val DOWNLOADED_WEAPONS = "weapons.json"
        const val DOWNLOADED_WIKI = "wiki.json"
        private const val VERSION_MARKER = "dataset-version"

        /** Version of whatever the app will actually load, downloaded or bundled. */
        fun installedVersion(context: Context, bundledVersion: Int): Int {
            val marker = File(context.filesDir, VERSION_MARKER)
            return marker.takeIf { it.exists() }
                ?.readText()
                ?.trim()
                ?.toIntOrNull()
                ?.takeIf { it > bundledVersion }
                ?: bundledVersion
        }

        /** The downloaded copy when there is one, otherwise null for "use the asset". */
        fun downloadedFile(context: Context, name: String): File? =
            File(context.filesDir, name).takeIf { it.exists() && it.length() > 0 }
    }
}
