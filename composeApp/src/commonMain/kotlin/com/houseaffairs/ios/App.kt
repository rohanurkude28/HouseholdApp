package com.houseaffairs.ios

import kotlinx.coroutines.flow.first
import com.houseaffairs.app.core.data.restoredValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import com.houseaffairs.app.core.data.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextField
import androidx.compose.runtime.LaunchedEffect
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
import com.houseaffairs.app.core.data.plannedReminders
import com.houseaffairs.app.core.model.AppModule
import com.houseaffairs.app.core.model.Role
import com.houseaffairs.app.core.time.format
import com.houseaffairs.app.core.time.isBefore
import com.houseaffairs.app.feature.quickadd.QuickAddDraft
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.GoogleAuthProvider
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private val session = observeSession()
// Home's data through the same repository Android uses.
private val homeRepo = com.houseaffairs.app.core.data.HomeRepository()

@Composable
fun App(googleSignIn: GoogleSignInSheet? = null) {
    HouseAffairsTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val state by remember { session }.collectAsState(SessionState.Loading)
            if (smokeTest) LaunchedEffect(Unit) { SmokeTest.run() }
            joinCode?.let { code -> LaunchedEffect(code) { SmokeTest.joinOther(code) } }
            when (val s = state) {
                SessionState.Loading -> Centered { CircularProgressIndicator() }
                SessionState.SignedOut -> Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) { SignIn(googleSignIn) }
                is SessionState.NeedsHousehold -> Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) { NeedsHousehold() }
                is SessionState.Ready -> Main(s)
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { content() }
}

// ---------------------------------------------------------------------------------------------
// Signing in and getting into a home

@Composable
private fun SignIn(googleSignIn: GoogleSignInSheet?) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun attempt(block: suspend () -> Unit) {
        busy = true
        error = null
        scope.launch {
            runCatching { block() }.onFailure { error = it.message ?: "Couldn't sign in. Try again." }
            busy = false
        }
    }
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
            if (googleSignIn != null && !useEmulators) Button(enabled = !busy, onClick = { attempt { googleSignIn()?.let { signInWithGoogle(it) } } }) {
                Text(if (busy) "Signing in…" else "Continue with Google")
            }
            // Local testing: the same test family as Android's local build (the emulator takes unsigned tokens).
            if (useEmulators) TEST_PEOPLE.forEach { (id, name) ->
                OutlinedButton(enabled = !busy, onClick = { attempt { signInAsTest(id, name) } }) { Text("Sign in as $name (test)") }
            }
            TextButton(enabled = !busy, onClick = { attempt { DemoHome.start() } }) { Text("Try the demo") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
        }
    }
}

private val TEST_PEOPLE = listOf("test-asha" to "Asha", "test-ravi" to "Ravi", "test-mia" to "Mia")

private suspend fun signInAsTest(id: String, name: String) {
    val token = """{"sub":"$id","email":"${name.lowercase()}@example.com","name":"$name Test","email_verified":true}"""
    Firebase.auth.signInWithCredential(GoogleAuthProvider.credential(token, "test-access-token"))
}

/** Signed in but not in a home: join the family with their code, or start a new home. */
@Composable
private fun NeedsHousehold() {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    fun attempt(block: suspend () -> Unit) {
        busy = true
        error = null
        scope.launch {
            runCatching { block() }.onFailure { error = it.message ?: "That didn't work. Try again." }
            busy = false
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Join your family", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("On the other phone: Settings → Family → Invite. Type the 6-letter code here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            OutlinedTextField(code, { code = it.uppercase().take(6) }, label = { Text("Invite code") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        item { Button(enabled = !busy && code.length == 6, onClick = { attempt { Family.join(code) } }, modifier = Modifier.fillMaxWidth()) { Text("Join") } }
        item {
            Spacer(Modifier.height(16.dp))
            Text("Or start a new home", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        item { OutlinedTextField(name, { name = it }, label = { Text("Home name, like “Urkude home”") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedButton(enabled = !busy && name.isNotBlank(), onClick = { attempt { Family.create(name, "INR") } }, modifier = Modifier.fillMaxWidth()) { Text("Start a home") } }
        error?.let { e -> item { Text(e, color = MaterialTheme.colorScheme.error) } }
        item {
            Row {
                TextButton(enabled = !busy, onClick = { attempt { DemoHome.start() } }) { Text("Try the demo") }
                TextButton(onClick = { scope.launch { DemoHome.signOut() } }) { Text("Sign out") }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// The app: five tabs, one + to add anything, Undo after every change

private enum class Tab(val emoji: String, val label: String) { HOME("🏠", "Home"), MONEY("💰", "Money"), KITCHEN("🥘", "Kitchen"), PLANS("✅", "Plans"), THINGS("💊", "Things") }

/** What every screen needs to change things and offer Undo. */
internal class Actions(val scope: CoroutineScope, val snackbar: SnackbarHostState) {
    /** Entries deleted but still undoable: hidden from every list until the Undo has passed. */
    val hidden = androidx.compose.runtime.mutableStateListOf<String>()

    /** Says [message] once [work] is done (no Undo: a saved edit is simply edited again). */
    fun say(message: String, work: suspend () -> Unit) {
        scope.launch {
            runCatching { work() }.onFailure { snackbar.showSnackbar("That didn't save. Check the connection and try again."); return@launch }
            snackbar.showSnackbar(message, withDismissAction = true)
        }
    }

    /** Hides [id] now and deletes it only if Undo isn't pressed. */
    fun removeLater(message: String, id: String, delete: suspend () -> Unit) {
        hidden += id
        scope.launch {
            val undone = snackbar.showSnackbar(message, actionLabel = "Undo", withDismissAction = true) == SnackbarResult.ActionPerformed
            if (undone) hidden -= id else runCatching { delete() }.onFailure { hidden -= id }
        }
    }

    /** Runs a change, then shows [message] with Undo. */
    fun run(message: String, change: suspend () -> (suspend () -> Unit)) {
        scope.launch {
            val undo = runCatching { change() }.getOrElse { snackbar.showSnackbar("That didn't save. Check the connection and try again."); return@launch }
            if (snackbar.showSnackbar(message, actionLabel = "Undo", withDismissAction = true) == SnackbarResult.ActionPerformed) undo()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Main(s: SessionState.Ready) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val actions = remember(scope) { Actions(scope, snackbar) }
    val store = remember(s.household.id, s.uid, s.me) { FamilyStore(s) }
    val members by remember(s.household.id) { Family.membersOf(s.household.id) }.collectAsState(emptyList())
    val data by remember(s.household.id, s.me) { homeRepo.observe(s.household.id, s.uid, s.me.role, s.me::canAccess, s.household.baseCurrency) }.collectAsState(HomeData())
    var tab by remember { mutableStateOf(Tab.entries.firstOrNull { it.name == startTab?.substringBefore('/') } ?: Tab.HOME) }
    // "-openTab ADD" (test runs only) opens the + sheet.
    var adding by remember { mutableStateOf(startTab == "ADD" || startTab == "ADD/SCAN") }
    var me by remember { mutableStateOf(startTab == "ME") }
    var editing by remember { mutableStateOf<Editing?>(null) }
    var searching by remember { mutableStateOf(false) }
    // "-openTab FORM/EXPENSE" (test runs only) opens that kind's new form.
    var form by remember { mutableStateOf(startTab?.removePrefix("FORM/")?.takeIf { startTab?.startsWith("FORM/") == true }?.let { k -> com.houseaffairs.app.feature.add.EntryType.entries.firstOrNull { it.name == k }?.let { FormOpen(it) } }) }
    // Android's form for every kind it has; the plain fields only for anything else.
    val edit: (String, String) -> Unit = { collection, id -> if (typeFor(collection) != null) form = FormOpen(null, collection, id) else editing = Editing(collection, id) }

    // Reminders for tasks with a time and for birthdays, the same rules Android rings by.
    LaunchedEffect(data) {
        rememberWho(s)
        // Test runs ("-openTab BACKUP/DRY"): build the Drive backup without sending it, and say what's in it.
        if (data.loaded && startTab == "BACKUP/DRY" && !calendarTried) {
            calendarTried = true
            runCatching { backupFile(s) }.onSuccess { (n, file) ->
                val back = restoredValue(fromJson(parseJson(file)["collections"]!!))
                println("BACKUP $n entries, ${file.length} chars, times kept: ${Regex("__time").findAll(file).count()}, sample: ${Regex("\"__time\":\\d+").find(file)?.value} · round trip ok: ${back is Map<*, *>}")
            }.onFailure { println("BACKUP failed: ${it.message}") }
        }
        // Calendar sync, when on: at most every 6 hours, as Android's CalendarWorker.
        val cal = calendarState()
        // Once per launch, so a refused Google prompt doesn't come back on every change.
        if (data.loaded && cal.on && !calendarTried && com.houseaffairs.app.core.time.nowMillis() - (cal.lastSync ?: 0L) > 6 * 3_600_000L) {
            calendarTried = true
            runCatching { syncCalendar(s) }
        }
        if (data.loaded) {
            // The digest uses the same money numbers as Home.
            val money = if (s.me.canAccess(com.houseaffairs.app.core.model.AppModule.MONEY)) kotlinx.coroutines.withTimeoutOrNull(10_000) { tripsMoney.observeMoney(s.household.id, s.uid).first() } else null
            val digest = digestReminder(s, data, money, today(), nowMinute())
            if (useEmulators) println("DIGEST ${digest?.let { "${it.date} ${it.minute / 60}:00 ${it.title} | ${it.body.replace("\n", " / ")}" } ?: "none"}")
            scheduleReminders(allReminders(data, s.uid) + listOfNotNull(digest))
        }
    }

    val tabs = Tab.entries.filter {
        when (it) {
            Tab.HOME -> true
            // Everyone gets Money: without access it's just "My spends", as on Android.
            Tab.MONEY -> true
            Tab.KITCHEN -> s.me.canAccess(AppModule.STOCK)
            Tab.PLANS -> s.me.canAccess(AppModule.PLANS)
            Tab.THINGS -> s.me.canAccess(AppModule.MEDICINE) || s.me.canAccess(AppModule.VAULT)
        }
    }
    // The avatar opens My story (the live-to-100 wheel), with ⚙️ for Settings, as on Android.
    var settings by remember { mutableStateOf(startTab == "SETTINGS" || startTab == "SETTINGS/EXPORT") }
    if (settings) {
        SettingsScreen(s, members) { settings = false }
        return
    }
    if (me) {
        val story = com.houseaffairs.app.feature.review.rememberStoryModel(weekly, s.household.id, s.member(), s.household.baseCurrency)
        com.houseaffairs.app.feature.review.MyStoryScreen(
            onBack = { me = false }, onOpenSettings = { settings = true },
            onLogActivity = { me = false; form = FormOpen(com.houseaffairs.app.feature.add.EntryType.ACTIVITY) },
            onEditEntry = { type, id -> me = false; form = FormOpen(null, type.collection, id) },
            model = story,
        )
        return
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        // Android's bar: icons with names, and the + in the middle instead of a floating button.
        bottomBar = {
            com.houseaffairs.app.core.ui.HouseBottomBar(
                tabs.map { com.houseaffairs.app.core.ui.HouseTab.valueOf(it.name) },
                com.houseaffairs.app.core.ui.HouseTab.valueOf(tab.name),
                { picked -> tab = Tab.valueOf(picked.name) },
                onAdd = { adding = true },
            )
        },
    ) { padding ->
        // The Scaffold keeps clear of the notch and home bar; Home's sky runs up behind the clock instead.
        Box(if (tab == Tab.HOME) Modifier.padding(bottom = padding.calculateBottomPadding()) else Modifier.padding(padding)) {
            when (tab) {
                Tab.HOME -> HomeTab(
                    s, data, store, members, actions, onMe = { me = true }, onAdd = { adding = true }, onSearch = { searching = true },
                    // "money", "plans/TASKS", "stock/SHOPPING": the tab before the slash.
                    onOpen = { route ->
                        // Setup's "Invite your family" opens Settings, as on Android.
                        if (route == "settings") settings = true
                        when (route.substringBefore('/')) {
                            "money" -> Tab.MONEY
                            "stock" -> Tab.KITCHEN
                            "plans" -> Tab.PLANS
                            "vault", "things" -> Tab.THINGS
                            else -> null
                        }?.takeIf { it in tabs }?.let { tab = it }
                    },
                    onEdit = edit,
                )
                Tab.MONEY -> MoneyScreen(s, store, edit, onAdd = { adding = true }, actions = actions)
                Tab.KITCHEN -> KitchenTab(s, actions, members, onAdd = { adding = true }, onAddRecipe = { form = FormOpen(com.houseaffairs.app.feature.add.EntryType.RECIPE) }, onEdit = edit)
                Tab.PLANS -> PlansTab(store, actions, members, s, data, onAdd = { adding = true }, onEdit = edit)
                Tab.THINGS -> ThingsTab(s, actions, onAdd = { adding = true }, onEdit = edit)
            }
        }
    }
    CheckInOnOpen(s)
    if (adding) AddSheet(s, members, actions, data, onForm = { adding = false; form = it }) { adding = false }
    form?.let { f -> FormSheet(s, members, actions, f) { form = null } }
    editing?.let { e ->
        EditSheet(
            e, store,
            onSave = { msg, work -> actions.say(msg) { work() } },
            onDelete = { msg -> actions.removeLater(msg, e.id) { store.delete(e.collection, e.id) } },
            onDone = { editing = null },
        )
    }
    if (searching) SearchSheet(everything(store, actions), onOpen = { searching = false; edit(it.collection, it.entry.id) }) { searching = false }
}

/** Everything this person can see, for search: each list with what it is and where it lives. */
@Composable
private fun everything(store: FamilyStore, actions: Actions): List<Found> {
    val sources = remember(store) {
        listOf(
            Triple(store.expenses(), "Expense", "transactions"), Triple(store.shopping(), "To buy", "shopping"),
            Triple(store.stock("GROCERY"), "Pantry", "stock"), Triple(store.stock("HOUSEHOLD"), "Supplies", "stock"),
            Triple(store.medicines(), "Medicine", "medicines"), Triple(store.tasks(), "To do", "tasks"),
            Triple(store.events(), "Date", "events"), Triple(store.kept(), "Kept", "keptAt"),
        )
    }
    return sources.flatMap { (flow, kind, collection) ->
        val list by flow.collectAsState(emptyList())
        list.filter { it.id !in actions.hidden }.map { Found(it, kind, collection) }
    }
}

// ---------------------------------------------------------------------------------------------
// Adding: one box, the same parser as Android

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSheet(s: SessionState.Ready, members: List<MemberInfo>, actions: Actions, data: HomeData, onForm: (FormOpen) -> Unit = {}, onDone: () -> Unit) {
    var text by remember { mutableStateOf("") }
    // A scanned bill: the expense and each item, saved together (as Android's "Scan a bill").
    var bill by remember { mutableStateOf<QuickAddDraft?>(null) }
    var billNote by remember { mutableStateOf<String?>(null) }
    val draft = bill ?: remember(text) { if (text.isBlank()) null else runCatching { parser.parse(text) }.getOrNull() }
    val line = draft?.let(::preview)
    val answer = rememberAnswer(s, data, draft)
    // Pinned tiles stay on this phone, as on Android.
    var pinned by remember { mutableStateOf(loadPref("pinnedKinds").orEmpty().split(",").filter { it.isNotBlank() }) }
    LaunchedEffect(pinned) { savePref("pinnedKinds", pinned.joinToString(",")) }
    ModalBottomSheet(onDismissRequest = onDone, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Ask, add or find", style = MaterialTheme.typography.headlineSmall)
            // One box for everything, as on Android; its hint cycles through real examples.
            var listening by remember { mutableStateOf(false) }
            var heard by remember { mutableStateOf<String?>(null) }
            androidx.compose.runtime.DisposableEffect(Unit) { onDispose { if (listening) speech?.stop() } }
            var hint by remember { mutableStateOf(0) }
            LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(3000); hint = (hint + 1) % EXAMPLES.size } }
            TextField(
                value = text, onValueChange = { text = it; bill = null; billNote = null },
                placeholder = { Text("“${EXAMPLES[hint]}”", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), maxLines = 1) },
                textStyle = MaterialTheme.typography.titleLarge,
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                // Speak instead of typing, as Android's mic: the words fill the box as they're heard.
                trailingIcon = speech?.let { voice ->
                    {
                        IconButton(onClick = {
                            if (listening) { voice.stop(); listening = false }
                            else {
                                heard = null
                                listening = true
                                voice.start(onText = { text = it }, onEnd = { problem -> listening = false; heard = problem })
                            }
                        }) {
                            Icon(
                                if (listening) androidx.compose.material.icons.Icons.Filled.Stop else androidx.compose.material.icons.Icons.Filled.Mic,
                                contentDescription = if (listening) "Stop listening" else "Speak",
                                tint = if (listening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            if (text.isEmpty() && bill == null) Row(verticalAlignment = Alignment.CenterVertically) {
                ScanButton("Scan a bill: the expense and each item", autoStart = startTab == "ADD/SCAN") { scanned ->
                    billNote = com.houseaffairs.app.feature.add.notABill(scanned)
                    if (billNote == null) {
                        bill = com.houseaffairs.app.feature.add.billDrafts(com.houseaffairs.app.core.data.scanBill(scanned, today()))
                        if (bill == null) billNote = "Couldn't read that bill. Try again with the whole bill in the frame, flat and in good light."
                    }
                }
                Text("Scan a bill: the expense and each item", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            billNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            if (listening) Text("Listening… say it as you'd tell family.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            heard?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            when {
                draft == null -> {
                    // Android's menu: the big tiles, then everything else behind "More things to add".
                    // Each starts the line in the box, since the iPhone saves through the parser.
                    val usual = (pinned + USUAL_KEYS).distinct().mapNotNull { key -> IOS_OPTIONS.firstOrNull { it.first.key == key }?.first }.take(com.houseaffairs.app.core.data.USUAL_COUNT)
                    com.houseaffairs.app.feature.add.AddMenu(
                        usual = usual,
                        groups = IOS_GROUPS.map { (name, keys) -> name to keys.mapNotNull { key -> IOS_OPTIONS.firstOrNull { it.first.key == key }?.first } },
                        pinned = pinned.toSet(),
                        // A kind opens its form, as on Android; questions and money moves fill the box.
                        onPick = { o ->
                            val list = o.key.removePrefix("WISH:").takeIf { o.key.startsWith("WISH:") }?.let { com.houseaffairs.app.feature.quickadd.ListTemplate.valueOf(it) }
                            val kind = if (list != null) com.houseaffairs.app.feature.add.EntryType.WISH else com.houseaffairs.app.feature.add.EntryType.entries.firstOrNull { it.name == o.key && it.available }
                            if (kind != null) onForm(FormOpen(kind, preset = list?.let { mapOf("template" to it.label) } ?: emptyMap()))
                            else text = IOS_OPTIONS.first { it.first.key == o.key }.second
                        },
                        onPin = { o -> pinned = if (o.key in pinned) pinned - o.key else listOf(o.key) + pinned },
                        onExample = { text = it },
                    )
                }
                answer != null -> AnswerCard(answer)
                line == null -> Text("This one works on the Android app for now.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                        Text(line, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
                    }
                    Button(onClick = {
                        val saver = Saver(s, members)
                        actions.run(line) { saver.save(draft)?.undo ?: error("Not saved") }
                        onDone()
                    }, modifier = Modifier.fillMaxWidth(), shape = CircleShape) { Text("Save") }
                }
            }
        }
    }
}

private val EXAMPLES = listOf("450 groceries DMart", "need milk and curd", "call mom at 5 pm", "Riya birthday 12 Dec", "bought 1kg paneer", "crocin for Riya")

private fun opt(key: String, emoji: String, label: String, starts: String) = com.houseaffairs.app.feature.add.MenuOption(key, emoji, label) to starts

/** Android's options (same keys), each with the start of a line the parser understands. */
private val IOS_OPTIONS = listOf(
    opt("EXPENSE", "💸", "I spent", "spent "),
    opt("SUBSCRIPTION", "🔁", "Bills & subs", "Netflix 649 monthly"),
    opt("TRIP", "🧳", "Trip", "trip to Goa from 20 Dec"),
    opt("MOVE", "↔️", "Move money", "move 1000 from Dining to Travel"),
    opt("SHOPPING", "🛒", "Need to buy", "buy "),
    opt("GROCERY", "🥫", "Pantry", "bought "),
    opt("TASK", "✅", "To do", "remind me to "),
    opt("EVENT", "🎂", "Birthday or event", "Riya birthday 12 Dec"),
    opt("GOAL", "🏆", "Weekly goal", "goal: "),
    opt("WISH:WATCH", "🎬", "Movie or show", "watch "),
    opt("WISH:BOOKS", "📚", "Book", "read "),
    opt("WISH:MUSIC", "🎵", "Song or album", "listen to "),
    opt("WISH:GIFTS", "🎁", "Gift idea", "gift for "),
    opt("MEDICINE", "💊", "Medicine", "crocin for "),
    opt("KEPT", "📦", "Where it's kept", "keys are in the "),
    opt("PLACE", "📍", "Saved place", "try "),
    opt("ASK_WHERE", "🔎", "Where is…", "where is "),
    opt("ASK_COOK", "🍲", "What can I cook?", "what can I cook?"),
    opt("ASK_SPENT", "📊", "How much did we spend?", "how much did we spend on groceries this month?"),
)

/** Android's first four on a new phone. */
private val USUAL_KEYS = listOf("EXPENSE", "SHOPPING", "TASK", "MEDICINE")

private val IOS_GROUPS = listOf(
    "Money" to listOf("EXPENSE", "SUBSCRIPTION", "TRIP", "MOVE"),
    "Kitchen" to listOf("SHOPPING", "GROCERY"),
    "Plans" to listOf("TASK", "EVENT", "GOAL"),
    "Lists" to listOf("WISH:WATCH", "WISH:BOOKS", "WISH:MUSIC", "WISH:GIFTS"),
    "Home & health" to listOf("MEDICINE", "KEPT", "PLACE"),
    "Ask" to listOf("ASK_WHERE", "ASK_COOK", "ASK_SPENT"),
)

/** What a line will become, in words, before it's saved; null when iPhone can't save it yet. */
private fun preview(d: QuickAddDraft): String? = when (d) {
    is QuickAddDraft.Expense -> "💸 " + listOfNotNull(formatMinor(d.amount.minor(), d.currency ?: "INR"), d.category, d.merchant, d.date?.format("EEE d MMM")).joinToString(" · ")
    is QuickAddDraft.Shopping -> "🛒 Buy ${d.item}" + (d.quantity?.let { " · $it" } ?: "")
    is QuickAddDraft.Task -> "✅ " + listOfNotNull(d.title, d.due?.format("EEE d MMM"), d.time, d.repeat?.lowercase(), d.forName?.let { "for $it" }).joinToString(" · ")
    is QuickAddDraft.Event -> "🎂 " + listOfNotNull(d.title, d.kind, d.date?.format("d MMM")).joinToString(" · ")
    is QuickAddDraft.Grocery -> "🥬 In the pantry: " + listOfNotNull(d.title, d.quantity, d.expiry?.let { "use by " + it.format("d MMM") }).joinToString(" · ")
    is QuickAddDraft.Household -> "🧴 In supplies: ${d.title}"
    is QuickAddDraft.Medicine -> "💊 " + listOfNotNull(d.title, d.forName?.let { "for $it" }, d.frequency).joinToString(" · ")
    is QuickAddDraft.Subscription -> "🔁 ${d.title} · ${formatMinor(d.amount.minor(), d.currency ?: "INR")} ${d.frequency.lowercase()}"
    is QuickAddDraft.ListItem -> "${d.template.emoji} ${d.title} · ${d.template.title}"
    is QuickAddDraft.Kept -> "📦 ${d.item} · kept in ${d.location}"
    is QuickAddDraft.Goal -> "🏆 Goal this week: ${d.title}"
    is QuickAddDraft.Place -> "📍 ${d.title}"
    is QuickAddDraft.Batch -> d.drafts.mapNotNull(::preview).takeIf { it.isNotEmpty() }?.joinToString("\n")
    else -> null
}

// ---------------------------------------------------------------------------------------------
// Home

// ---------------------------------------------------------------------------------------------
// Money, Kitchen, Plans, Things

@Composable
private fun PlansTab(store: FamilyStore, actions: Actions, members: List<MemberInfo>, s: SessionState.Ready, data: HomeData, onAdd: () -> Unit, onEdit: (String, String) -> Unit) {
    // Android's parts, in Android's order; the everyday one first.
    var part by remember { mutableStateOf(startTab?.substringAfter("PLANS/", "")?.substringBefore('/')?.takeIf { it.isNotEmpty() } ?: "To do") }
    val openTasks = data.tasks.filter { it.id !in actions.hidden }
    val todo = rememberTasks(s, openTasks, actions)
    val goals by remember(s.household.id) { plansThings.observeGoals(s.household.id, s.uid) }.collectAsState(null)
    val wishes by remember(s.household.id) { plansThings.observeWishes(s.household.id, s.uid) }.collectAsState(null)
    val watch by remember(s.household.id) { plansThings.observeWatch(s.household.id, s.uid) }.collectAsState(emptyList())
    val finished by remember(s.household.id) { plansThings.observeFinished(s.household.id, s.uid) }.collectAsState(emptyList())
    // "-openTab PLANS/Lists/TRAVEL" (test runs only) opens on that list.
    var listFilter by remember { mutableStateOf(com.houseaffairs.app.feature.quickadd.ListTemplate.entries.firstOrNull { it.name == startTab?.substringAfterLast('/') }) }
    var showEarlier by remember { mutableStateOf(false) }
    var showDone by remember { mutableStateOf(false) }
    var showPast by remember { mutableStateOf(false) }
    var boardView by remember { mutableStateOf(true) }
    var placeKind by remember { mutableStateOf<String?>(null) }
    var showYear by remember { mutableStateOf(false) }
    var openDream by remember { mutableStateOf<com.houseaffairs.app.core.data.Dream?>(null) }
    val money by remember(s.household.id, s.uid) { if (s.me.canAccess(com.houseaffairs.app.core.model.AppModule.MONEY)) tripsMoney.observeMoney(s.household.id, s.uid) else kotlinx.coroutines.flow.flowOf(null) }.collectAsState(null)
    val dreams by remember(s.household.id) { tripsMoney.observeDreams(s.household.id, s.uid) }.collectAsState(emptyList())
    val places by remember(s.household.id) { plansThings.observePlaces(s.household.id, s.uid) }.collectAsState(emptyList())
    val names = members.associate { it.uid to it.displayName.substringBefore(' ') }
    // Android's own rows (from :ui), which pad themselves.
    val shared = true
    LazyColumn(
        Modifier.fillMaxSize(),
        state = com.houseaffairs.app.core.ui.rememberTopListState(part),
        contentPadding = if (shared) PaddingValues(top = 16.dp, bottom = 24.dp) else PaddingValues(16.dp),
        verticalArrangement = if (shared) Arrangement.Top else Arrangement.spacedBy(10.dp),
    ) {
        val side = if (shared) Modifier.padding(horizontal = 20.dp, vertical = 4.dp) else Modifier
        item { Box(side) { TabTitle("Plans", "${openTasks.size} to do · ${data.events.size} dates coming") } }
        item { com.houseaffairs.app.core.ui.SubTabs(listOf("To do", "Events", "Lists", "Goals", "Trips & dreams", "Places"), part, { it }, { part = it }) }
        // This month's tasks, finished things and goals, at the top where they're ticked off; tap for the year.
        if (part == "To do" || part == "Goals" || part == "Lists") item(key = "plans-month") {
            com.houseaffairs.app.feature.plans.PlansMonthCard(finished, goals.orEmpty(), onOpen = { showYear = true }, modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp))
        }
        when (part) {
            "Trips & dreams" -> tripsOn(s, money, dreams, places.map { it.title }, showPast, { showPast = !showPast }, boardView, { boardView = it }, actions, onAdd, onEdit) { openDream = it }
            "Places" -> placesOn(places, placeKind, { placeKind = it }, onAdd, onEdit)
            "Goals" -> goals?.let { goalsOn(s, it, showEarlier, { showEarlier = !showEarlier }, actions, onAdd, onEdit) } ?: item { com.houseaffairs.app.core.ui.LoadingRow() }
            "Lists" -> wishes?.let { listsOn(s, it, watch, finished, listFilter, { listFilter = it }, showDone, { showDone = !showDone }, actions, onAdd, onEdit, dreams, money) { openDream = it } } ?: item { com.houseaffairs.app.core.ui.LoadingRow() }
            "Events" -> if (!data.loaded) item { com.houseaffairs.app.core.ui.LoadingRow() } else datesOn(data.events.filter { it.id !in actions.hidden }, wishes.orEmpty(), onAdd, onEdit) { listFilter = com.houseaffairs.app.feature.quickadd.ListTemplate.GIFTS; part = "Lists" }
            else -> if (!data.loaded) item { com.houseaffairs.app.core.ui.LoadingRow() } else tasksOn(s, openTasks, todo, names, actions, onAdd, onEdit)
        }
    }
    if (showYear) com.houseaffairs.app.feature.plans.PlansYearSheet(finished, goals.orEmpty()) { showYear = false }
    openDream?.let { dream ->
        val m = money
        val cur = m?.budget?.currency ?: s.household.baseCurrency
        val hidden = s.uid in s.household.hideCostsFrom
        com.houseaffairs.app.feature.plans.DreamSheet(
            dream = dream,
            progress = m?.dreams?.firstOrNull { it.dream.id == dream.id },
            money = m, currency = cur,
            full = { if (hidden) "•••" else formatMinor(it, cur) },
            short = { if (hidden) "•••" else com.houseaffairs.app.core.util.compactMoneyText(it, cur) },
            onFund = { from, amount, label ->
                openDream = null
                actions.run("${formatMinor(amount, cur)} moved from $label to ${dream.title}") {
                    val saved = tripsMoney.transfer(s.household.id, s.uid, from, com.houseaffairs.app.core.data.Pocket.Dream(dream.id), amount); ({ saved.delete() })
                }
            },
            onAchieved = { openDream = null; tripsMoney.markDreamAchieved(s.household.id, dream.id, !dream.achieved) },
            onEdit = { openDream = null; onEdit("dreams", dream.id) },
            onPlanTrip = { openDream = null; onAdd() },
            onDismiss = { openDream = null },
        )
    }
}

private val plansThings = com.houseaffairs.app.core.data.ThingsRepository()
private val tripsMoney = com.houseaffairs.app.core.data.MoneyRepository()

// ---------------------------------------------------------------------------------------------
// Pieces

@Composable
private fun TabTitle(title: String, line: String) {
    Column {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(line, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A tab's parts as chips, all in sight. */
@Composable
private fun Parts(parts: List<String>, selected: String, onPick: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        parts.forEach { p ->
            FilterChip(
                selected = p == selected, onClick = { onPick(p) }, label = { Text(p) }, shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary),
            )
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
private fun TaskRow(row: TodayRow.Task, onOpen: () -> Unit, onDone: () -> Unit) {
    RowCard(onClick = onOpen) {
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
                } + (row.task.time?.let { " · $it" } ?: "") + (row.task.repeat?.let { " · repeats ${it.lowercase()}" } ?: ""),
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

private var calendarTried = false
