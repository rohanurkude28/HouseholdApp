package com.houseaffairs.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.houseaffairs.app.core.data.Findable
import com.houseaffairs.app.core.data.FoundGroup
import com.houseaffairs.app.core.model.RolePolicy
import com.houseaffairs.app.core.data.SEARCH_GROUPS
import com.houseaffairs.app.core.data.SearchRepository
import com.houseaffairs.app.core.data.SessionManager
import com.houseaffairs.app.core.data.SessionState
import com.houseaffairs.app.core.data.searchAll
import com.houseaffairs.app.feature.add.EntryType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val loading: Boolean = true,
    val groups: List<FoundGroup> = emptyList(),
    /** Topics worth a tap before anything is typed. */
    val ideas: List<String> = listOf("Travel", "Health", "Car", "School", "Bills", "Gift"),
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val session: SessionManager,
    private val search: SearchRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()
    private var everything: List<Findable> = emptyList()

    /** Read afresh each time the sheet opens: a one-off read, so it never goes stale behind the sheet. */
    fun open() {
        val ready = session.state.value as? SessionState.Ready ?: return
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            val collections = EntryType.entries
                .filter { it.collection in SEARCH_GROUPS && ready.me.canAccess(it.module) }
                .map { it.collection }
            everything = search.load(ready.household.id, ready.user.uid, collections, RolePolicy.ownMedicinesOnly(ready.me.role))
            _state.update { it.copy(loading = false, groups = searchAll(it.query, everything)) }
        }
    }

    fun ask(query: String) = _state.update { it.copy(query = query, groups = searchAll(query, everything)) }

    /** The form that opens a hit: supplies share the pantry's collection. */
    fun typeOf(item: Findable): EntryType? = EntryType.entries.firstOrNull {
        it.collection == item.collection && (item.collection != "stock" || (it == EntryType.HOUSEHOLD) == item.supply)
    }
}
