package com.houseaffairs.app.core.photo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.houseaffairs.app.core.data.GalleryPhoto
import com.houseaffairs.app.core.data.MoneyRepository
import com.houseaffairs.app.core.data.PantryRepository
import com.houseaffairs.app.core.data.SessionManager
import com.houseaffairs.app.core.data.SessionState
import com.houseaffairs.app.core.data.ThingsRepository
import com.houseaffairs.app.core.data.WatchRepository
import com.houseaffairs.app.core.data.galleryGroups
import com.houseaffairs.app.core.model.AppModule
import com.houseaffairs.app.core.model.RolePolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Every photo the family added, from each list this person's role can open. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GalleryViewModel @Inject constructor(
    session: SessionManager,
    things: ThingsRepository,
    pantry: PantryRepository,
    watch: WatchRepository,
    money: MoneyRepository,
) : ViewModel() {
    val photos: StateFlow<List<GalleryPhoto>> = session.state.filterIsInstance<SessionState.Ready>().flatMapLatest { r ->
        val hid = r.household.id
        val uid = r.user.uid
        fun <T> gated(module: AppModule, flow: () -> Flow<List<T>>): Flow<List<T>> = if (r.me.canAccess(module)) flow() else flowOf(emptyList())
        fun each(ids: List<String>, title: String, group: String, type: String, id: String) = ids.map { GalleryPhoto(it, title, group, type, id) }
        combine(
            listOf(
                gated(AppModule.PLANS) { money.observeDreams(hid, uid) }.map { l -> l.flatMap { each(it.photos, it.title, "Vision board", "DREAM", it.id) } },
                gated(AppModule.PLANS) { things.observeWishes(hid, uid) }.map { l -> l.flatMap { each(it.photos, it.title, "Wishes & lists", "WISH", it.id) } },
                gated(AppModule.PLANS) { watch.observe(hid, uid) }.map { l -> l.flatMap { each(it.photos, it.title, "Wishes & lists", "WISH", it.id) } },
                gated(AppModule.STOCK) { pantry.observeRecipes(hid, uid) }.map { l -> l.flatMap { each(it.photos, it.title, "Recipes", "RECIPE", it.id) } },
                gated(AppModule.VAULT) { things.observeKept(hid, uid) }.map { l -> l.flatMap { each(it.photos, it.title, "Where it's kept", "KEPT", it.id) } },
                gated(AppModule.MEDICINE) { pantry.observeMedicines(hid, uid, RolePolicy.ownMedicinesOnly(r.me.role)) }.map { l -> l.flatMap { each(it.photos, it.title, "Medicines", "MEDICINE", it.id) } },
                gated(AppModule.STOCK) { pantry.observePantry(hid, uid) }.map { l -> l.flatMap { each(it.photos, it.title, "Kitchen & supplies", "GROCERY", it.id) } },
                gated(AppModule.STOCK) { pantry.observeSupplies(hid, uid) }.map { l -> l.flatMap { each(it.photos, it.title, "Kitchen & supplies", "HOUSEHOLD", it.id) } },
            ),
        ) { parts -> parts.flatMap { it } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/**
 * All the pictures in one place, grouped by where they live. A tap opens one full screen, with a
 * way to the entry it belongs to.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GallerySheet(onDismiss: () -> Unit, onOpenEntry: (type: String, id: String) -> Unit, viewModel: GalleryViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val groups = remember(photos) { galleryGroups(photos) { PhotoStore.has(context, it) } }
    var viewing by remember { mutableStateOf<GalleryPhoto?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxWidth().height(640.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { Text("Photos", style = MaterialTheme.typography.headlineSmall) }
            if (groups.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("Add a photo to a dream, a wish, a recipe, a medicine or something you've kept, and it shows up here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            groups.forEach { (group, list) ->
                item(span = { GridItemSpan(maxLineSpan) }) { Text("$group · ${list.size}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp)) }
                items(list, key = { "$group-${it.photoId}" }) { p ->
                    Column {
                        PhotoThumb(p.photoId, 108.dp) { viewing = p }
                        Text(p.title, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
    viewing?.let { p ->
        PhotoViewer(
            p.photoId, onDismiss = { viewing = null }, caption = "${p.title} · ${p.group}",
            actionLabel = "Open", onAction = { viewing = null; onDismiss(); onOpenEntry(p.entryType, p.entryId) },
        )
    }
}
