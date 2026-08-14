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
    /**
     * Set when there is no profile under that BattleTag at all.
     *
     * Different from every other failure, and worth saying so: a saved favourite that
     * answers this will answer it forever, because the player renamed or the tag was saved
     * wrong. "Try again" is the wrong advice; "check the name" is the right one.
     */
    val isGone: Boolean = false,
    val error: String? = null,
    /** The hero whose full figures are open, and what they are. */
    val openHero: HeroStat? = null,
    val heroStats: List<PlayerRepository.StatGroup> = emptyList(),
    val heroStatsLoading: Boolean = false,
    /** This season's competitive placements, when the profile shows any. */
    val ranks: PlayerRepository.Ranks? = null,
    /** Which queue the numbers on screen describe. */
    val mode: PlayerRepository.Mode = PlayerRepository.Mode.Everything,
    /** Portrait and role for every hero the dataset knows, keyed as OverFast keys them. */
    val roster: Map<String, HeroWiki> = emptyMap(),
    val roles: Set<PlayerRole> = PlayerRole.entries.toSet(),
) {
    /** Whether the profile on screen is one of the starred ones. */
    val isFavourite: Boolean
        get() = selected != null && favourites.any { it.id == selected.id }

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

    /** Empties the field and the stale results under it, ready for a different name. */
    fun clearQuery() = _state.update {
        it.copy(query = "", searched = false, results = emptyList(), error = null)
    }

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
                PlayerRepository.Result.Private, PlayerRepository.Result.Gone ->
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
                ranks = null,
                isPrivate = false,
                error = null,
            )
        }
        inFlight = viewModelScope.launch {
            // The placements come from a different endpoint, and are not worth making the
            // career figures wait for: whichever arrives first is shown.
            launch {
                val ranks = repository.ranks(hit.id)
                _state.update { if (it.selected?.id == hit.id) it.copy(ranks = ranks) else it }
            }
            when (val result = repository.summary(hit.id, _state.value.mode)) {
                is PlayerRepository.Result.Ok ->
                    _state.update { it.copy(loading = false, summary = result.value) }
                PlayerRepository.Result.Private ->
                    _state.update { it.copy(loading = false, isPrivate = true) }
                PlayerRepository.Result.Gone ->
                    _state.update { it.copy(loading = false, isGone = true) }
                is PlayerRepository.Result.Failed ->
                    _state.update { it.copy(loading = false, error = result.cause) }
            }
        }
    }

    /**
     * Reloads the open profile for a different queue.
     *
     * Quick play dwarfs competitive for most people - twelve thousand games against
     * nineteen, on the account this was tested with - so a combined figure is really a
     * quick play figure wearing a hat.
     */
    fun mode(mode: PlayerRepository.Mode) {
        if (mode == _state.value.mode) return
        _state.update { it.copy(mode = mode) }
        _state.value.selected?.let { select(it) }
    }

    /** Everything the profile records about one hero, fetched on demand. */
    fun openHero(hero: HeroStat) {
        val player = _state.value.selected ?: return
        _state.update { it.copy(openHero = hero, heroStats = emptyList(), heroStatsLoading = true) }
        viewModelScope.launch {
            val groups = repository.heroStats(player.id, hero.key, _state.value.mode)
            _state.update {
                if (it.openHero?.key == hero.key) {
                    it.copy(heroStats = groups, heroStatsLoading = false)
                } else {
                    it
                }
            }
        }
    }

    fun closeHero() = _state.update {
        it.copy(openHero = null, heroStats = emptyList(), heroStatsLoading = false)
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

    fun setRoles(roles: Set<PlayerRole>) = _state.update { it.copy(roles = roles) }

    fun removeFavourite(hit: PlayerRepository.Hit) {
        repository.removeFavourite(hit)
        _state.update { it.copy(favourites = repository.favourites()) }
    }
}
