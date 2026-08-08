package com.bellizia.owcompanion.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bellizia.owcompanion.R
import com.bellizia.owcompanion.data.PlayerRepository
import com.bellizia.owcompanion.data.WikiRepository
import com.bellizia.owcompanion.data.model.HeroWiki
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One hero's line on the profile, already sorted and trimmed for display. */
data class HeroStat(
    val key: String,
    val name: String,
    val role: String,
    /** Asset URI of the portrait, or null for a hero the dataset does not know. */
    val portrait: String?,
    val games: Int,
    val winrate: Double,
    val kda: Double,
    val seconds: Long,
)

/** Which roles the hero list is narrowed to. */
enum class PlayerRole(val key: String, val labelRes: Int) {
    Tank("tank", R.string.role_tank),
    Damage("damage", R.string.role_damage),
    Support("support", R.string.role_support),
}

data class PlayerUiState(
    val query: String = "",
    val searching: Boolean = false,
    val results: List<PlayerRepository.Hit> = emptyList(),
    val selected: PlayerRepository.Hit? = null,
    /** Starred accounts, most recently added first. */
    val favourites: List<PlayerRepository.Hit> = emptyList(),
    /** True once a search has actually run, so "nobody by that name" is not shown while typing. */
    val searched: Boolean = false,
    val loading: Boolean = false,
    val summary: PlayerRepository.Summary? = null,
    /** Set when the profile is real but Blizzard is not publishing its numbers. */
    val isPrivate: Boolean = false,
    val error: String? = null,
    /** Portrait and role for every hero the dataset knows, keyed as OverFast keys them. */
    val roster: Map<String, HeroWiki> = emptyMap(),
    val roles: Set<PlayerRole> = PlayerRole.entries.toSet(),
) {
    /** Whether the profile on screen is one of the starred ones. */
    val isFavourite: Boolean
        get() = selected != null && favourites.any { it.id == selected.id }

    /** Whether stepping back to the list of results would show anything. */
    val canGoBack: Boolean
        get() = selected != null && results.isNotEmpty()

    /** Heroes worth showing, most played first, narrowed to the chosen roles. */
    val heroes: List<HeroStat>
        get() = summary?.heroes.orEmpty()
            .map { (key, block) ->
                val hero = roster[key]
                HeroStat(
                    key = key,
                    // Falling back to the slug means a hero added to the game before the
                    // next dataset build still shows up, just without a face.
                    name = hero?.name ?: key.split("-")
                        .joinToString(" ") { it.replaceFirstChar(Char::uppercase) },
                    role = hero?.role.orEmpty(),
                    portrait = WikiRepository.imageUri(hero?.portrait),
                    games = block.games,
                    winrate = block.winrate,
                    kda = block.kda,
                    seconds = block.seconds,
                )
            }
            .filter { it.games > 0 }
            .filter { stat -> roles.any { it.key.equals(stat.role, ignoreCase = true) } ||
                stat.role.isBlank() }
            .sortedByDescending { it.seconds }
}

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PlayerRepository(application)
    private val wiki = WikiRepository(application)

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var inFlight: Job? = null

    init {
        // Someone who has already said who they are should not have to say it every time.
        val favourites = repository.favourites()
        _state.update { it.copy(favourites = favourites) }
        favourites.firstOrNull()?.let(::select)
        viewModelScope.launch {
            val roster = wiki.wiki().heroes.associateBy { it.key }
            _state.update { it.copy(roster = roster) }
        }
    }

    fun setQuery(query: String) = _state.update { it.copy(query = query, searched = false) }

    fun search() {
        val name = _state.value.query
        if (name.isBlank()) return
        inFlight?.cancel()
        _state.update { it.copy(searching = true, error = null, results = emptyList()) }
        inFlight = viewModelScope.launch {
            // A whole BattleTag addresses one profile directly, and finds people the name
            // search does not index at all.
            val direct = (repository.lookup(name) as? PlayerRepository.Result.Ok)?.value
            if (direct != null) {
                select(direct)
                return@launch
            }
            when (val result = repository.search(name)) {
                is PlayerRepository.Result.Ok -> _state.update {
                    it.copy(searching = false, results = result.value, searched = true)
                }
                is PlayerRepository.Result.Failed -> _state.update {
                    it.copy(searching = false, error = result.cause, searched = true)
                }
                PlayerRepository.Result.Private ->
                    _state.update { it.copy(searching = false, searched = true) }
            }
        }
    }

    /** Opening a profile does not star it; that is a separate, deliberate act. */
    fun select(hit: PlayerRepository.Hit) {
        inFlight?.cancel()
        _state.update {
            it.copy(
                selected = hit,
                loading = true,
                summary = null,
                isPrivate = false,
                error = null,
            )
        }
        inFlight = viewModelScope.launch {
            when (val result = repository.summary(hit.id)) {
                is PlayerRepository.Result.Ok ->
                    _state.update { it.copy(loading = false, summary = result.value) }
                PlayerRepository.Result.Private ->
                    _state.update { it.copy(loading = false, isPrivate = true) }
                is PlayerRepository.Result.Failed ->
                    _state.update { it.copy(loading = false, error = result.cause) }
            }
        }
    }

    /** Back to whatever was on screen before this profile was opened. */
    fun back() {
        inFlight?.cancel()
        _state.update {
            it.copy(selected = null, summary = null, isPrivate = false, error = null)
        }
    }

    fun toggleFavourite() {
        val hit = _state.value.selected ?: return
        if (_state.value.isFavourite) repository.removeFavourite(hit) else repository.addFavourite(hit)
        _state.update { it.copy(favourites = repository.favourites()) }
    }

    fun toggleRole(role: PlayerRole) = _state.update { current ->
        // Same rule as every other filter in the app: from "everything" a tap narrows to
        // one, and turning the last one off goes back to everything.
        val all = PlayerRole.entries.toSet()
        val next = when {
            current.roles == all -> setOf(role)
            role in current.roles && current.roles.size == 1 -> all
            role in current.roles -> current.roles - role
            else -> current.roles + role
        }
        current.copy(roles = next)
    }

    fun removeFavourite(hit: PlayerRepository.Hit) {
        repository.removeFavourite(hit)
        _state.update { it.copy(favourites = repository.favourites()) }
    }
}
