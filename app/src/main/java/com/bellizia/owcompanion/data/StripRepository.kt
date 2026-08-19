package com.bellizia.owcompanion.data

import android.content.Context
import com.bellizia.owcompanion.ui.comics.Strip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Strips saved by name, so a comic outlives the app being closed.
 *
 * One readable JSON file, like the tactics boards next door and for the same reason: a strip
 * is small, there are never many, and a single file is something a person could copy off
 * their phone if they ever wanted to keep it.
 */
class StripRepository(private val context: Context) {

    suspend fun all(): List<Strip> = withContext(Dispatchers.IO) {
        runCatching {
            json.decodeFromString(ListSerializer(Strip.serializer()), file().readText())
        }.getOrDefault(emptyList())
    }

    /** Saving under a name that already exists replaces it, which is what "save" means. */
    suspend fun save(strip: Strip): List<Strip> = withContext(Dispatchers.IO) {
        val updated = listOf(strip) + all().filterNot { it.name.equals(strip.name, true) }
        write(updated)
        updated
    }

    suspend fun delete(name: String): List<Strip> = withContext(Dispatchers.IO) {
        val updated = all().filterNot { it.name.equals(name, true) }
        write(updated)
        updated
    }

    private fun write(strips: List<Strip>) {
        file().writeText(json.encodeToString(ListSerializer(Strip.serializer()), strips))
    }

    private fun file() = File(context.filesDir, "strips.json").apply {
        if (!exists()) writeText("[]")
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
