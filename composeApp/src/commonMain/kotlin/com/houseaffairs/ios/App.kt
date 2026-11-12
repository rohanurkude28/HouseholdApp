package com.houseaffairs.ios

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.houseaffairs.app.core.data.Attention
import com.houseaffairs.app.core.data.COMING_UP_COLLECTIONS
import com.houseaffairs.app.core.data.HomeData
import com.houseaffairs.app.core.data.Priority
import com.houseaffairs.app.core.data.TodayRow
import com.houseaffairs.app.core.data.buildAttention
import com.houseaffairs.app.core.data.buildToday
import com.houseaffairs.app.core.model.AppModule
import com.houseaffairs.app.core.time.format
import kotlinx.coroutines.launch

private val session = observeSession()
private val homeStore = HomeStore()

@Composable
fun App(googleSignIn: GoogleSignInSheet? = null) {
    HouseAffairsTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val state by remember { session }.collectAsState(SessionState.Loading)
            Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
                when (val s = state) {
                    SessionState.Loading -> Centered { CircularProgressIndicator() }
                    SessionState.SignedOut -> SignIn(googleSignIn)
                    is SessionState.NeedsHousehold -> NeedsHousehold()
                    is SessionState.Ready -> Home(s)
                }
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun SignIn(googleSignIn: GoogleSignInSheet?) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Centered {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("🏡", style = MaterialTheme.typography.displayMedium)
            Text("HouseAffairs", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "Money, kitchen, medicines and plans for the whole family.",
                style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            // The same Google account as on Android opens the same home.
            if (googleSignIn != null) Button(
                enabled = !busy,
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        runCatching { googleSignIn()?.let { signInWithGoogle(it) } }
                            .onFailure { error = it.message ?: "Couldn't sign in with Google. Try again." }
                        busy = false
                    }
                },
            ) { Text(if (busy) "Signing in…" else "Continue with Google") }
            TextButton(
                enabled = !busy,
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        runCatching { DemoHome.start() }.onFailure { error = it.message ?: "Couldn't start the demo. Try again." }
                        busy = false
                    }
                },
            ) { Text("Try the demo") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
        }
    }
}

@Composable
private fun NeedsHousehold() {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    Centered {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("You're not in a home yet", style = MaterialTheme.typography.titleLarge)
            Text("Creating or joining a family home isn't on iPhone yet. Join from the Android app with this Google account, or start a demo home to look around.", textAlign = TextAlign.Center)
            Button(enabled = !busy, onClick = { busy = true; scope.launch { runCatching { DemoHome.start() }; busy = false } }) { Text("Start a demo home") }
            TextButton(onClick = { scope.launch { DemoHome.signOut() } }) { Text("Sign out") }
        }
    }
}

@Composable
private fun Home(s: SessionState.Ready) {
    val scope = rememberCoroutineScope()
    val hid = s.household.id
    val data by remember(hid, s.me) { homeStore.observe(hid, s.me, s.household.baseCurrency) }.collectAsState(HomeData())
    if (!data.loaded) return Centered { CircularProgressIndicator() }

    val today = today()
    val money: (Long) -> String = { formatMinor(it, s.household.baseCurrency) }
    val attention = buildAttention(data, today, money = money)
    val todayRows = buildToday(attention, data.tasks, today)
    val comingUp = attention.filter { it.priority == Priority.SOON && it.collection in COMING_UP_COLLECTIONS }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(today.format("EEEE, d MMMM").uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(s.household.name.ifBlank { "Home" }, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = { scope.launch { DemoHome.signOut() } }) { Text("Sign out") }
            }
        }
        item { Stats(data, s) }
        item { SectionTitle("Today", todayRows.size) }
        if (todayRows.isEmpty()) item { Muted("Nothing needs you today.") }
        items(todayRows, key = { it.key() }) { row ->
            when (row) {
                is TodayRow.Task -> TaskRow(row) { scope.launch { homeStore.completeTask(hid, row.task.id) } }
                is TodayRow.Alert -> AlertRow(row.item)
            }
        }
        if (comingUp.isNotEmpty()) {
            item { SectionTitle("Coming up", comingUp.size) }
            items(comingUp, key = { "up-" + it.id }) { AlertRow(it) }
        }
        if (s.me.canAccess(AppModule.STOCK)) {
            item { SectionTitle("Shopping list", data.shopping.size) }
            if (data.shopping.isEmpty()) item { Muted("Nothing to buy.") }
            items(data.shopping, key = { "shop-" + it.id }) { item ->
                RowCard(onClick = { scope.launch { homeStore.markBought(hid, item.id) } }) {
                    Text(item.aisle.emoji)
                    Spacer(Modifier.width(12.dp))
                    Text(item.name, Modifier.weight(1f))
                    Text("Bought", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

private fun TodayRow.key(): String = when (this) {
    is TodayRow.Task -> "task-" + task.id
    is TodayRow.Alert -> "alert-" + item.id
}

@Composable
private fun Stats(data: HomeData, s: SessionState.Ready) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (s.me.canAccess(AppModule.MONEY)) Stat("Spent this month", formatMinor(data.monthSpentMinor, s.household.baseCurrency), Modifier.weight(1f))
        Stat("To buy", data.shopping.size.toString(), Modifier.weight(1f))
        Stat("Open tasks", data.tasks.size.toString(), Modifier.weight(1f))
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun SectionTitle(title: String, count: Int) {
    Text(if (count > 0) "$title · $count" else title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun Muted(text: String) = Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun RowCard(onClick: (() -> Unit)? = null, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

@Composable
private fun PriorityDot(priority: Priority?) {
    val color = when (priority) {
        Priority.URGENT -> MaterialTheme.colorScheme.error
        Priority.TODAY -> MaterialTheme.colorScheme.secondary
        Priority.SOON -> MaterialTheme.colorScheme.primary
        null -> MaterialTheme.colorScheme.outline
    }
    Box(Modifier.size(10.dp).background(color, CircleShape))
}

@Composable
private fun TaskRow(row: TodayRow.Task, onDone: () -> Unit) {
    RowCard {
        Checkbox(checked = false, onCheckedChange = { onDone() })
        Column(Modifier.weight(1f)) {
            Text(row.task.title, style = MaterialTheme.typography.bodyLarge)
            val due = row.task.dueDate
            Text(
                when (row.priority) {
                    Priority.URGENT -> "Overdue"
                    Priority.TODAY -> "Due today"
                    Priority.SOON -> "Due ${due?.format("EEE d MMM")}"
                    null -> "No date"
                } + (row.task.repeat?.let { " · repeats ${it.lowercase()}" } ?: ""),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        PriorityDot(row.priority)
    }
}

@Composable
private fun AlertRow(item: Attention) {
    RowCard {
        Text(
            when (item.area) {
                com.houseaffairs.app.core.data.Area.MONEY -> "💸"
                com.houseaffairs.app.core.data.Area.KITCHEN -> "🥘"
                com.houseaffairs.app.core.data.Area.HEALTH -> "💊"
                com.houseaffairs.app.core.data.Area.PLANS -> "📅"
            },
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(item.title, style = MaterialTheme.typography.bodyLarge)
            Text(item.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        PriorityDot(item.priority)
    }
}

