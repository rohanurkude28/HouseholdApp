package com.houseaffairs.app.core.data

import com.houseaffairs.app.core.model.Visibility
import com.houseaffairs.app.core.time.FAR_FUTURE
import com.houseaffairs.app.core.time.LocalDate
import com.houseaffairs.app.core.time.systemToday
import com.houseaffairs.app.core.time.toEpochDay
import com.houseaffairs.app.core.util.oneDecimal
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FieldValue
import dev.gitlive.firebase.firestore.Query
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.where
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** What ticking a shopping item off did: the pantry item it filled, and how to undo it. */
class Bought(val stockId: String, val unit: String, val undo: () -> Unit) {
    operator fun invoke() = undo()
}

/** Result of cooking, for the confirmation message and Undo. */
data class CookResult(val message: String, val undo: suspend () -> Unit)

/**
 * The kitchen loop: shopping → pantry (when bought) → recipes (when cooked) → shopping again
 * (when something runs low or out).
 */
class PantryRepository {

    /** Food only: household supplies live in the same collection but never get cooked. */
    fun observePantry(householdId: String, uid: String): Flow<List<PantryStock>> =
        visible(householdId, "stock", uid).docs()
            .map { snap -> snap.filter { it.str("status") !in GONE_STATUSES && it.str("type") != "HOUSEHOLD" }.map(::toStock).sortedBy { it.expiry ?: FAR_FUTURE } }
            .catch { emit(emptyList()) }

    fun observeSupplies(householdId: String, uid: String): Flow<List<PantryStock>> =
        visible(householdId, "stock", uid).docs()
            .map { snap -> snap.filter { it.str("status") !in GONE_STATUSES && it.str("type") == "HOUSEHOLD" }.map(::toStock).sortedBy { it.title.lowercase() } }
            .catch { emit(emptyList()) }

    /** "Used one": a supply goes down by one unit; the last one moves it to the shopping list. */
    fun useOne(householdId: String, uid: String, stock: PantryStock, openShopping: List<String>) {
        val left = ((stock.amount ?: 1.0) - 1).coerceAtLeast(0.0)
        if (left <= 0.0) {
            usedUp(householdId, uid, stock, openShopping)
            return
        }
        Writes.later {
            collection(householdId, "stock").document(stock.id).updateFields(
                mapOf(
                    "quantity" to mapOf("amount" to left, "unit" to stock.unit),
                    "level" to stock.initialAmount?.takeIf { it > 0 }?.let { (left / it * 100).toInt() },
                ),
            )
        }
    }

    /** Medicines this person may see; [ownOnly] (teens) means only ones that are for them. */
    private fun medicines(householdId: String, uid: String, ownOnly: Boolean): Query =
        if (ownOnly) collection(householdId, "medicines").where { "forWhom" contains uid } else visible(householdId, "medicines", uid)

    fun observeMedicines(householdId: String, uid: String, ownOnly: Boolean = false): Flow<List<PantryStock>> =
        medicines(householdId, uid, ownOnly).docs()
            .map { snap -> snap.filter { it.str("status") !in GONE_STATUSES }.map(::toStock).sortedBy { it.expiry ?: FAR_FUTURE } }
            .catch { emit(emptyList()) }

    /** Medicines with what they're for and who they're for, for "I'm feeling…". */
    fun observeMedicineInfo(householdId: String, uid: String, ownOnly: Boolean = false): Flow<List<MedicineInfo>> =
        medicines(householdId, uid, ownOnly).docs()
            .map { snap ->
                snap.filter { it.str("status") !in GONE_STATUSES }.map { d ->
                    MedicineInfo(
                        id = d.id,
                        title = d.str("title").orEmpty(),
                        usedFor = d.strings("usedFor"),
                        forWhom = d.strings("forWhom"),
                        dosage = d.str("dosage"),
                        expiry = d.date("expiry"),
                    )
                }
            }
            .catch { emit(emptyList()) }

    /** Each dish's most recent cooking date, from the cooking log. */
    fun observeLastCooked(householdId: String, uid: String): Flow<Map<String, LocalDate>> =
        visible(householdId, "cookLog", uid).docs()
            .map { snap ->
                snap.mapNotNull { d ->
                    val title = d.str("title") ?: return@mapNotNull null
                    val date = d.dayAndMinute("createdAt")?.first ?: return@mapNotNull null
                    title to date
                }.groupBy({ it.first.lowercase() }, { it.second }).mapValues { (_, dates) -> dates.max() }
            }
            .catch { emit(emptyMap()) }

    fun observeRecipes(householdId: String, uid: String): Flow<List<Recipe>> =
        visible(householdId, "recipes", uid).docs()
            .map { snap -> snap.map(::toRecipe).sortedBy { it.title } }
            .catch { emit(emptyList()) }

    fun observeShopping(householdId: String, uid: String): Flow<List<ShoppingEntry>> =
        visible(householdId, "shopping", uid).docs()
            .map { snap -> snap.filter { it.bool("done") != true }.map(::toShopping) }
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
        val batch = store.batch()
        val restore = mutableListOf<Pair<String, Map<String, Any?>>>()
        plan.deductions.filter { it.after != null }.forEach { d ->
            val ref = collection(householdId, "stock").document(d.stock.id)
            batch.updateFields(
                ref,
                mapOf(
                    "quantity" to mapOf("amount" to d.after, "unit" to d.stock.unit),
                    "level" to d.level,
                    "status" to if (d.usedUp) "USED_UP" else "IN_STOCK",
                    "usedUpAt" to if (d.usedUp) FieldValue.serverTimestamp else FieldValue.delete,
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
                collection(householdId, "shopping").document.also { ref ->
                    batch.set(ref, common(uid, "cooking") + mapOf("title" to item, "done" to false, "reason" to "Ran low after cooking $title"))
                }
            }
        val log = collection(householdId, "cookLog").document
        batch.set(log, common(uid, "cooking") + mapOf("title" to title, "type" to "COOKED", "eaters" to eaters, "people" to people))
        Writes.later { batch.commit() }

        val parts = buildList {
            plan.deductions.filter { it.after != null }.take(3).forEach { d -> add("${d.stock.title} −${short(d.need)} ${d.stock.unit}") }
            if (added.isNotEmpty()) add("${added.size} added to shopping")
        }
        return CookResult("Cooked $title for $people · ${parts.joinToString(" · ").ifEmpty { "pantry updated" }}") {
            val undo = store.batch()
            restore.forEach { (id, fields) -> undo.updateFields(collection(householdId, "stock").document(id), fields) }
            added.forEach { undo.delete(it) }
            undo.delete(log)
            Writes.later { undo.commit() }
        }
    }

    /** One-shot read used by voice ("made paneer sabzi"). */
    suspend fun snapshot(householdId: String, uid: String): Triple<List<Recipe>, List<PantryStock>, List<ShoppingEntry>> {
        val recipes = visible(householdId, "recipes", uid).get().documents.map(::toRecipe)
        val pantry = visible(householdId, "stock", uid).get().documents.filter { it.str("status") !in GONE_STATUSES && it.str("type") != "HOUSEHOLD" }.map(::toStock)
        val shopping = visible(householdId, "shopping", uid).get().documents.filter { it.bool("done") != true }.map(::toShopping)
        return Triple(recipes, pantry, shopping)
    }

    /**
     * Ticking an item off the shopping list puts it in the pantry. If the pantry already has it
     * (say 50 g of paneer left), it's topped up by the usual pack size instead of duplicated.
     */
    /** Ticks a shopping item off into the pantry; the returned undo puts both back as they were. */
    suspend fun markBought(householdId: String, uid: String, shoppingId: String): Bought {
        val ref = collection(householdId, "shopping").document(shoppingId)
        val item = toShopping(ref.get())
        val existing = visible(householdId, "stock", uid).get().documents
            .filter { it.str("status") !in GONE_STATUSES || it.str("type") == "HOUSEHOLD" }
            .map(::toStock)
            .firstOrNull { sameIngredient(it.title, item.title) && it.amount != null }
        val batch = store.batch()
        batch.updateFields(ref, mapOf("done" to true, "boughtAt" to FieldValue.serverTimestamp))
        var restore: (dev.gitlive.firebase.firestore.WriteBatch) -> Unit = {}
        var stockId = existing?.id.orEmpty()
        var unit = existing?.unit ?: item.unit ?: naturalUnit(item.title)
        if (existing != null) {
            val before = mapOf(
                "quantity" to mapOf("amount" to existing.amount, "unit" to existing.unit),
                "initialAmount" to existing.initialAmount,
                "expiry" to existing.expiry?.stamp(),
            )
            restore = { it.updateFields(collection(householdId, "stock").document(existing.id), before) }
            val added = item.amount?.let { convertFor(item.title, it, item.unit ?: existing.unit, existing.unit) } ?: existing.initialAmount ?: 1.0
            val total = (existing.amount ?: 0.0) + added
            batch.updateFields(
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
            val added = collection(householdId, "stock").document
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
        Writes.later { batch.commit() }
        return Bought(stockId, unit) {
            val undo = store.batch()
            undo.updateFields(ref, mapOf("done" to false, "boughtAt" to FieldValue.delete))
            restore(undo)
            Writes.later { undo.commit() }
        }
    }

    /**
     * Purchase history for restock predictions: ticked-off shopping items plus groceries added as
     * bought, and what each pantry entry remembers (brand, rating, restock choice).
     */
    fun observeHistory(householdId: String, uid: String): Flow<Pair<List<Purchase>, List<StockMemory>>> =
        combine(
            visible(householdId, "shopping", uid).docs().map { snap ->
                snap.filter { it.bool("done") == true }.mapNotNull { d ->
                    d.date("boughtAt")?.let { Purchase(d.str("title") ?: d.str("name").orEmpty(), it) }
                }
            },
            visible(householdId, "stock", uid).docs().map { snap -> snap.filter { it.str("type") != "HOUSEHOLD" } },
        ) { bought, stock ->
            val added = stock.filter { it.str("source") != "shopping" }.mapNotNull { d -> d.date("createdAt")?.let { Purchase(d.str("title").orEmpty(), it) } }
            val memory = stock.map { d ->
                StockMemory(d.str("title").orEmpty(), d.str("brand")?.takeIf { it.isNotBlank() }, d.str("rating"), d.str("restock"), d.date("createdAt"))
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
            StockKind.MEDICINES -> medicines(householdId, uid, ownOnly).docs()
            else -> visible(householdId, "stock", uid).docs().map { snap ->
                snap.filter { (it.str("type") == "HOUSEHOLD") == (kind == StockKind.SUPPLIES) }
            }
        }
        val ticked = if (kind == StockKind.FOOD) {
            visible(householdId, "shopping", uid).docs().map { snap ->
                snap.filter { it.bool("done") == true }.mapNotNull { d ->
                    d.date("boughtAt")?.let { KitchenBuy(d.str("title") ?: d.str("name").orEmpty(), it, d.str("aisle")) }
                }
            }
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList())
        }
        return combine(ticked, docs) { ticked, stock ->
            val added = stock.filter { it.str("source") != "shopping" }.mapNotNull { d ->
                val type = d.str("groceryType") ?: d.str("supplyType") ?: if (kind == StockKind.MEDICINES) "Medicines" else null
                (d.date("boughtOn") ?: d.date("createdAt"))?.let { KitchenBuy(d.str("title").orEmpty(), it, type) }
            }
            val lives = stock.mapNotNull { d ->
                val start = d.date("boughtOn") ?: d.date("createdAt") ?: return@mapNotNull null
                d.date("usedUpAt")?.takeIf { d.str("status") == "USED_UP" }?.let { StockLife(d.str("title").orEmpty(), start, it) }
            }
            val wasted = stock.flatMap { d ->
                val cost = d.long("costMinor")
                val level = d.long("level")?.toInt()
                val title = d.str("title").orEmpty()
                // Parts thrown away earlier ("half the milk"), each on its own day, valued at its share.
                val parts = (d.plainData()["wasteLog"] as? List<*>).orEmpty().mapNotNull { raw ->
                    val m = raw as? Map<*, *> ?: return@mapNotNull null
                    val share = (m["share"] as? Number)?.toInt() ?: return@mapNotNull null
                    val at = m["at"] as? LocalDate ?: return@mapNotNull null
                    Expired(partLabel(title, share), at, cost?.let { it * share / 100 }, 100, thrown = true)
                }
                parts + listOfNotNull(
                    when (d.str("status")) {
                        "WASTED" -> d.date("wastedAt")?.let { Expired(title, it, cost, level, thrown = true) }
                        "USED_UP" -> null
                        else -> d.date("expiry")?.let { Expired(title, it, cost, level) }
                    },
                )
            }
            StockHistory(ticked + added, lives, wasted, stock.filter { it.str("status") !in GONE_STATUSES }.map(::toStock))
        }.catch { emit(StockHistory(emptyList(), emptyList(), emptyList(), emptyList())) }
    }

    /** Every pack of pantry food and supplies, bought and finished, for how long things last. */
    fun observeLives(householdId: String, uid: String): Flow<List<StockLife>> =
        visible(householdId, "stock", uid).docs().map { snap ->
            snap.mapNotNull { d ->
                val start = d.date("boughtOn") ?: d.date("createdAt") ?: return@mapNotNull null
                val used = d.str("status") == "USED_UP"
                // Finished before the date was recorded: nothing to count.
                val end = if (used) d.date("usedUpAt") ?: return@mapNotNull null else null
                StockLife(d.str("title").orEmpty(), start, end)
            }
        }.catch { emit(emptyList()) }

    /** Puts [title] on the shopping list; the returned undo takes it off again. */
    fun addToShopping(householdId: String, uid: String, title: String): () -> Unit {
        val ref = collection(householdId, "shopping").document
        Writes.later { ref.set(common(uid, "restock") + mapOf("title" to title, "done" to false)) }
        return { Writes.later { ref.delete() } }
    }

    /**
     * "It's finished" (or [thrown] away): out of the pantry (or the medicine cabinet), onto the shopping list.
     * Returns the shopping item it added, if any, so "don't buy again" can take it off.
     */
    /** "Put it back": undoes Bin it or Used up, so it's in stock again (and off the buy list if it went on). */
    fun putBack(householdId: String, stock: PantryStock, from: String = "stock", shoppingId: String? = null) {
        val del = FieldValue.delete
        Writes.later { collection(householdId, from).document(stock.id).updateFields(mapOf("status" to "IN_STOCK", "wastedAt" to del, "usedUpAt" to del, "level" to (stock.initialAmount?.takeIf { it > 0 }?.let { ((stock.amount ?: it) / it * 100).toInt().coerceIn(0, 100) } ?: 100))) }
        shoppingId?.let { Writes.later { collection(householdId, "shopping").document(it).delete() } }
    }

    fun usedUp(householdId: String, uid: String, stock: PantryStock, openShopping: List<String>, from: String = "stock", thrown: Boolean = false, addToList: Boolean = true): String? {
        val batch = store.batch()
        batch.updateFields(
            collection(householdId, from).document(stock.id),
            // Thrown away keeps its level, so the waste is valued by what was left;
            // finished records when it ran out, so "how long did it last?" has an answer.
            if (thrown) mapOf("status" to "WASTED", "wastedAt" to FieldValue.serverTimestamp)
            else mapOf("status" to "USED_UP", "level" to 0, "usedUpAt" to FieldValue.serverTimestamp),
        )
        val added = if (addToList && openShopping.none { sameIngredient(it, stock.title) }) collection(householdId, "shopping").document else null
        added?.let { batch.set(it, common(uid, "pantry") + mapOf("title" to stock.title, "done" to false)) }
        Writes.later { batch.commit() }
        return added?.id
    }

    /**
     * "Threw some away": [share] (% of the pack) goes to waste and comes off what's left. When
     * nothing would be left, it's the whole thing thrown away. Returns true if all of it went.
     */
    /** Part of a pack thrown away. Returns what "Put it back" does: the level, amount and waste entry as they were. */
    suspend fun wastePart(householdId: String, uid: String, stock: PantryStock, openShopping: List<String>, share: Int, from: String = "stock"): () -> Unit {
        val ref = collection(householdId, from).document(stock.id)
        val doc = ref.get()
        val level = doc.long("level")?.toInt()
        val left = levelAfterWaste(level, share)
        if (left <= 0) {
            val added = usedUp(householdId, uid, stock, openShopping, from, thrown = true, addToList = false)
            return { putBack(householdId, stock, from, added) }
        }
        val entry = mapOf("share" to share, "at" to Timestamp.now())
        val quantity = doc.plainData()["quantity"]
        ref.updateFields(
            mapOf(
                "level" to left,
                "quantity" to stock.amount?.let { a -> stock.initialAmount?.takeIf { it > 0 }?.let { mapOf("amount" to it * left / 100.0, "unit" to stock.unit) } ?: mapOf("amount" to a, "unit" to stock.unit) },
                "wasteLog" to FieldValue.arrayUnion(entry),
            ),
        )
        return {
            Writes.later { ref.updateFields(mapOf("level" to (level ?: FieldValue.delete), "quantity" to (quantity ?: FieldValue.delete), "wasteLog" to FieldValue.arrayRemove(entry))) }
        }
    }

    /** "Won't take it today": no dose reminders that day, and what's left isn't counted down for it. */
    fun skipDose(householdId: String, medicineId: String, day: LocalDate) {
        Writes.later { collection(householdId, "medicines").document(medicineId).update("skipped" to FieldValue.arrayUnion(day.toEpochDay())) }
    }

    /** "Don't buy again" after finishing something: takes it back off the shopping list. */
    fun removeFromShopping(householdId: String, shoppingId: String) {
        Writes.later { collection(householdId, "shopping").document(shoppingId).delete() }
    }

    /** The follow-up to "bought": how much came home and when it's best before. */
    fun refineBought(householdId: String, stockId: String, amount: Double?, unit: String, expiry: LocalDate?) {
        val changes = buildMap<String, Any?> {
            amount?.let { put("quantity", mapOf("amount" to it, "unit" to unit)); put("initialAmount", it) }
            expiry?.let { put("expiry", it.stamp()) }
        }
        if (changes.isNotEmpty()) Writes.later { collection(householdId, "stock").document(stockId).updateFields(changes) }
    }

    private fun common(uid: String, source: String) = mapOf(
        "createdBy" to uid,
        "visibleTo" to Visibility.everyone,
        "createdAt" to FieldValue.serverTimestamp,
        "source" to source,
    )

    private fun short(value: Double?): String = when {
        value == null -> "some"
        value % 1.0 == 0.0 -> value.toLong().toString()
        else -> oneDecimal(value)
    }

    private fun quantity(doc: DocumentSnapshot): Pair<Double?, String?> {
        val q = doc.plainData()["quantity"] as? Map<*, *>
        return (q?.get("amount") as? Number)?.toDouble() to (q?.get("unit") as? String)
    }

    private fun toStock(doc: DocumentSnapshot): PantryStock {
        val (amount, unit) = quantity(doc)
        return PantryStock(
            id = doc.id,
            title = doc.str("title").orEmpty(),
            amount = amount,
            unit = unit ?: "pcs",
            initialAmount = doc.dbl("initialAmount") ?: amount,
            expiry = doc.date("expiry"),
            category = doc.str("groceryType") ?: doc.str("supplyType")
                ?: doc.strings("usedFor").firstOrNull(),
            keptAt = doc.str("keptAt")?.takeIf { it.isNotBlank() },
            photos = doc.strings("photos"),
            restock = doc.str("restock"),
            schedule = courseOf(doc)?.let { scheduleLine(it, systemToday()) },
        )
    }

    private fun toShopping(doc: DocumentSnapshot): ShoppingEntry {
        val (amount, unit) = quantity(doc)
        return ShoppingEntry(doc.id, doc.str("title") ?: doc.str("name").orEmpty(), amount, unit, doc.str("aisle"))
    }

    private fun toRecipe(doc: DocumentSnapshot) = Recipe(
        id = doc.id,
        title = doc.str("title").orEmpty(),
        ingredients = (doc.plainData()["ingredients"] as? List<*>).orEmpty().mapNotNull { raw ->
            val m = raw as? Map<*, *> ?: return@mapNotNull null
            Ingredient((m["name"] as? String).orEmpty(), (m["amount"] as? Number)?.toDouble(), (m["unit"] as? String) ?: "pcs")
        },
        servings = (doc.str("servings") ?: "2").toIntOrNull() ?: 2,
        mood = doc.strings("mood"),
        link = doc.str("link"),
        meal = doc.str("mealType"),
        macros = doc.str("macros")?.takeIf { it.isNotBlank() },
        photos = doc.strings("photos"),
    )
}
