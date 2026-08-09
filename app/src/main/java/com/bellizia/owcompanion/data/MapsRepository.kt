package com.bellizia.owcompanion.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The maps, with a picture each.
 *
 * Written by tools/fetch_maps.py from Blizzard's own screenshots, which is the same
 * provenance as the hero portraits and the reason these can be shipped at all. The top-down
 * tactical diagrams people actually want belong to the community members who drew them, so
 * they stay out; a board can still take one from the device.
 */
class MapsRepository(private val context: Context) {

    @Serializable
    data class GameMap(
        val key: String = "",
        val name: String = "",
        val modes: List<String> = emptyList(),
        val location: String? = null,
        val country: String? = null,
        /** File name inside assets/maps, or null for the one Blizzard have not published. */
        val image: String? = null,
    ) {
        /** What Coil should load, or null when there is nothing to show. */
        val uri: String? get() = image?.let { "file:///android_asset/maps/$it" }
    }

    @Serializable
    private data class Payload(val maps: List<GameMap> = emptyList())

    suspend fun maps(): List<GameMap> = withContext(Dispatchers.IO) {
        cached ?: load().also { cached = it }
    }

    private fun load(): List<GameMap> = runCatching {
        val body = context.assets.open(ASSET).bufferedReader().use { it.readText() }
        json.decodeFromString(Payload.serializer(), body).maps
    }.getOrDefault(emptyList())

    private companion object {
        const val ASSET = "maps.json"
        val json = Json { ignoreUnknownKeys = true }

        /** One read for the life of the process: it is a few kilobytes and never changes. */
        @Volatile
        var cached: List<GameMap>? = null
    }
}
