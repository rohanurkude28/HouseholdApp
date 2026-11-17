package com.houseaffairs.app.feature.review

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.houseaffairs.app.core.data.SessionManager
import com.houseaffairs.app.core.data.SessionState
import com.houseaffairs.app.core.data.SettingsRepository
import com.houseaffairs.app.core.data.WeeklyRepository
import com.houseaffairs.app.core.data.WellPlan
import com.houseaffairs.app.core.time.isoWeek
import com.houseaffairs.app.feature.add.EntryType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

// Android's way into the shared live-to-100 screens (:ui WeeklyReview.kt, WellPlanSheet.kt):
// Hilt hands over the reader and the session; the screens themselves are the iPhone's too.

/** What My story needs from Android: the shared reader, who's signed in, and marking it seen. */
@HiltViewModel
class StoryViewModel @Inject constructor(
    val session: SessionManager,
    val weekly: WeeklyRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    fun markSeen() { viewModelScope.launch { settings.markReviewSeen(isoWeek(LocalDate.now())) } }
}

@Composable
fun WeeklyReviewScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onLogActivity: () -> Unit,
    onEditEntry: (EntryType, String) -> Unit = { _, _ -> },
    /** Changes when something is saved elsewhere, so the week is read again. */
    refreshKey: Int = 0,
    viewModel: StoryViewModel = hiltViewModel(),
) {
    val r = viewModel.session.state.value as? SessionState.Ready ?: return
    LaunchedEffect(Unit) { viewModel.markSeen() }
    val model = rememberStoryModel(viewModel.weekly, r.household.id, r.me, r.household.baseCurrency)
    MyStoryScreen(onBack, onOpenSettings, onLogActivity, onEditEntry, refreshKey, model)
}

/** Loads and saves the signed-in person's live-well plan. */
@HiltViewModel
class WellPlanViewModel @Inject constructor(
    private val session: SessionManager,
    private val weekly: WeeklyRepository,
) : ViewModel() {
    private val _plan = MutableStateFlow<WellPlan?>(null)
    val plan = _plan.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        val r = session.state.value as? SessionState.Ready ?: return
        viewModelScope.launch { _plan.value = runCatching { weekly.loadPlan(r.household.id, r.user.uid) }.getOrDefault(WellPlan()) }
    }

    fun save(plan: WellPlan) {
        val r = session.state.value as? SessionState.Ready ?: return
        weekly.savePlan(r.household.id, r.user.uid, plan)
        _plan.value = plan
    }
}
/** Android's plan sheet: the shared one, loaded and saved through [WellPlanViewModel]. */
@Composable
fun WellPlanSheet(onDismiss: () -> Unit, onSaved: () -> Unit = {}, viewModel: WellPlanViewModel = hiltViewModel()) {
    val plan by viewModel.plan.collectAsStateWithLifecycle()
    WellPlanSheet(plan, onSave = { viewModel.save(it); onSaved() }, onDismiss = onDismiss)
}
