package com.bellizia.owcompanion.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** A build somebody put together and named, so they can come back to it. */
@Serializable
data class SavedBuild(
    val id: String,
    val name: String,
    val hero: String,
    val weapon: String,
    /** Item names, in the order they were added. */
    val items: List<String> = emptyList(),
    val savedAt: Long = 0,
)

@Serializable
private data class BuildFile(val builds: List<SavedBuild> = emptyList())

/**
 * Stores saved builds as a single JSON file.
 *
 * A handful of builds per hero is not a database's worth of data, and keeping it as one
 * readable file means it can be inspected, copied off the device or hand-edited without
 * anything special.
 */
class BuildRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private val file: File get() = File(context.filesDir, FILE_NAME)

    suspend fun all(): List<SavedBuild> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyList()
        runCatching { json.decodeFromString(BuildFile.serializer(), file.readText()).builds }
            .getOrDefault(emptyList())
    }

    suspend fun save(build: SavedBuild): List<SavedBuild> = mutate { builds ->
        val existing = builds.indexOfFirst { it.id == build.id }
        if (existing >= 0) {
            builds.toMutableList().also { it[existing] = build }
        } else {
            builds + build
        }
    }

    suspend fun delete(id: String): List<SavedBuild> = mutate { builds ->
        builds.filterNot { it.id == id }
    }

    suspend fun rename(id: String, name: String): List<SavedBuild> = mutate { builds ->
        builds.map { if (it.id == id) it.copy(name = name) else it }
    }

    /** A copy under a new id, so editing the clone never touches the original. */
    suspend fun clone(id: String): List<SavedBuild> = mutate { builds ->
        val source = builds.firstOrNull { it.id == id } ?: return@mutate builds
        builds + source.copy(
            id = newId(),
            name = "${source.name} (copy)",
            savedAt = System.currentTimeMillis(),
        )
    }

    private suspend fun mutate(
        transform: (List<SavedBuild>) -> List<SavedBuild>,
    ): List<SavedBuild> = withContext(Dispatchers.IO) {
        val updated = transform(all())
        file.writeText(json.encodeToString(BuildFile.serializer(), BuildFile(updated)))
        updated
    }

    companion object {
        private const val FILE_NAME = "builds.json"

        fun newId(): String = java.util.UUID.randomUUID().toString()
    }
}
