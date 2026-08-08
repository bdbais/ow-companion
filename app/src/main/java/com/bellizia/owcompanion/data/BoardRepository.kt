package com.bellizia.owcompanion.data

import android.content.Context
import com.bellizia.owcompanion.ui.board.Board
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Boards saved by name, so a plan outlives the app being closed.
 *
 * Kept as one JSON file rather than a database: a board is small, there are never many, and
 * the whole set being one readable file makes it something a person could copy off their
 * phone if they ever wanted to.
 */
class BoardRepository(private val context: Context) {

    suspend fun all(): List<Board> = withContext(Dispatchers.IO) {
        runCatching {
            json.decodeFromString(ListSerializer(Board.serializer()), file().readText())
        }.getOrDefault(emptyList())
    }

    /** Saving under a name that already exists replaces it, which is what "save" means. */
    suspend fun save(board: Board): List<Board> = withContext(Dispatchers.IO) {
        val updated = listOf(board) + all().filterNot { it.name.equals(board.name, true) }
        write(updated)
        updated
    }

    suspend fun delete(name: String): List<Board> = withContext(Dispatchers.IO) {
        val updated = all().filterNot { it.name.equals(name, true) }
        write(updated)
        updated
    }

    private fun write(boards: List<Board>) {
        file().writeText(json.encodeToString(ListSerializer(Board.serializer()), boards))
    }

    private fun file() = File(context.filesDir, "boards.json").apply {
        if (!exists()) writeText("[]")
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
