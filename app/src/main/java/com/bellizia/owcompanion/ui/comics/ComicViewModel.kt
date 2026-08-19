package com.bellizia.owcompanion.ui.comics

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bellizia.owcompanion.data.StripRepository
import com.bellizia.owcompanion.data.WikiRepository
import com.bellizia.owcompanion.data.model.HeroWiki
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ComicUiState(
    val strip: Strip = Strip(),
    val panelIndex: Int = 0,
    val roster: List<HeroWiki> = emptyList(),
    val loading: Boolean = true,
    val saved: List<Strip> = emptyList(),
    /** Which balloon the text field is editing, or null when none is selected. */
    val editing: String? = null,
    /** Which character is selected, so scale and flip know what they act on. */
    val picked: String? = null,
) {
    val panel: Panel get() = strip.panel(panelIndex)
    val line: Line? get() = panel.lines.firstOrNull { it.id == editing }
}

/**
 * A comic strip: heroes standing in panels, saying things.
 *
 * The backgrounds work the way the tactics board's do - whatever picture the reader brings.
 * There is no library of scenery to choose from because this app has no right to hand out
 * anybody's artwork, and a strip made of screenshots the player took themselves is both
 * legal and better.
 */
class ComicViewModel(application: Application) : AndroidViewModel(application) {

    private val strips = StripRepository(application)

    private val _state = MutableStateFlow(ComicUiState())
    val state: StateFlow<ComicUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val heroes = WikiRepository(getApplication()).wiki().heroes.sortedBy { it.name }
            _state.update { it.copy(roster = heroes, loading = false, saved = strips.all()) }
        }
    }

    fun setName(name: String) = _state.update { it.copy(strip = it.strip.copy(name = name)) }

    fun selectPanel(index: Int) =
        _state.update { it.copy(panelIndex = index, editing = null, picked = null) }

    /**
     * A new panel starts empty rather than copying the one before it.
     *
     * The opposite of the tactics board, deliberately: there a phase is the same board a
     * moment later, so copying is the whole point. Here the next panel is usually a
     * different shot, and inheriting the last one's dialogue would mean deleting it first
     * every single time.
     */
    fun addPanel() = _state.update { state ->
        val panels = state.strip.panels + Panel(id = nextId(state.strip))
        state.copy(
            strip = state.strip.copy(panels = panels),
            panelIndex = panels.lastIndex,
            editing = null,
            picked = null,
        )
    }

    fun removePanel() = _state.update { state ->
        if (state.strip.panels.size <= 1) return@update state
        val panels = state.strip.panels.filterIndexed { i, _ -> i != state.panelIndex }
        state.copy(
            strip = state.strip.copy(panels = panels),
            panelIndex = state.panelIndex.coerceAtMost(panels.lastIndex),
            editing = null,
            picked = null,
        )
    }

    fun addActor(hero: HeroWiki) = updatePanel { panel ->
        // Placed left, then right, then centre-ish: two heroes facing each other is the
        // common case and it should need no dragging at all.
        val slots = listOf(0.28f to 0.55f, 0.72f to 0.55f, 0.5f to 0.72f)
        val (x, y) = slots.getOrElse(panel.actors.size) { 0.5f to 0.5f }
        panel.copy(
            actors = panel.actors + Actor(
                id = "a${System.nanoTime()}",
                heroKey = hero.key,
                heroName = hero.name,
                portrait = hero.portrait,
                x = x,
                y = y,
                // The one on the right looks left, so they are talking to each other rather
                // than both facing off the edge of the panel.
                flipped = panel.actors.size == 1,
            ),
        )
    }

    fun moveActor(id: String, x: Float, y: Float) = updatePanel { panel ->
        panel.copy(
            actors = panel.actors.map {
                if (it.id == id) it.copy(x = x.coerceIn(0f, 1f), y = y.coerceIn(0f, 1f)) else it
            },
        )
    }

    fun scaleActor(id: String, by: Float) = updatePanel { panel ->
        panel.copy(
            actors = panel.actors.map {
                if (it.id == id) it.copy(scale = (it.scale * by).coerceIn(0.4f, 2.2f)) else it
            },
        )
    }

    fun flipActor(id: String) = updatePanel { panel ->
        panel.copy(actors = panel.actors.map { if (it.id == id) it.copy(flipped = !it.flipped) else it })
    }

    fun removeActor(id: String) {
        updatePanel { panel -> panel.copy(actors = panel.actors.filterNot { it.id == id }) }
        _state.update { if (it.picked == id) it.copy(picked = null) else it }
    }

    fun addLine(kind: Balloon) {
        val id = "l${System.nanoTime()}"
        updatePanel { panel ->
            val stacked = 0.15f + panel.lines.size * 0.13f
            panel.copy(
                lines = panel.lines + Line(
                    id = id,
                    kind = kind,
                    x = 0.5f,
                    y = stacked.coerceAtMost(0.8f),
                ),
            )
        }
        _state.update { it.copy(editing = id, picked = null) }
    }

    fun setLineText(id: String, text: String) = updatePanel { panel ->
        panel.copy(lines = panel.lines.map { if (it.id == id) it.copy(text = text) else it })
    }

    fun setLineKind(id: String, kind: Balloon) = updatePanel { panel ->
        panel.copy(lines = panel.lines.map { if (it.id == id) it.copy(kind = kind) else it })
    }

    fun moveLine(id: String, x: Float, y: Float) = updatePanel { panel ->
        panel.copy(
            lines = panel.lines.map {
                if (it.id == id) it.copy(x = x.coerceIn(0f, 1f), y = y.coerceIn(0f, 1f)) else it
            },
        )
    }

    fun removeLine(id: String) {
        updatePanel { panel -> panel.copy(lines = panel.lines.filterNot { it.id == id }) }
        _state.update { if (it.editing == id) it.copy(editing = null) else it }
    }

    fun edit(id: String?) = _state.update { it.copy(editing = id, picked = null) }

    fun pick(id: String?) = _state.update { it.copy(picked = id, editing = null) }

    /**
     * Take a copy of the picture rather than remembering where it came from.
     *
     * The photo picker's grant dies with the process, so a strip holding only the original
     * URI would come back from a restart with blank panels. Copied per panel, because a
     * strip whose panels are different shots is the normal case.
     */
    fun setBackground(uri: String?) = viewModelScope.launch {
        // Named after the panel's id, not its position. Keyed by position, deleting panel
        // two would leave panel three pointing at the picture that used to belong to it.
        val id = _state.value.panel.id
        val stored = uri?.let { source ->
            withContext(Dispatchers.IO) {
                runCatching {
                    val file = File(getApplication<Application>().filesDir, "panel-$id.png")
                    getApplication<Application>().contentResolver
                        .openInputStream(Uri.parse(source))!!
                        .use { input -> file.outputStream().use(input::copyTo) }
                    file.toURI().toString()
                }.getOrNull()
            }
        }
        updatePanel { it.copy(background = stored) }
    }

    fun save() = viewModelScope.launch {
        val strip = _state.value.strip
        if (strip.name.isBlank()) return@launch
        _state.update { it.copy(saved = strips.save(strip)) }
    }

    fun open(strip: Strip) =
        _state.update { it.copy(strip = strip, panelIndex = 0, editing = null) }

    fun delete(name: String) = viewModelScope.launch {
        _state.update { it.copy(saved = strips.delete(name)) }
    }

    fun reset() = _state.update { it.copy(strip = Strip(), panelIndex = 0, editing = null) }

    private fun updatePanel(change: (Panel) -> Panel) = _state.update { state ->
        val panels = state.strip.panels.mapIndexed { i, panel ->
            if (i == state.panelIndex) change(panel) else panel
        }
        state.copy(strip = state.strip.copy(panels = panels))
    }

    private fun nextId(strip: Strip) = "p${strip.panels.size + 1}-${System.nanoTime()}"
}
