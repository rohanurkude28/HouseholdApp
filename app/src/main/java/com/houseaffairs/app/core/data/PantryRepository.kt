package com.houseaffairs.app.core.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.houseaffairs.app.core.model.Visibility
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class Recipe(
    val id: String,
    val title: String,
    val ingredients: List<Ingredient>,
    val servings: Int,
    val mood: List<String>,
    val link: String?,
    /** Breakfast, Lunch, Dinner, Snack or Dessert, when known. */
    val meal: String? = null,
    /** Per serving, as "protein · carbs · fat · fibre" in grams ("25g · 30g · 12g · 4g"); approximate for library dishes. */
    val macros: String? = null,
    val photos: List<String> = emptyList(),
)

/** What ticking a shopping item off did: the pantry item it filled, and how to undo it. */
class Bought(val stockId: String, val unit: String, val undo: () -> Unit) {
    operator fun invoke() = undo()
}

data class ShoppingEntry(val id: String, val title: String, val amount: Double?, val unit: String?, val aisleChoice: String? = null) {
    val aisle: Aisle get() = Aisle.from(aisleChoice) ?: aisleOf(title)
}

/** Result of cooking, for the confirmation message and Undo. */
data class CookResult(val message: String, val undo: suspend () -> Unit)

/**
 * The kitchen loop: shopping → pantry (when bought) → recipes (when cooked) → shopping again
 * (when something runs low or out).
 */
@Singleton
class PantryRepository @Inject constructor(
    private val db: FirebaseFirestore,
) {
    private fun collection(householdId: String, name: String) =
        db.collection("households").document(householdId).collection(name)

    private fun visible(householdId: String, name: String, uid: String): Query =
        collection(householdId, name).whereArrayContainsAny("visibleTo", listOf(Visibility.EVERYONE, uid))

    /** Food only: household supplies live in the same collection but never get cooked. */
    fun observePantry(householdId: String, uid: String): Flow<List<PantryStock>> =
        visible(householdId, "stock", uid).observeSnapshots()
            .map { snap -> snap.documents.filter { it.getString("status") !in GONE_STATUSES && it.getString("type") != "HOUSEHOLD" }.map(::toStock).sortedBy { it.expiry ?: java.time.LocalDate.MAX } }
            .catch { emit(emptyList()) }

    fun observeSupplies(householdId: String, uid: String): Flow<List<PantryStock>> =
        visible(householdId, "stock", uid).observeSnapshots()
            .map { snap -> snap.documents.filter { it.getString("status") !in GONE_STATUSES && it.getString("type") == "HOUSEHOLD" }.map(::toStock).sortedBy { it.title.lowercase() } }
            .catch { emit(emptyList()) }

    /** "Used one": a supply goes down by one unit; the last one moves it to the shopping list. */
    fun useOne(householdId: String, uid: String, stock: PantryStock, openShopping: List<String>) {
        val left = ((stock.amount ?: 1.0) - 1).coerceAtLeast(0.0)
        if (left <= 0.0) {
            usedUp(householdId, uid, stock, openShopping)
            return
        }
        collection(householdId, "stock").document(stock.id).update(
            mapOf(
                "quantity" to mapOf("amount" to left, "unit" to stock.unit),
                "level" to stock.initialAmount?.takeIf { it > 0 }?.let { (left / it * 100).toInt() },
            ),
        )
    }

    /** Medicines this person may see; [ownOnly] (teens) means only ones that are for them. */
    private fun medicines(householdId: String, uid: String, ownOnly: Boolean): Query =
        if (ownOnly) collection(householdId, "medicines").whereArrayContains("forWhom", uid) else visible(householdId, "medicines", uid)

    fun observeMedicines(householdId: String, uid: String, ownOnly: Boolean = false): Flow<List<PantryStock>> =
        medicines(householdId, uid, ownOnly).observeSnapshots()
            .map { snap -> snap.documents.filter { it.getString("status") !in GONE_STATUSES }.map(::toStock).sortedBy { it.expiry ?: java.time.LocalDate.MAX } }
            .catch { emit(emptyList()) }

    /** Medicines with what they're for and who they're for, for "I'm feeling…". */
    fun observeMedicineInfo(householdId: String, uid: String, ownOnly: Boolean = false): Flow<List<MedicineInfo>> =
        medicines(householdId, uid, ownOnly).observeSnapshots()
            .map { snap ->
                snap.documents.filter { it.getString("status") !in GONE_STATUSES }.map { d ->
                    MedicineInfo(
                        id = d.id,
                        title = d.getString("title").orEmpty(),
                        usedFor = (d.get("usedFor") as? List<*>).orEmpty().filterIsInstance<String>(),
                        forWhom = (d.get("forWhom") as? List<*>).orEmpty().filterIsInstance<String>(),
                        dosage = d.getString("dosage"),
                        expiry = d.localDate("expiry"),
                    )
                }
            }
            .catch { emit(emptyList()) }

    /** Each dish's most recent cooking date, from the cooking log. */
    fun observeLastCooked(householdId: String, uid: String): Flow<Map<String, java.time.LocalDate>> =
        visible(householdId, "cookLog", uid).observeSnapshots()
            .map { snap ->
                snap.documents.mapNotNull { d ->
                    val title = d.getString("title") ?: return@mapNotNull null
                    val date = d.getTimestamp("createdAt", com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        ?.toDate()?.toInstant()?.atZone(java.time.ZoneId.systemDefault())?.toLocalDate() ?: return@mapNotNull null
                    title to date
                }.groupBy({ it.first.lowercase() }, { it.second }).mapValues { (_, dates) -> dates.max() }
            }
            .catch { emit(emptyMap()) }

    fun observeRecipes(householdId: String, uid: String): Flow<List<Recipe>> =
        visible(householdId, "recipes", uid).observeSnapshots()
            .map { snap -> snap.documents.map(::toRecipe).sortedBy { it.title } }
            .catch { emit(emptyList()) }

    fun observeShopping(householdId: String, uid: String): Flow<List<ShoppingEntry>> =
        visible(householdId, "shopping", uid).observeSnapshots()
            .map { snap -> snap.documents.filter { it.getBoolean("done") != true }.map(::toShopping) }
            .catch { emit(emptyList()) }

    /** Applies [plan]: lowers pantry quantities, lists what ran out, and logs the meal. */
    suspend fun cook(
        householdId: String,
        uid: String,
        title: String,
        plan: CookPlan,
        openShopping: List<String>,
        eaters: List<String> = emptyList(),
        people: Int = 0,
    ): CookResult {
        val batch = db.batch()
        val restore = mutableListOf<Pair<String, Map<String, Any?>>>()
        plan.deductions.filter { it.after != null }.forEach { d ->
            val ref = collection(householdId, "stock").document(d.stock.id)
            batch.update(
                ref,
                mapOf(
                    "quantity" to mapOf("amount" to d.after, "unit" to d.stock.unit),
                    "level" to d.level,
                    "status" to if (d.usedUp) "USED_UP" else "IN_STOCK",
                    "usedUpAt" to if (d.usedUp) com.google.firebase.firestore.FieldValue.serverTimestamp() else com.google.firebase.firestore.FieldValue.delete(),
                ),
            )
            restore += d.stock.id to mapOf(
                "quantity" to mapOf("amount" to d.stock.amount, "unit" to d.stock.unit),
                "level" to d.stock.initialAmount?.takeIf { it > 0 }?.let { ((d.stock.amount ?: 0.0) / it * 100).toInt() },
                "status" to "IN_STOCK",
            )
        }
        val added = plan.toBuy.distinctBy { it.lowercase() }
            .filter { item -> openShopping.none { sameIngredient(it, item) } }
            .map { item ->
                collection(householdId, "shopping").document().also { ref ->
                    batch.set(ref, common(uid, "cooking") + mapOf("title" to item, "done" to false, "reason" to "Ran low after cooking $title"))
                }
            }
        val log = collection(householdId, "cookLog").document()
        batch.set(log, common(uid, "cooking") + mapOf("title" to title, "type" to "COOKED", "eaters" to eaters, "people" to people))
        batch.commit()

        val parts = buildList {
            plan.deductions.filter { it.after != null }.take(3).forEach { d -> add("${d.stock.title} −${short(d.need)} ${d.stock.unit}") }
            if (added.isNotEmpty()) add("${added.size} added to shopping")
        }
        return CookResult("Cooked $title for $people · ${parts.joinToString(" · ").ifEmpty { "pantry updated" }}") {
            val undo = db.batch()
            restore.forEach { (id, fields) -> undo.update(collection(householdId, "stock").document(id), fields) }
            added.forEach { undo.delete(it) }
            undo.delete(log)
            undo.commit()
        }
    }

    /** One-shot read used by voice ("made paneer sabzi"). */
    suspend fun snapshot(householdId: String, uid: String): Triple<List<Recipe>, List<PantryStock>, List<ShoppingEntry>> {
        val recipes = visible(householdId, "recipes", uid).get().await().documents.map(::toRecipe)
        val pantry = visible(householdId, "stock", uid).get().await().documents.filter { it.getString("status") !in GONE_STATUSES && it.getString("type") != "HOUSEHOLD" }.map(::toStock)
        val shopping = visible(householdId, "shopping", uid).get().await().documents.filter { it.getBoolean("done") != true }.map(::toShopping)
        return Triple(recipes, pantry, shopping)
    }

    /**
     * Ticking an item off the shopping list puts it in the pantry. If the pantry already has it
     * (say 50 g of paneer left), it's topped up by the usual pack size instead of duplicated.
     */
    /** Ticks a shopping item off into the pantry; the returned undo puts both back as they were. */
    suspend fun markBought(householdId: String, uid: String, shoppingId: String): Bought {
        val ref = collection(householdId, "shopping").document(shoppingId)
        val item = toShopping(ref.get().await())
        val existing = visible(householdId, "stock", uid).get().await().documents
            .filter { it.getString("status") !in GONE_STATUSES || it.getString("type") == "HOUSEHOLD" }
            .map(::toStock)
            .firstOrNull { sameIngredient(it.title, item.title) && it.amount != null }
        val batch = db.batch()
        batch.update(ref, mapOf("done" to true, "boughtAt" to FieldValue.serverTimestamp()))
        var restore: (com.google.firebase.firestore.WriteBatch) -> Unit = {}
        var stockId = existing?.id.orEmpty()
        var unit = existing?.unit ?: item.unit ?: naturalUnit(item.title)
        if (existing != null) {
            val before = mapOf(
                "quantity" to mapOf("amount" to existing.amount, "unit" to existing.unit),
                "initialAmount" to existing.initialAmount,
                "expiry" to existing.expiry?.let { com.google.firebase.Timestamp(java.util.Date.from(it.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant())) },
            )
            restore = { it.update(collection(householdId, "stock").document(existing.id), before) }
            val added = item.amount?.let { convertFor(item.title, it, item.unit ?: existing.unit, existing.unit) } ?: existing.initialAmount ?: 1.0
            val total = (existing.amount ?: 0.0) + added
            batch.update(
                collection(householdId, "stock").document(existing.id),
                mapOf(
                    "quantity" to mapOf("amount" to total, "unit" to existing.unit),
                    "initialAmount" to total,
                    "level" to 100,
                    "status" to "IN_STOCK",
                    "expiry" to null,
                ),
            )
        } else {
            val added = collection(householdId, "stock").document()
            stockId = added.id
            restore = { it.delete(added) }
            batch.set(
                added,
                common(uid, "shopping") + mapOf(
                    "type" to "GROCERY",
                    "title" to item.title,
                    // No amount said: leave it unknown (the follow-up asks) rather than "1 pc" of paneer.
                    "quantity" to mapOf("amount" to item.amount, "unit" to unit),
                    "initialAmount" to item.amount,
                    "level" to 100,
                    "status" to "IN_STOCK",
                ),
            )
        }
        batch.commit()
        return Bought(stockId, unit) {
            val undo = db.batch()
            undo.update(ref, mapOf("done" to false, "boughtAt" to FieldValue.delete()))
            restore(undo)
            undo.commit()
        }
    }

    /**
     * Purchase history for restock predictions: ticked-off shopping items plus groceries added as
     * bought, and what each pantry entry remembers (brand, rating, restock choice).
     */
    fun observeHistory(householdId: String, uid: String): Flow<Pair<List<Purchase>, List<StockMemory>>> =
        combine(
            visible(householdId, "shopping", uid).observeSnapshots().map { snap ->
                snap.documents.filter { it.getBoolean("done") == true }.mapNotNull { d ->
                    d.localDate("boughtAt")?.let { Purchase(d.getString("title") ?: d.getString("name").orEmpty(), it) }
                }
            },
            visible(householdId, "stock", uid).observeSnapshots().map { snap -> snap.documents.filter { it.getString("type") != "HOUSEHOLD" } },
        ) { bought, stock ->
            val added = stock.filter { it.getString("source") != "shopping" }.mapNotNull { d -> d.localDate("createdAt")?.let { Purchase(d.getString("title").orEmpty(), it) } }
            val memory = stock.map { d ->
                StockMemory(d.getString("title").orEmpty(), d.getString("brand")?.takeIf { it.isNotBlank() }, d.getString("rating"), d.getString("restock"), d.localDate("createdAt"))
            }
            (bought + added) to memory
        }.catch { emit(emptyList<Purchase>() to emptyList()) }

    /**
     * For the month and year views: what came home, what was finished, and what went to waste
     * (thrown away, or still there past its date), with what it cost. Food also counts what was
     * ticked off the shopping list; medicines follow the same teen rule as the cabinet.
     */
    fun observeHistory(householdId: String, uid: String, kind: StockKind, ownOnly: Boolean = false): Flow<StockHistory> {
        val docs = when (kind) {
            StockKind.MEDICINES -> medicines(householdId, uid, ownOnly).observeSnapshots().map { it.documents }
            else -> visible(householdId, "stock", uid).observeSnapshots().map { snap ->
                snap.documents.filter { (it.getString("type") == "HOUSEHOLD") == (kind == StockKind.SUPPLIES) }
            }
        }
        val ticked = if (kind == StockKind.FOOD) {
            visible(householdId, "shopping", uid).observeSnapshots().map { snap ->
                snap.documents.filter { it.getBoolean("done") == true }.mapNotNull { d ->
                    d.localDate("boughtAt")?.let { KitchenBuy(d.getString("title") ?: d.getString("name").orEmpty(), it, d.getString("aisle")) }
                }
            }
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList())
        }
        return combine(ticked, docs) { ticked, stock ->
            val added = stock.filter { it.getString("source") != "shopping" }.mapNotNull { d ->
                val type = d.getString("groceryType") ?: d.getString("supplyType") ?: if (kind == StockKind.MEDICINES) "Medicines" else null
                (d.localDate("boughtOn") ?: d.localDate("createdAt"))?.let { KitchenBuy(d.getString("title").orEmpty(), it, type) }
            }
            val lives = stock.mapNotNull { d ->
                val start = d.localDate("boughtOn") ?: d.localDate("createdAt") ?: return@mapNotNull null
                d.localDate("usedUpAt")?.takeIf { d.getString("status") == "USED_UP" }?.let { StockLife(d.getString("title").orEmpty(), start, it) }
            }
            val wasted = stock.flatMap { d ->
                val cost = d.getLong("costMinor")
                val level = d.getLong("level")?.toInt()
                val title = d.getString("title").orEmpty()
                // Parts thrown away earlier ("half the milk"), each on its own day, valued at its share.
                val parts = (d.get("wasteLog") as? List<*>).orEmpty().mapNotNull { raw ->
                    val m = raw as? Map<*, *> ?: return@mapNotNull null
                    val share = (m["share"] as? Number)?.toInt() ?: return@mapNotNull null
                    val at = (m["at"] as? com.google.firebase.Timestamp)?.toDate()?.toInstant()?.atZone(java.time.ZoneId.systemDefault())?.toLocalDate() ?: return@mapNotNull null
                    Expired(partLabel(title, share), at, cost?.let { it * share / 100 }, 100, thrown = true)
                }
                parts + listOfNotNull(
                    when (d.getString("status")) {
                        "WASTED" -> d.localDate("wastedAt")?.let { Expired(title, it, cost, level, thrown = true) }
                        "USED_UP" -> null
                        else -> d.localDate("expiry")?.let { Expired(title, it, cost, level) }
                    },
                )
            }
            StockHistory(ticked + added, lives, wasted, stock.filter { it.getString("status") !in GONE_STATUSES }.map(::toStock))
        }.catch { emit(StockHistory(emptyList(), emptyList(), emptyList(), emptyList())) }
    }

    /** Every pack of pantry food and supplies, bought and finished, for how long things last. */
    fun observeLives(householdId: String, uid: String): Flow<List<StockLife>> =
        visible(householdId, "stock", uid).observeSnapshots().map { snap ->
            snap.documents.mapNotNull { d ->
                val start = d.localDate("boughtOn") ?: d.localDate("createdAt") ?: return@mapNotNull null
                val used = d.getString("status") == "USED_UP"
                // Finished before the date was recorded: nothing to count.
                val end = if (used) d.localDate("usedUpAt") ?: return@mapNotNull null else null
                StockLife(d.getString("title").orEmpty(), start, end)
            }
        }.catch { emit(emptyList()) }

    /** Puts [title] on the shopping list; the returned undo takes it off again. */
    fun addToShopping(householdId: String, uid: String, title: String): () -> Unit {
        val ref = collection(householdId, "shopping").document()
        ref.set(common(uid, "restock") + mapOf("title" to title, "done" to false))
        return { ref.delete() }
    }

    /**
     * "It's finished" (or [thrown] away): out of the pantry (or the medicine cabinet), onto the shopping list.
     * Returns the shopping item it added, if any, so "don't buy again" can take it off.
     */
    /** "Put it back": undoes Bin it or Used up, so it's in stock again (and off the buy list if it went on). */
    fun putBack(householdId: String, stock: PantryStock, from: String = "stock", shoppingId: String? = null) {
        val del = com.google.firebase.firestore.FieldValue.delete()
        collection(householdId, from).document(stock.id).update(mapOf("status" to "IN_STOCK", "wastedAt" to del, "usedUpAt" to del, "level" to (stock.initialAmount?.takeIf { it > 0 }?.let { ((stock.amount ?: it) / it * 100).toInt().coerceIn(0, 100) } ?: 100)))
        shoppingId?.let { collection(householdId, "shopping").document(it).delete() }
    }

    fun usedUp(householdId: String, uid: String, stock: PantryStock, openShopping: List<String>, from: String = "stock", thrown: Boolean = false, addToList: Boolean = true): String? {
        val batch = db.batch()
        batch.update(
            collection(householdId, from).document(stock.id),
            // Thrown away keeps its level, so the waste is valued by what was left;
            // finished records when it ran out, so "how long did it last?" has an answer.
            if (thrown) mapOf("status" to "WASTED", "wastedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp())
            else mapOf("status" to "USED_UP", "level" to 0, "usedUpAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()),
        )
        val added = if (addToList && openShopping.none { sameIngredient(it, stock.title) }) collection(householdId, "shopping").document() else null
        added?.let { batch.set(it, common(uid, "pantry") + mapOf("title" to stock.title, "done" to false)) }
        batch.commit()
        return added?.id
    }

    /**
     * "Threw some away": [share] (% of the pack) goes to waste and comes off what's left. When
     * nothing would be left, it's the whole thing thrown away. Returns true if all of it went.
     */
    suspend fun wastePart(householdId: String, uid: String, stock: PantryStock, openShopping: List<String>, share: Int, from: String = "stock"): Boolean {
        val ref = collection(householdId, from).document(stock.id)
        val level = ref.get().await().getLong("level")?.toInt()
        val left = levelAfterWaste(level, share)
        if (left <= 0) {
            usedUp(householdId, uid, stock, openShopping, from, thrown = true)
            return true
        }
        ref.update(
            mapOf(
                "level" to left,
                "quantity" to stock.amount?.let { a -> stock.initialAmount?.takeIf { it > 0 }?.let { mapOf("amount" to it * left / 100.0, "unit" to stock.unit) } ?: mapOf("amount" to a, "unit" to stock.unit) },
                "wasteLog" to FieldValue.arrayUnion(mapOf("share" to share, "at" to com.google.firebase.Timestamp.now())),
            ),
        ).await()
        return false
    }

    /** "Won't take it today": no dose reminders that day, and what's left isn't counted down for it. */
    fun skipDose(householdId: String, medicineId: String, day: LocalDate) {
        collection(householdId, "medicines").document(medicineId).update("skipped", FieldValue.arrayUnion(day.toEpochDay()))
    }

    /** "Don't buy again" after finishing something: takes it back off the shopping list. */
    fun removeFromShopping(householdId: String, shoppingId: String) {
        collection(householdId, "shopping").document(shoppingId).delete()
    }

    /** The follow-up to "bought": how much came home and when it's best before. */
    fun refineBought(householdId: String, stockId: String, amount: Double?, unit: String, expiry: LocalDate?) {
        val changes = buildMap<String, Any?> {
            amount?.let { put("quantity", mapOf("amount" to it, "unit" to unit)); put("initialAmount", it) }
            expiry?.let { put("expiry", com.google.firebase.Timestamp(java.util.Date.from(it.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()))) }
        }
        if (changes.isNotEmpty()) collection(householdId, "stock").document(stockId).update(changes)
    }

    private fun common(uid: String, source: String) = mapOf(
        "createdBy" to uid,
        "visibleTo" to Visibility.everyone,
        "createdAt" to FieldValue.serverTimestamp(),
        "source" to source,
    )

    private fun short(value: Double?): String = when {
        value == null -> "some"
        value % 1.0 == 0.0 -> value.toLong().toString()
        else -> "%.1f".format(value)
    }

    private fun quantity(doc: DocumentSnapshot): Pair<Double?, String?> {
        val q = doc.get("quantity") as? Map<*, *>
        return (q?.get("amount") as? Number)?.toDouble() to (q?.get("unit") as? String)
    }

    private fun toStock(doc: DocumentSnapshot): PantryStock {
        val (amount, unit) = quantity(doc)
        return PantryStock(
            id = doc.id,
            title = doc.getString("title").orEmpty(),
            amount = amount,
            unit = unit ?: "pcs",
            initialAmount = (doc.get("initialAmount") as? Number)?.toDouble() ?: amount,
            expiry = doc.localDate("expiry"),
            category = doc.getString("groceryType") ?: doc.getString("supplyType")
                ?: (doc.get("usedFor") as? List<*>)?.filterIsInstance<String>()?.firstOrNull(),
            keptAt = doc.getString("keptAt")?.takeIf { it.isNotBlank() },
            photos = (doc.get("photos") as? List<*>).orEmpty().filterIsInstance<String>(),
            restock = doc.getString("restock"),
            schedule = courseOf(doc)?.let { scheduleLine(it, LocalDate.now()) },
        )
    }

    private fun toShopping(doc: DocumentSnapshot): ShoppingEntry {
        val (amount, unit) = quantity(doc)
        return ShoppingEntry(doc.id, doc.getString("title") ?: doc.getString("name").orEmpty(), amount, unit, doc.getString("aisle"))
    }

    private fun toRecipe(doc: DocumentSnapshot) = Recipe(
        id = doc.id,
        title = doc.getString("title").orEmpty(),
        ingredients = (doc.get("ingredients") as? List<*>).orEmpty().mapNotNull { raw ->
            val m = raw as? Map<*, *> ?: return@mapNotNull null
            Ingredient((m["name"] as? String).orEmpty(), (m["amount"] as? Number)?.toDouble(), (m["unit"] as? String) ?: "pcs")
        },
        servings = (doc.getString("servings") ?: "2").toIntOrNull() ?: 2,
        mood = (doc.get("mood") as? List<*>).orEmpty().filterIsInstance<String>(),
        link = doc.getString("link"),
        meal = doc.getString("mealType"),
        macros = doc.getString("macros")?.takeIf { it.isNotBlank() },
        photos = (doc.get("photos") as? List<*>).orEmpty().filterIsInstance<String>(),
    )
}
