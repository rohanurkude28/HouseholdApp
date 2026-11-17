package com.houseaffairs.app.core.data

import com.houseaffairs.app.feature.add.EntryType
import com.houseaffairs.app.feature.add.FormValues
import com.houseaffairs.app.feature.add.MoneyValue
import com.houseaffairs.app.feature.add.QuantityValue
import com.houseaffairs.app.feature.quickadd.ListTemplate
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fills a fresh demo home with a few weeks of believable family life, so every screen has
 * something to show: a budget and spending, a stocked kitchen, tasks, birthdays, lists and more.
 * Saved through the normal save path, so it behaves exactly like real entries.
 */
@Singleton
class DemoSeeder @Inject constructor(
    private val entries: EntryRepository,
    private val money: MoneyRepository,
) {
    suspend fun seed(householdId: String, uid: String, today: LocalDate = LocalDate.now()) {
        val cur = "INR"
        fun rs(amount: Int) = MoneyValue(amount.toString(), cur)
        fun save(type: EntryType, values: FormValues) = entries.save(householdId, uid, type, values, listOf("*"), "demo")

        money.saveBudget(
            householdId, uid,
            Budget(
                limitMinor = 100_000_00, currency = cur, savingsGoalMinor = 20_000_00, incomeMinor = 125_000_00,
                categories = mapOf(
                    "Rent" to 30_000_00, "Groceries" to 12_000_00, "Dining" to 5_000_00, "Transport" to 5_000_00,
                    "Bills" to 6_000_00, "Shopping" to 6_000_00, "Health" to 3_000_00, "Entertainment" to 3_000_00,
                ),
            ),
        )
        listOf(
            Triple("Rent", "Landlord", 30_000) to 1L, Triple("Groceries", "DMart", 2_340) to 2L, Triple("Dining", "Toit", 1_850) to 3L,
            Triple("Transport", "Uber", 420) to 1L, Triple("Bills", "Electricity", 1_960) to 5L, Triple("Groceries", "Vegetable vendor", 380) to 0L,
            Triple("Shopping", "Myntra", 2_199) to 6L, Triple("Dining", "Swiggy", 640) to 0L,
        ).forEach { (e, ago) ->
            val (category, merchant, amount) = e
            save(EntryType.EXPENSE, mapOf("amount" to rs(amount), "category" to category, "merchant" to merchant, "status" to "Paid", "date" to today.minusDays(ago), "paymentMethod" to if (ago % 2 == 0L) "UPI" else "Card"))
        }
        save(EntryType.EXPENSE, mapOf("amount" to rs(15_000), "category" to "Travel", "merchant" to "Goa trip", "status" to "Planned", "date" to today.plusDays(12)))
        save(EntryType.SUBSCRIPTION, mapOf("title" to "Netflix", "amount" to rs(649), "frequency" to "Monthly", "nextDate" to today.plusDays(4), "category" to "Entertainment"))
        save(EntryType.SUBSCRIPTION, mapOf("title" to "Hotstar", "amount" to rs(299), "frequency" to "Monthly", "nextDate" to today.plusDays(5), "category" to "Entertainment", "using" to "Not using"))

        listOf(
            Triple("Paneer", QuantityValue("200", "g"), today.plusDays(1)), Triple("Milk", QuantityValue("1", "L"), today.plusDays(2)),
            Triple("Onions", QuantityValue("1", "kg"), null), Triple("Tomatoes", QuantityValue("500", "g"), today.plusDays(4)),
            Triple("Potatoes", QuantityValue("2", "kg"), null), Triple("Toor dal", QuantityValue("1", "kg"), null),
            Triple("Rice", QuantityValue("5", "kg"), null), Triple("Peas", QuantityValue("250", "g"), today.plusDays(6)),
            Triple("Jeera", QuantityValue("100", "g"), null), Triple("Garam masala", QuantityValue("100", "g"), null),
            Triple("Besan", QuantityValue("500", "g"), null), Triple("Curd", QuantityValue("400", "g"), today.plusDays(2)),
        ).forEach { (title, qty, expiry) ->
            save(EntryType.GROCERY, mapOf("title" to title, "quantity" to qty, "expiry" to expiry, "keptAt" to if (title == "Jeera") "Spice box" else null))
        }
        listOf("Eggs", "Bread", "Coriander", "Ghee").forEach { save(EntryType.SHOPPING, mapOf("title" to it)) }
        // A few everyday dishes as the demo family's own recipes, so Cook has something to show.
        SAMPLE_RECIPES.filter { it.title in setOf("Paneer tikka", "Matar paneer", "Dal tadka", "Curd rice", "Kheer", "Chicken curry", "Veg pulao", "Aloo paratha") }.forEach { r ->
            save(
                EntryType.RECIPE,
                mapOf(
                    "title" to r.title, "ingredients" to r.ingredients, "servings" to r.servings.toString(),
                    "mood" to r.mood, "mealType" to r.meal?.takeIf { it != "Lunch or dinner" }, "macros" to r.macros,
                ),
            )
        }
        save(EntryType.MEDICINE, mapOf("title" to "Crocin", "usedFor" to listOf("Fever", "Headache"), "expiry" to today.plusMonths(8), "keptAt" to "Medicine drawer"))
        save(EntryType.MEDICINE, mapOf("title" to "Cough syrup", "usedFor" to listOf("Cold & cough"), "expiry" to today.plusDays(20)))
        save(EntryType.HOUSEHOLD, mapOf("title" to "Sunscreen", "supplyType" to "Cosmetics & skincare", "expiry" to today.plusMonths(5)))
        save(EntryType.HOUSEHOLD, mapOf("title" to "AAA batteries", "supplyType" to "Batteries", "quantity" to QuantityValue("6", "units")))

        save(EntryType.TASK, mapOf("title" to "Pay school fees", "due" to today, "assignee" to listOf(uid)))
        save(EntryType.TASK, mapOf("title" to "Water the plants", "due" to today.plusDays(1), "repeat" to "Weekly", "assignee" to listOf(uid)))
        save(EntryType.TASK, mapOf("title" to "Book car service", "due" to today.plusDays(3)))
        save(EntryType.EVENT, mapOf("title" to "Riya's birthday", "kind" to "Birthday", "date" to today.plusDays(5), "repeat" to "Yearly"))
        save(EntryType.EVENT, mapOf("title" to "Car insurance renewal", "kind" to "Renewal", "date" to today.plusDays(20), "repeat" to "Yearly"))
        save(EntryType.GOAL, mapOf("title" to "Finish a book", "kind" to "Learning", "progress" to 60))

        save(EntryType.WISH, mapOf("template" to ListTemplate.WATCH.label, "title" to "Panchayat"))
        save(EntryType.WISH, mapOf("template" to ListTemplate.WATCH.label, "title" to "Dune: Part Two"))
        save(EntryType.WISH, mapOf("template" to ListTemplate.BOOKS.label, "title" to "Atomic Habits"))
        save(EntryType.WISH, mapOf("template" to ListTemplate.TRAVEL.label, "title" to "Goa", "place" to "North Goa"))
        save(EntryType.WISH, mapOf("template" to ListTemplate.GIFTS.label, "title" to "Art set", "event" to "Riya's birthday"))
        save(EntryType.DREAM, mapOf("title" to "Bali trip", "kind" to "Travel", "amount" to rs(60_000), "targetDate" to today.plusMonths(5)))
        save(EntryType.PLACE, mapOf("title" to "Toit", "kind" to "Restaurant", "area" to "Indiranagar", "rating" to "❤️ Love it"))
        save(EntryType.KEPT, mapOf("title" to "Passports", "location" to "Blue file in the wardrobe", "kind" to "Documents"))
        save(EntryType.KEPT, mapOf("title" to "Gold necklace", "location" to "Bank locker", "kind" to "Jewellery"))
    }
}
