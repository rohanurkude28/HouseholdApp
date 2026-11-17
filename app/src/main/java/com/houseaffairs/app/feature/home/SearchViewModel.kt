package com.houseaffairs.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.houseaffairs.app.core.data.Findable
import com.houseaffairs.app.core.data.FoundGroup
import com.houseaffairs.app.core.data.SEARCH_GROUPS
import com.houseaffairs.app.core.data.SearchRepository
import com.houseaffairs.app.core.data.SearchSummary
import com.houseaffairs.app.core.data.SessionManager
import com.houseaffairs.app.core.data.SessionState
import com.houseaffairs.app.core.data.SettingsRepository
import com.houseaffairs.app.core.data.didYouMean
import com.houseaffairs.app.core.data.searchAll
import com.houseaffairs.app.core.data.searchSummary
import com.houseaffairs.app.core.model.RolePolicy
import com.houseaffairs.app.feature.add.EntryType
import com.houseaffairs.app.feature.quickadd.QuickAddParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val loading: Boolean = true,
    val groups: List<FoundGroup> = emptyList(),
    /** The kind the results are narrowed to ("Spending"), or null for all. */
    val kind: String? = null,
    val summary: SearchSummary? = null,
    /** When nothing matched: the closest words the home does have. */
    val suggestion: String? = null,
    /** What the search was read as ("Spending", "Last month", "Over ₹1000"), shown as chips. */
    val understood: List<String> = emptyList(),
    /** The family's tags with how many things carry each, most used first. */
    val tags: List<Pair<String, Int>> = emptyList(),
    /** Topics worth a tap before anything is typed. */
    val ideas: List<String> = listOf("Travel", "Food", "Health", "Bills", "Car", "Documents", "Gift", "Kids"),
) {
    /** Results after the kind chip, if one is picked. */
    val shown: List<FoundGroup> get() = kind?.let { k -> groups.filter { it.group == k } }?.takeIf { it.isNotEmpty() } ?: groups
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val session: SessionManager,
    private val search: SearchRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()
    val recent: StateFlow<List<String>> = settings.recentSearches.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private var everything: List<Findable> = emptyList()
    // The parser's words for spending categories: "petrol" → Transport.
    private val parser = QuickAddParser()

    /** Read afresh each time the sheet opens: a one-off read, so it never goes stale behind the sheet. */
    fun open() {
        val ready = session.state.value as? SessionState.Ready ?: return
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            val collections = EntryType.entries
                .filter { it.collection in SEARCH_GROUPS && ready.me.canAccess(it.module) }
                .map { it.collection }
            everything = search.load(ready.household.id, ready.user.uid, collections, RolePolicy.ownMedicinesOnly(ready.me.role))
            val tags = everything.flatMap { it.tags }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(10).map { it.key to it.value }
            _state.update { recompute(it.copy(loading = false, tags = tags)) }
        }
    }

    fun ask(query: String) = _state.update { recompute(it.copy(query = query, kind = null)) }

    /** A kind chip: narrow to it, or tap again for everything. */
    fun pickKind(kind: String?) = _state.update { it.copy(kind = if (it.kind == kind) null else kind) }

    /** Remembered when a search leads somewhere: a result opened, or search pressed with results. */
    fun remember() {
        val q = _state.value.query
        if (_state.value.groups.isNotEmpty()) viewModelScope.launch { settings.rememberSearch(q) }
    }

    fun clearRecent() = viewModelScope.launch { settings.clearSearches() }

    private fun recompute(s: SearchUiState): SearchUiState {
        val groups = searchAll(s.query, everything, parser::categoryOfWord)
        return s.copy(
            groups = groups,
            understood = com.houseaffairs.app.core.data.parseSearch(s.query, java.time.LocalDate.now()).understood,
            summary = searchSummary(groups),
            suggestion = if (groups.isEmpty() && !s.loading) didYouMean(s.query, everything) else null,
        )
    }

    fun typeOf(item: Findable): EntryType? = item.entryType()
}

/** The form that opens a hit: supplies share the pantry's collection. */
fun Findable.entryType(): EntryType? = EntryType.entries.firstOrNull {
    it.collection == collection && (collection != "stock" || (it == EntryType.HOUSEHOLD) == supply)
}
