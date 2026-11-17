package com.houseaffairs.app.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.houseaffairs.app.core.data.ListRow
import com.houseaffairs.app.core.data.OrganizedList
import com.houseaffairs.app.core.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// The list pieces themselves are in :ui (ListParts.kt), shared with the iPhone. This is the
// Android side: which groups are open is kept per phone in SettingsRepository.

/** Which list groups this phone has opened or closed, shared by every long list. */
@HiltViewModel
class ListGroupsViewModel @Inject constructor(private val settings: SettingsRepository) : ViewModel() {
    val toggled: StateFlow<Set<String>> = settings.toggledGroups.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    fun toggle(key: String) {
        viewModelScope.launch { settings.toggleGroup(key) }
    }
}

/** [rows] arranged for [listId] with the person's search, filters and the groups saved on this phone. */
@Composable
fun <T> rememberOrganized(
    listId: String,
    rows: List<ListRow<T>>,
    controls: ListControlsState,
    order: List<String> = emptyList(),
    openUpTo: Int = 5,
    groups: ListGroupsViewModel = hiltViewModel(),
    search: ((String) -> Set<String>)? = null,
): OrganizedList<T> {
    val toggled by groups.toggled.collectAsState()
    return rememberOrganized(listId, rows, controls, toggled, order, openUpTo, search)
}
