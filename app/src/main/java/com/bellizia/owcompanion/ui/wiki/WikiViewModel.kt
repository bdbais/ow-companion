package com.bellizia.owcompanion.ui.wiki

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import com.bellizia.owcompanion.R
import androidx.lifecycle.viewModelScope
import com.bellizia.owcompanion.data.DatasetRepository
import com.bellizia.owcompanion.data.WikiRepository
import com.bellizia.owcompanion.sim.WeaponSpec
import com.bellizia.owcompanion.data.model.HeroWiki
import com.bellizia.owcompanion.ui.chart.HeroRole
import com.bellizia.owcompanion.ui.common.Masked
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class HeroSort(@StringRes val labelRes: Int) {
    Name(R.string.wiki_sort_name),
    Release(R.string.wiki_sort_release),
    Health(R.string.wiki_sort_health),
    Changes(R.string.wiki_sort_changes),
}

/** A term the roster does not contain. Masked so that reading this file does not spend it. */
internal val SHORTHAND: String by lazy { Masked.text("PtBfqA==") }

/** What it resolves to, in the order it resolves to. */
internal val SHORTLIST: List<String> by lazy { Masked.list("DcxYrB77O85ZsXbXNMQjghr+Pw==") }

data class WikiUiState(
    val loading: Boolean = true,
    val heroes: List<HeroWiki> = emptyList(),
    val query: String = "",
    val roles: Set<HeroRole> = HeroRole.entries.toSet(),
    val sort: HeroSort = HeroSort.Name,
    val selectedKey: String? = null,
    /** Needed for the combo: a hero's own guns are half of any opening. */
    val weapons: List<WeaponSpec> = emptyList(),
) {
    /**
     * Whether the query is the shorthand rather than a hero anybody is looking for.
     *
     * Typed in full it narrows the list to three names and nothing else, which is not a
     * result a search for a hero ever reaches.
     */
    val narrowed: Boolean get() = query.trim().equals(SHORTHAND, ignoreCase = true)

    val visible: List<HeroWiki>
        get() = if (narrowed) {
            SHORTLIST.mapNotNull { name -> heroes.firstOrNull { it.name == name } }
        } else {
            broadlyVisible
        }

    private val broadlyVisible: List<HeroWiki>
        get() = heroes
            .filter { hero ->
                val role = HeroRole.of(hero.role)
                val roleMatches = role == null || role in roles
                val queryMatches = query.isBlank() ||
                    hero.name.contains(query, ignoreCase = true) ||
                    hero.abilities.any { it.name?.contains(query, ignoreCase = true) == true }
                roleMatches && queryMatches
            }
            .sortedWith(
                when (sort) {
                    HeroSort.Name -> compareBy { it.name }
                    // Newest first: what changed recently is what people look up.
                    HeroSort.Release -> compareByDescending { it.releaseDate ?: "" }
                    HeroSort.Health -> compareByDescending { it.totalHitpoints ?: 0 }
                    HeroSort.Changes -> compareByDescending { hero ->
                        hero.patches.sumOf { it.changes.size }
                    }
                },
            )

    val selected: HeroWiki?
        get() = heroes.firstOrNull { it.key == selectedKey }
}

class WikiViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = WikiRepository(application)
    private val weapons = DatasetRepository(application)

    private val _state = MutableStateFlow(WikiUiState())
    val state: StateFlow<WikiUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val wiki = repository.wiki()
            _state.update { it.copy(loading = false, heroes = wiki.heroes) }
            val set = weapons.weapons()
            _state.update { it.copy(weapons = set.weapons) }
        }
    }

    fun setQuery(query: String) = _state.update { it.copy(query = query) }


    fun setSort(sort: HeroSort) = _state.update { it.copy(sort = sort) }

    fun setRoles(roles: Set<HeroRole>) = _state.update { it.copy(roles = roles) }

    fun select(key: String?) = _state.update { it.copy(selectedKey = key) }
}
