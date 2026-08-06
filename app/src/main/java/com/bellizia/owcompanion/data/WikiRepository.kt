package com.bellizia.owcompanion.data

import android.content.Context
import com.bellizia.owcompanion.data.model.WikiData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Loads the hero wiki: portraits, abilities, release dates and the full balance history.
 *
 * Kept apart from the weapon dataset because it is twenty times the size and the damage
 * chart has no use for it - there is no reason to parse a decade of patch notes to draw a
 * bar.
 */
class WikiRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private var cached: WikiData? = null

    suspend fun wiki(): WikiData = cached ?: withContext(Dispatchers.IO) {
        val downloaded = DatasetUpdater.downloadedFile(context, DatasetUpdater.DOWNLOADED_WIKI)
        val parsed = downloaded
            ?.let { file ->
                runCatching { json.decodeFromString(WikiData.serializer(), file.readText()) }
                    .getOrNull()
            }
            ?: json.decodeFromString(
                WikiData.serializer(),
                context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() },
            )
        parsed.also { cached = it }
    }

    companion object {
        private const val ASSET_NAME = "wiki.json"

        /** Coil can read straight out of the APK's assets with this scheme. */
        fun imageUri(fileName: String?): String? =
            fileName?.let { "file:///android_asset/heroes/$it" }
    }
}
