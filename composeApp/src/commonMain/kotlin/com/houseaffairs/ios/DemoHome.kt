package com.houseaffairs.ios

import com.houseaffairs.app.core.model.Role
import com.houseaffairs.app.core.model.RolePolicy
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.FieldValue
import dev.gitlive.firebase.firestore.firestore
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

/**
 * "Try the demo" on iOS: signs in anonymously, creates "Demo home" and fills it with a slice
 * of family life. Documents have the same shape Android's EntryRepository writes, so either app
 * reads them. Android's DemoSeeder fills more (budget, lists, places); this covers what iOS shows.
 */
object DemoHome {
    private val db get() = Firebase.firestore

    suspend fun start() {
        val user = Firebase.auth.currentUser ?: Firebase.auth.signInAnonymously().user ?: error("Demo sign-in failed")
        val uid = user.uid
        val home = db.collection("households").document
        val batch = db.batch()
        batch.set(home, mapOf("name" to "Demo home", "baseCurrency" to "INR", "budgetCycleStartDay" to 1, "createdBy" to uid, "createdAt" to FieldValue.serverTimestamp))
        batch.set(
            home.collection("members").document(uid),
            mapOf(
                "displayName" to "Demo", "photoUrl" to null, "role" to Role.OWNER.name,
                "moduleAccess" to RolePolicy.defaultAccess(Role.OWNER), "restrictedCategories" to emptyList<String>(),
                "inviteCode" to null, "joinedAt" to FieldValue.serverTimestamp,
            ),
        )
        batch.set(db.collection("users").document(uid), mapOf("householdId" to home.id), merge = true)
        batch.commit()
        seed(home.id, uid)
    }

    private suspend fun seed(hid: String, uid: String) {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        fun day(offset: Int) = startOfDay(today.plus(DatePeriod(days = offset)))
        val batch = db.batch()
        fun save(collection: String, type: String, fields: Map<String, Any?>) = batch.set(
            db.collection("households").document(hid).collection(collection).document,
            mapOf("type" to type, "createdBy" to uid, "visibleTo" to listOf("*"), "createdAt" to FieldValue.serverTimestamp, "source" to "demo") + fields,
        )

        listOf(Triple("Rent", "Landlord", 30_000), Triple("Groceries", "DMart", 2_340), Triple("Dining", "Toit", 1_850), Triple("Bills", "Electricity", 1_960))
            .forEachIndexed { i, (category, merchant, rupees) ->
                save("transactions", "EXPENSE", mapOf("amountMinor" to rupees * 100L, "currency" to "INR", "category" to category, "merchant" to merchant, "status" to "Paid", "date" to day(-i)))
            }
        save("recurring", "SUBSCRIPTION", mapOf("title" to "Netflix", "amountMinor" to 64_900L, "currency" to "INR", "frequency" to "Monthly", "nextDate" to day(4), "category" to "Entertainment"))
        save("recurring", "SUBSCRIPTION", mapOf("title" to "Hotstar", "amountMinor" to 29_900L, "currency" to "INR", "frequency" to "Monthly", "nextDate" to day(5), "category" to "Entertainment", "using" to "Not using"))
        listOf("Paneer" to 1, "Milk" to 2, "Curd" to 2, "Tomatoes" to 4, "Onions" to null, "Rice" to null).forEach { (title, expires) ->
            save("stock", "GROCERY", mapOf("title" to title, "expiry" to expires?.let { day(it) }, "status" to "IN_STOCK", "level" to 100))
        }
        save("medicines", "MEDICINE", mapOf("title" to "Cough syrup", "usedFor" to listOf("Cold & cough"), "expiry" to day(20), "status" to "IN_STOCK", "level" to 100))
        listOf("Eggs", "Bread", "Coriander", "Ghee").forEach { save("shopping", "SHOPPING", mapOf("title" to it, "done" to false)) }
        save("tasks", "TASK", mapOf("title" to "Pay school fees", "due" to day(0), "assignee" to uid, "done" to false, "assignedBy" to uid))
        save("tasks", "TASK", mapOf("title" to "Water the plants", "due" to day(1), "repeat" to "Weekly", "assignee" to uid, "done" to false, "assignedBy" to uid))
        save("tasks", "TASK", mapOf("title" to "Book car service", "due" to day(3), "done" to false, "assignedBy" to uid))
        save("events", "EVENT", mapOf("title" to "Riya's birthday", "kind" to "Birthday", "date" to day(5), "repeat" to "Yearly"))
        save("events", "EVENT", mapOf("title" to "Car insurance renewal", "kind" to "Renewal", "date" to day(20), "repeat" to "Yearly"))
        batch.commit()
    }

    suspend fun signOut() = Firebase.auth.signOut()
}
