package com.bellizia.owcompanion.ui.comics

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bellizia.owcompanion.data.AiArt
import com.bellizia.owcompanion.data.StripRepository
import com.bellizia.owcompanion.data.WikiRepository
import com.bellizia.owcompanion.data.model.HeroWiki
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    /** Candidates being generated, or the ones that came back. */
    val studio: Studio? = null,
) {
    val panel: Panel get() = strip.panel(panelIndex)
    val line: Line? get() = panel.lines.firstOrNull { it.id == editing }
}

/**
 * A round of generation, from the moment it is asked for to the moment one is chosen.
 *
 * [wanted] is how many were asked for and [ready] is what survived - and those two numbers
 * differ on purpose. Figures are refused about half the time, so a round that asked for six
 * and shows three is working correctly, not failing. Once [done] is true, what is on screen
 * is all there is going to be.
 */
data class Studio(
    val kind: Kind,
    /** What was asked for, so a round that has been superseded cannot write into a newer one. */
    val prompt: String,
    val wanted: Int,
    val ready: List<String> = emptyList(),
    val done: Boolean = false,
) {
    enum class Kind { Figure, Scene }
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

    private var studioJob: Job? = null

    /**
     * Asks for several pictures at once and keeps whichever arrive usable.
     *
     * Several, because one is a coin toss: a figure prompt comes back clean roughly half the
     * time, so asking for one and showing it would hand somebody a blob half the time they
     * pressed the button. They arrive in parallel and appear as they land, so the wait is
     * one generation long rather than six.
     *
     * The seed is random per round, so pressing the button twice on the same pose gives
     * different figures rather than the same rejected one again.
     */
    fun generate(kind: Studio.Kind, prompt: String) {
        studioJob?.cancel()
        val wanted = if (kind == Studio.Kind.Figure) FIGURE_TRIES else SCENE_TRIES
        _state.update { it.copy(studio = Studio(kind = kind, prompt = prompt, wanted = wanted)) }

        studioJob = viewModelScope.launch {
            val context = getApplication<Application>()
            val base = (1..100_000).random()
            // In sequence, because the service serves one caller at a time: asked for six at
            // once it answers 429 to five of them. Each result is published the moment it
            // lands, so the first picture can be chosen while the rest are still coming.
            for (index in 0 until wanted) {
                if (_state.value.studio?.prompt != prompt) return@launch
                val path = withContext(Dispatchers.IO) {
                    attempt(context, prompt, base + index, kind)
                }
                if (path != null) {
                    _state.update { state ->
                        val studio = state.studio ?: return@update state
                        if (studio.prompt != prompt) state
                        else state.copy(studio = studio.copy(ready = studio.ready + path))
                    }
                }
            }
            _state.update { it.copy(studio = it.studio?.copy(done = true)) }
        }
    }

    /**
     * One picture, waiting out the queue rather than counting a busy signal as a failure.
     *
     * A 429 arrives in under a fifth of a second, so a short wait and one more try costs
     * almost nothing and is the difference between "the service was busy" and "your picture
     * did not come out".
     */
    private suspend fun attempt(
        context: Application,
        prompt: String,
        seed: Int,
        kind: Studio.Kind,
    ): String? {
        repeat(BUSY_RETRIES) { round ->
            val file = try {
                AiArt.fetch(context, prompt, seed, wide = kind == Studio.Kind.Scene)
            } catch (busy: AiArt.Busy) {
                delay(1_500L * (round + 1))
                return@repeat
            } ?: return null
            return when (kind) {
                Studio.Kind.Scene -> file.absolutePath
                Studio.Kind.Figure -> cutOut(file.absolutePath)
            }
        }
        return null
    }

    /**
     * Keys a generated figure to transparency, writing the cut-out beside the original.
     *
     * Null when the picture was not a usable silhouette, which is the ordinary outcome
     * rather than a failure - the caller simply does not offer that one.
     */
    private fun cutOut(path: String): String? {
        val source = AiArt.decode(File(path)) ?: return null
        val cut = Silhouette.cut(source) ?: return null.also { source.recycle() }
        source.recycle()
        val out = File(path.removeSuffix(".jpg") + "-cut.png")
        return runCatching {
            out.outputStream().use { stream ->
                cut.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
            }
            out.absolutePath
        }.getOrNull().also { cut.recycle() }
    }

    fun closeStudio() {
        studioJob?.cancel()
        _state.update { it.copy(studio = null) }
    }

    /** Puts a chosen figure in the panel, standing where a figure usually stands. */
    fun addFigure(path: String) {
        updatePanel { panel ->
            val slots = listOf(0.28f to 0.78f, 0.72f to 0.78f, 0.5f to 0.82f)
            val (x, y) = slots.getOrElse(panel.actors.size) { 0.5f to 0.8f }
            panel.copy(
                actors = panel.actors + Actor(
                    id = "a${System.nanoTime()}",
                    heroKey = "",
                    heroName = "",
                    portrait = null,
                    figure = path,
                    x = x,
                    y = y,
                    flipped = panel.actors.size == 1,
                ),
            )
        }
        closeStudio()
    }

    /** Uses a chosen scene as this panel's background. */
    fun useScene(path: String) {
        updatePanel { it.copy(background = File(path).toURI().toString()) }
        closeStudio()
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

    private companion object {
        /**
         * Three, not six.
         *
         * They arrive one after another at roughly fifteen seconds each, so six would be a
         * minute and a half of waiting for choices nobody stays to see. Three is about
         * forty-five seconds, and the first appears in fifteen.
         */
        const val FIGURE_TRIES = 3

        /** Scenes almost always come back usable, so two is already a choice. */
        const val SCENE_TRIES = 2

        /** A busy signal comes back instantly; waiting it out is nearly free. */
        const val BUSY_RETRIES = 3
    }
}
