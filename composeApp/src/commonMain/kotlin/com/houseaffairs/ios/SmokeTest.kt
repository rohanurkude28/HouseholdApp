package com.houseaffairs.ios

import com.houseaffairs.app.core.data.*
import com.houseaffairs.app.core.model.Role
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.GoogleAuthProvider
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterIsInstance

/**
 * Local testing only (emulators + "-smokeTest"): walks the iPhone's main flows the way a person
 * would, so they're checked without tapping: Asha starts a home, adds things, makes an invite;
 * Ravi joins with it and sees them. Each step prints "SMOKE ok/FAIL …" to the device log.
 */
object SmokeTest {
    private fun log(ok: Boolean, step: String) = println("SMOKE ${if (ok) "ok" else "FAIL"} $step")

    private suspend fun step(name: String, block: suspend () -> Unit) =
        runCatching { block() }.onSuccess { log(true, name) }.onFailure { log(false, "$name: ${it.message}") }

    private suspend fun signIn(id: String, name: String) {
        Firebase.auth.signOut()
        val token = """{"sub":"$id-${(1000..9999).random()}","email":"${name.lowercase()}@example.com","name":"$name Test","email_verified":true}"""
        Firebase.auth.signInWithCredential(GoogleAuthProvider.credential(token, "test-access-token"))
    }

    private suspend fun ready(): SessionState.Ready = observeSession().filterIsInstance<SessionState.Ready>().first()

    /** Joins another phone's home with [code] as a new test person, then reads it the way the screens do. */
    suspend fun joinOther(code: String) {
        step("sign in as Ravi") { signIn("join-ravi", "Ravi") }
        step("join with $code") { Family.join(code) }
        step("see that home's things") {
            val r = ready()
            val home = com.houseaffairs.app.core.data.HomeRepository().observe(r.household.id, r.uid, r.me.role, r.me::canAccess, r.household.baseCurrency).first { it.loaded }
            val money = com.houseaffairs.app.core.data.MoneyRepository().observeMoney(r.household.id, r.uid).first()
            println("SMOKE joined ${r.household.name}: tasks=${home.tasks.size} shopping=${home.shopping.size} events=${home.events.size} spent=${home.monthSpentMinor} budgetLeft=${money?.summary?.leftMinor}")
        }
        step("add to the shared buy list") {
            val r = ready()
            Saver(r, emptyList()).save(parser.parse("need bread from iphone")!!) ?: error("not saved")
        }
        println("SMOKE done")
    }

    suspend fun run() {
        var code = ""
        step("sign in as Asha") { signIn("smoke-asha", "Asha") }
        step("start a home") { Family.create("Smoke home", "INR") }
        val asha = ready()
        val saver = Saver(asha, emptyList())
        for (line in listOf("450 groceries DMart", "need milk and eggs", "call mom at 5 pm tomorrow", "Riya birthday 12 Dec", "bought 1kg paneer", "crocin expires march 2027", "keys are in the blue drawer", "netflix 649 monthly")) {
            step("add “$line”") { saver.save(parser.parse(line)!!) ?: error("not saved") }
        }
        val store = FamilyStore(asha)
        step("read lists") {
            delay(1500)
            val counts = listOf(store.expenses().first().size, store.shopping().first().size, store.tasks().first().size, store.events().first().size, store.stock("GROCERY").first().size, store.medicines().first().size, store.kept().first().size)
            println("SMOKE counts expenses/shopping/tasks/events/pantry/medicines/kept = $counts")
            check(counts.all { it > 0 }) { "an empty list: $counts" }
        }
        step("tick off and undo") {
            val task = store.tasks().first().first()
            val undo = store.complete(task)
            undo()
            val milk = store.shopping().first().first()
            store.bought(milk)
        }
        step("change an expense") {
            val e = store.expenses().first().first()
            store.update("transactions", e.id, listOf("amountMinor" to 500_00L, "merchant" to "DMart Ready"))
            delay(800)
            check(store.expenses().first().first().amountMinor == 500_00L) { "amount not changed" }
        }
        step("search finds it") {
            val all = store.expenses().first().map { Found(it, "Expense", "transactions") } + store.kept().first().map { Found(it, "Kept", "keptAt") }
            check(searchFound("dmart", all).isNotEmpty()) { "dmart not found" }
            check(searchFound("keys", all).isNotEmpty()) { "keys not found" }
        }
        step("budget says what's left") {
            val db = dev.gitlive.firebase.Firebase.firestore
            val batch = db.batch()
            batch.set(
                db.collection("households").document(asha.household.id).collection("budgets").document("main"),
                mapOf("limitMinor" to 50_000_00L, "currency" to "INR", "cycleStartDay" to 1, "savingsGoalMinor" to 10_000_00L, "categories" to mapOf("Groceries" to 8_000_00L),
                    "createdBy" to asha.uid, "visibleTo" to listOf("*")),
            )
            batch.commit()
            val b = store.budget().first { it != null }!!
            println("SMOKE budget left=${b.leftMinor} line=${com.houseaffairs.app.core.data.spendLine(b, { formatMinor(it, "INR") }, null)}")
            check(b.leftMinor == 50_000_00L - 500_00L) { "left was ${b.leftMinor}" }
        }
        step("shared Money: month, year and savings") {
            val m = com.houseaffairs.app.core.data.MoneyRepository().observeMoney(asha.household.id, asha.uid).first { it != null }!!
            val y = com.houseaffairs.app.core.data.yearOutlook(m.budget, m.startedOn, m.txns, m.dreams.map { it.dream }, m.trips.map { it.trip }, today())
            val sb = com.houseaffairs.app.core.data.savingsBreakdown(m)
            println("SMOKE money left=${m.summary.leftMinor} months=${y.months.size} gullak=${sb.gullakMinor} envelopes=${m.envelopes.envelopes.size}")
            check(m.summary.limitMinor == 50_000_00L) { "budget not read" }
        }
        step("answers: do we have paneer") {
            val stock = com.houseaffairs.app.core.data.PantryRepository().observePantry(asha.household.id, asha.uid).first()
            val a = com.houseaffairs.app.core.data.haveAnswer("paneer", stock, emptyList(), today())
            println("SMOKE answer ${a.title} | ${a.lines}")
            check(a.title.contains("Paneer", ignoreCase = true) || a.lines.any { it.contains("aneer") }) { "no paneer answer" }
        }
        step("add a chore") {
            store.addChore("Water the plants", "Every 2 days")
            delay(800)
            check(store.tasks().first().any { it.chore && it.title == "Water the plants" }) { "no chore" }
        }
        step("delete one") {
            val k = store.kept().first().first()
            store.delete("keptAt", k.id)
            delay(800)
            check(store.kept().first().none { it.id == k.id }) { "still there" }
        }
        step("shared Home repository reads") {
            val home = com.houseaffairs.app.core.data.HomeRepository().observe(asha.household.id, asha.uid, asha.me.role, asha.me::canAccess, "INR").first { it.loaded }
            println("SMOKE home spent=${home.monthSpentMinor} tasks=${home.tasks.size} shopping=${home.shopping.size} events=${home.events.size}")
            check(home.monthSpentMinor > 0 && home.tasks.isNotEmpty()) { "empty home" }
        }
        step("shared search reads every field") {
            val found = com.houseaffairs.app.core.data.SearchRepository().load(asha.household.id, asha.uid, listOf("transactions", "tasks", "medicines"), false)
            println("SMOKE search found ${found.size}: " + found.joinToString { "${it.title}/${it.amountMinor}/${it.date}" })
            check(found.any { it.amountMinor == 500_00L }) { "no amount" }
            check(found.any { it.date != null }) { "no dates" }
        }
        step("shared forms save and read back") {
            val store = com.houseaffairs.app.feature.add.EntryStore()
            val type = com.houseaffairs.app.feature.add.EntryType.EXPENSE
            val values = type.defaults(asha.uid, "INR") + mapOf(
                "merchant" to "Form test", "amount" to com.houseaffairs.app.feature.add.MoneyValue("1250.5", "INR"),
                "paymentMethod" to "UPI", "category" to "Dining", "paidBy" to listOf(asha.uid),
            )
            check(com.houseaffairs.app.feature.add.missingFields(type, values).isEmpty()) { "missing " + com.houseaffairs.app.feature.add.missingFields(type, values).map { it.key } }
            store.save(asha.household.id, asha.uid, type, values, listOf("*"))
            delay(1500)
            val saved = com.houseaffairs.app.core.data.visible(asha.household.id, "transactions", asha.uid).get().documents.first { it.get<String?>("merchant") == "Form test" }
            val (back, _) = store.load(asha.household.id, type, saved.id)!!
            println("SMOKE form saved ${saved.id}: amount=${back["amount"]} date=${back["date"]} category=${back["category"]}")
            check((back["amount"] as com.houseaffairs.app.feature.add.MoneyValue).amount == "1250.5") { "amount ${back["amount"]}" }
            store.update(asha.household.id, asha.uid, type, saved.id, back + ("merchant" to "Form test edited"), listOf("*"))
            delay(1500)
            check(store.load(asha.household.id, type, saved.id)!!.first["merchant"] == "Form test edited") { "edit not saved" }
        }
        step("shared kept things") {
            com.houseaffairs.app.core.data.ThingsRepository().observeKept(asha.household.id, asha.uid).first()
        }
        step("make an invite") { code = Family.invite(asha.household.id, Role.ADULT) }
        step("sign in as Ravi") { signIn("smoke-ravi", "Ravi") }
        step("join with the code") { Family.join(code) }
        step("Ravi sees the family's things") {
            val ravi = ready()
            check(ravi.household.id == asha.household.id) { "a different home" }
            delay(1500)
            val n = FamilyStore(ravi).expenses().first().size
            check(n > 0) { "no expenses for Ravi" }
        }
        println("SMOKE done")
    }
}
