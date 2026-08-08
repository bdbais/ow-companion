package com.bellizia.owcompanion.ui.board

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bellizia.owcompanion.data.BoardRepository
import com.bellizia.owcompanion.data.WikiRepository
import com.bellizia.owcompanion.data.model.HeroWiki
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** What a drag on the board does. */
enum class Tool { Move, Arrow }

data class BoardUiState(
    val board: Board = Board(),
    val frameIndex: Int = 0,
    val roster: List<HeroWiki> = emptyList(),
    /** Which team the next hero added joins. */
    val adding: Side = Side.Ours,
    /** Placing pieces, or drawing the movement between them. */
    val tool: Tool = Tool.Move,
    val loading: Boolean = true,
    val saved: List<Board> = emptyList(),
) {
    val frame: Frame get() = board.frame(frameIndex)

    /**
     * Heroes already on the team currently being placed.
     *
     * A team cannot field the same hero twice, so those are shown greyed out rather than
     * silently ignoring the tap. The other team is unaffected: both sides fielding an Ana
     * is an ordinary situation and a plan should be able to say so.
     */
    val alreadyPlaced: Set<String>
        get() = frame.tokens.filter { it.side == adding }.map { it.heroKey }.toSet()
}

/**
 * A tactics board: hero tokens on a picture, a frame per phase of the plan.
 *
 * The background is whatever image the reader picks, which is the whole reason this works.
 * There is no published source of top-down Overwatch maps - the community has made some and
 * they are that community's to give away, not this app's - so rather than shipping someone
 * else's pictures, a player brings the view they want and puts their own plan on it.
 */
class BoardViewModel(application: Application) : AndroidViewModel(application) {

    private val boards = BoardRepository(application)

    private val _state = MutableStateFlow(BoardUiState())
    val state: StateFlow<BoardUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val heroes = WikiRepository(getApplication()).wiki().heroes.sortedBy { it.name }
            _state.update { it.copy(roster = heroes, loading = false, saved = boards.all()) }
        }
    }

    /**
     * Take a copy rather than remembering where the picture came from.
     *
     * The photo picker grants access for as long as the process lives and no longer, so a
     * board that only held the original URI would come back from a restart with its map
     * gone. A copy in the app's own files is also what makes the plan portable at all.
     */
    fun setBackground(uri: String?) = viewModelScope.launch {
        val stored = uri?.let { source ->
            withContext(Dispatchers.IO) {
                runCatching {
                    val file = File(getApplication<Application>().filesDir, "board-background.png")
                    getApplication<Application>().contentResolver
                        .openInputStream(Uri.parse(source))!!
                        .use { input -> file.outputStream().use(input::copyTo) }
                    file.toURI().toString()
                }.getOrNull()
            }
        }
        _state.update { it.copy(board = it.board.copy(background = stored)) }
    }

    fun setName(name: String) = _state.update { it.copy(board = it.board.copy(name = name)) }

    fun save() = viewModelScope.launch {
        val board = _state.value.board
        if (board.name.isBlank()) return@launch
        _state.update { it.copy(saved = boards.save(board)) }
    }

    fun load(board: Board) = _state.update {
        it.copy(board = board, frameIndex = 0)
    }

    fun deleteSaved(name: String) = viewModelScope.launch {
        _state.update { it.copy(saved = boards.delete(name)) }
    }

    fun setAddingSide(side: Side) = _state.update { it.copy(adding = side) }

    fun setTool(tool: Tool) = _state.update { it.copy(tool = tool) }

    fun addArrow(fromX: Float, fromY: Float, toX: Float, toY: Float) = editFrame { frame ->
        frame.copy(
            arrows = frame.arrows + Arrow(
                id = System.nanoTime().toString(),
                side = _state.value.adding,
                fromX = fromX.coerceIn(0f, 1f),
                fromY = fromY.coerceIn(0f, 1f),
                toX = toX.coerceIn(0f, 1f),
                toY = toY.coerceIn(0f, 1f),
            ),
        )
    }

    /** Arrows are drawn faster than they are aimed, so undo is the tool that gets used. */
    fun undoArrow() = editFrame { frame ->
        frame.copy(arrows = frame.arrows.dropLast(1))
    }

    fun add(hero: HeroWiki) = editFrame { frame ->
        // A team cannot field the same hero twice. The strip greys these out, so reaching
        // here means something went round the interface; refusing is the right answer either
        // way, and it keeps the rule in one place rather than only in the view.
        if (frame.tokens.any { it.side == _state.value.adding && it.heroKey == hero.key }) {
            return@editFrame frame
        }
        frame.copy(
            tokens = frame.tokens + Token(
                // Still not keyed on the hero: the same hero can appear once per side, and
                // a token needs an identity that survives being moved between frames.
                id = "${hero.key}-${System.nanoTime()}",
                heroKey = hero.key,
                heroName = hero.name,
                portrait = hero.portrait,
                side = _state.value.adding,
                // Dropped at the top left rather than the centre, where it would land on
                // top of whatever was placed last.
                x = 0.12f + (frame.tokens.size % 6) * 0.13f,
                y = 0.12f + (frame.tokens.size / 6) * 0.12f,
            ),
        )
    }

    fun move(id: String, x: Float, y: Float) = editFrame { frame ->
        frame.copy(
            tokens = frame.tokens.map {
                if (it.id == id) it.copy(x = x.coerceIn(0f, 1f), y = y.coerceIn(0f, 1f)) else it
            },
        )
    }

    fun remove(id: String) = editFrame { frame ->
        frame.copy(tokens = frame.tokens.filterNot { it.id == id })
    }

    fun setCaption(text: String) = editFrame { it.copy(caption = text) }

    /** A new phase starts as a copy of this one: you move what changed, not everything. */
    fun addFrame() = _state.update { current ->
        val copy = current.frame.copy(id = System.nanoTime().toString(), caption = "")
        val frames = current.board.frames.toMutableList()
        frames.add(current.frameIndex + 1, copy)
        current.copy(
            board = current.board.copy(frames = frames),
            frameIndex = current.frameIndex + 1,
        )
    }

    fun removeFrame() = _state.update { current ->
        if (current.board.frames.size <= 1) return@update current
        val frames = current.board.frames.toMutableList()
        frames.removeAt(current.frameIndex)
        current.copy(
            board = current.board.copy(frames = frames),
            frameIndex = current.frameIndex.coerceAtMost(frames.size - 1),
        )
    }

    fun selectFrame(index: Int) = _state.update {
        it.copy(frameIndex = index.coerceIn(0, it.board.frames.size - 1))
    }

    private fun editFrame(change: (Frame) -> Frame) = _state.update { current ->
        val frames = current.board.frames.toMutableList()
        frames[current.frameIndex] = change(current.frame)
        current.copy(board = current.board.copy(frames = frames))
    }
}
