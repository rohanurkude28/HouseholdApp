package com.houseaffairs.app.feature.add

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Redeem
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.ui.graphics.vector.ImageVector
import com.houseaffairs.app.core.designsystem.Accent
import com.houseaffairs.app.core.data.Ingredient
import com.houseaffairs.app.core.data.SUBCATEGORIES
import com.houseaffairs.app.core.data.Aisle
import com.houseaffairs.app.core.data.KEPT_KINDS
import com.houseaffairs.app.core.data.SUPPLY_TYPES
import com.houseaffairs.app.core.designsystem.HouseColors
import com.houseaffairs.app.core.model.AppModule
import com.houseaffairs.app.feature.quickadd.EXPENSE_CATEGORIES
import com.houseaffairs.app.feature.quickadd.ListTemplate
import java.time.LocalDate

private val PAYMENT_METHODS = listOf("UPI", "Card", "Cash", "Bank transfer")
private val FREQUENCIES = com.houseaffairs.app.core.data.RENEWAL_FREQUENCIES
private val REMINDERS = listOf("No reminder", "On the day", "1 day before", "3 days before", "1 week before")
private val RESTOCK = listOf("Off", "When it's low", "Every week", "Every month")
private val FLAVOURS = listOf("Sweet", "Spicy", "Tangy", "Crispy", "Savoury", "Healthy")
private val RATING = listOf("👍 Liked it", "👎 Not great")
private val PRIORITY = listOf("Low", "Normal", "High")
/** Task priority, most urgent first; a task without one is Low. */
val TASK_PRIORITIES = listOf("High", "Medium", "Low")

val DREAM_KINDS = linkedMapOf(
    "Travel" to "✈️", "Car" to "🚗", "Home" to "🏡", "Gadget" to "📱",
    "Education" to "🎓", "Wedding" to "💍", "Experience" to "🎟️",
    // Savings goals that aren't a purchase: an emergency fund, festivals, a rainy day.
    "Savings" to "🐷", "Emergency" to "🛟", "Festival" to "🪔", "Other" to "⭐",
)
val ACTIVITY_KINDS = listOf("Walk", "Run", "Cycling", "Yoga", "Gym", "Sports", "Swim", "Dance", "Gardening", "Housework")
val PLACE_KINDS = listOf("Restaurant", "Café", "Grocery", "Pharmacy", "Doctor", "Hospital", "Market", "Mall", "Salon", "Service", "Other")
val INVESTMENT_KINDS = listOf("Fixed deposit", "Mutual fund", "Recurring deposit", "Savings account", "Gold", "Stocks", "PPF", "Other")
val CREDIT_KINDS = listOf("Gift card", "Card points", "Coupon", "Cashback", "Voucher")

/** The list a wish is on; older items stored the name ("WATCH") rather than the label, and both work. */
private fun template(values: FormValues) = ListTemplate.of(values["template"] as? String)

/** Everything that can be added. Each kind is a collection plus the fields its form shows. */
enum class EntryType(
    val label: String,
    val formTitle: String,
    val icon: ImageVector,
    val accent: (HouseColors) -> Accent,
    val module: AppModule,
    val collection: String,
    /** The field saved as the entry's `title`; Home lists show it. */
    val titleKey: String?,
    val fields: List<FieldSpec>,
    val available: Boolean = true,
    /** Shown in the add menu. Investments stay hidden: this is a household app, not a portfolio. */
    val listed: Boolean = true,
) {
    EXPENSE(
        "Expense", "New expense", Icons.Outlined.Receipt, { it.money }, AppModule.MONEY, "transactions", "merchant",
        listOf(
            MoneySpec(),
            ChoiceSpec("category", "Category", EXPENSE_CATEGORIES, required = true),
            *SUBCATEGORIES.map { (cat, subs) ->
                ChoiceSpec("subcategory", "Type of ${cat.lowercase()}", subs, allowCustom = true, visibleWhen = { it["category"] == cat })
            }.toTypedArray(),
            TextSpec("merchant", "Where or who", "DMart, Uber, plumber…"),
            ChoiceSpec("trip", "Part of a trip or outing", listOf("None"), dynamicOptions = "trips", hideWithoutDynamic = true),
            // Planned spends reserve budget now ("Goa trip next month") and become real when paid.
            ChoiceSpec("status", "Status", listOf("Paid", "Planned"), required = true),
            DateSpec("date", "When", required = true),
            // One choice: UPI, cash, bank, or the exact credit card (each card listed by name, no
            // separate "which card" question). Without cards set up, it's just "Card".
            ChoiceSpec("paymentMethod", "Paid with", PAYMENT_METHODS, dynamicOptions = "cards", replacedByDynamic = "Card"),
            MembersSpec("paidBy", "Paid by", multi = false, essential = false),
            // Lightweight, not a bill split: who it was for ("Riya's school shoes").
            MembersSpec("forWhom", "For whom", essential = false),
            ChoiceSpec("tags", "Tags", listOf("Trip", "Festival", "Kids", "Work", "Gift"), multi = true, allowCustom = true, essential = false),
            TextSpec("note", "Note", multiline = true, essential = false),
        ),
    ),
    SUBSCRIPTION(
        "Subscription", "New subscription", Icons.Outlined.Autorenew, { it.money }, AppModule.MONEY, "recurring", "title",
        listOf(
            TextSpec("title", "Name", "Netflix, rent, gym…", required = true),
            MoneySpec(),
            ChoiceSpec("frequency", "How often", FREQUENCIES, required = true),
            DateSpec("nextDate", "Next payment", required = true),
            ChoiceSpec("category", "Category", EXPENSE_CATEGORIES, essential = false),
            ChoiceSpec("paymentMethod", "Paid with", PAYMENT_METHODS, essential = false),
            ChoiceSpec("remind", "Remind me", REMINDERS, essential = false),
            // "Ask me": Today shows a "Log it" button on the day. Otherwise it's logged as spent by itself.
            ChoiceSpec("onRenew", "When it renews", listOf("Ask me", "Log it automatically"), essential = false),
            // "Rarely" or "Not using" gets a "cancel before it renews?" heads-up a week ahead.
            ChoiceSpec("using", "Still using it?", listOf("Yes", "Rarely", "Not using"), essential = false),
            TextSpec("note", "Note", multiline = true, essential = false),
        ),
    ),
    GROCERY(
        "Grocery", "Add to pantry", Icons.Outlined.Kitchen, { it.stock }, AppModule.STOCK, "stock", "title",
        listOf(
            TextSpec("title", "Item", "Paneer, rice, olive oil…", required = true),
            QuantitySpec(units = listOf("pcs", "g", "kg", "ml", "L", "pack")),
            DateSpec("expiry", "Best before"),
            // Guessed from the name when left empty, so only needed to correct a guess.
            ChoiceSpec("groceryType", "Category", Aisle.FOOD.map { it.label }),
            TextSpec("keptAt", "Kept at", "Spice box, top shelf, fridge door…", essential = false),
            // With the rating, the shopping list can say "Last time: Amul 👍".
            TextSpec("brand", "Brand", "Amul, Tata, Aashirvaad…", essential = false),
            MoneySpec("cost", "Cost (adds to budget)", required = false, essential = false),
            TextSpec("store", "Bought at", essential = false),
            LevelSpec("level", "How much is left", "% left"),
            ChoiceSpec("flavours", "Taste", FLAVOURS, multi = true, allowCustom = true, essential = false),
            ChoiceSpec("rating", "Was it good?", RATING, essential = false),
            ChoiceSpec("restock", "Remind to restock", RESTOCK, essential = false),
        ),
    ),
    MEDICINE(
        "Medicine", "Add medicine", Icons.Outlined.LocalHospital, { it.vault }, AppModule.MEDICINE, "medicines", "title",
        listOf(
            TextSpec("title", "Medicine", "Crocin, cough syrup…", required = true),
            // Tags match the assistant's "I'm feeling…" care list, so it can suggest what's at home.
            ChoiceSpec(
                "usedFor", "Used for",
                listOf("Fever", "Headache", "Pain", "Cold & cough", "Sore throat", "Allergy", "Acidity", "Stomach", "Loose motions", "Cuts & wounds", "Burns", "Sprain", "Insect bite", "Vitamins"),
                multi = true, allowCustom = true,
            ),
            MembersSpec("forWhom", "For"),
            DateSpec("expiry", "Expires"),
            TextSpec("keptAt", "Kept at", "Medicine box, bedroom drawer…", essential = false),
            QuantitySpec(units = listOf("tablets", "strips", "ml", "bottles", "pcs"), essential = false),
            ChoiceSpec("form", "Form", listOf("Tablet", "Syrup", "Capsule", "Ointment", "Drops", "Inhaler", "Other"), essential = false),
            DateSpec("boughtOn", "Bought on", past = true, essential = false),
            MoneySpec("cost", "Cost (adds to budget)", required = false, essential = false),
            TextSpec("dosage", "Dosage note", "1 tablet after food, twice a day", essential = false),
            ChoiceSpec("restock", "Remind to restock", RESTOCK, essential = false),
        ),
    ),
    SHOPPING(
        "To buy", "Add to shopping list", Icons.Outlined.ShoppingCart, { it.stock }, AppModule.STOCK, "shopping", "title",
        listOf(
            TextSpec("title", "Item", "Milk, eggs…", required = true),
            QuantitySpec(units = listOf("pcs", "g", "kg", "ml", "L", "pack")),
            TextSpec("store", "Buy at", essential = false),
            // Guessed from the name when left empty; only needed to correct a guess.
            ChoiceSpec("aisle", "Aisle", com.houseaffairs.app.core.data.Aisle.entries.map { it.label }, essential = false),
            ChoiceSpec("priority", "Priority", PRIORITY, essential = false),
            TextSpec("note", "Note", essential = false),
        ),
    ),
    TASK(
        "Task", "New task", Icons.Outlined.TaskAlt, { it.plans }, AppModule.PLANS, "tasks", "title",
        listOf(
            TextSpec("title", "What needs doing", "Pay rent, call plumber…", required = true),
            MembersSpec("assignee", "Who", multi = false),
            DateSpec("due", "Due"),
            ChoiceSpec("priority", "Priority", TASK_PRIORITIES),
            ChoiceSpec("repeat", "Repeat", listOf("Never", "Daily", "Weekly", "Monthly"), essential = false),
            // A chore rota: each time it's done, the next one goes to the next person.
            MembersSpec("rotation", "Take turns", essential = false, visibleWhen = { (it["repeat"] as? String).let { r -> r != null && r != "Never" } }),
            ChoiceSpec("remind", "Remind", REMINDERS, essential = false),
            TextSpec("note", "Note", multiline = true, essential = false),
        ),
    ),
    EVENT(
        "Event", "New event", Icons.Outlined.Cake, { it.plans }, AppModule.PLANS, "events", "title",
        listOf(
            TextSpec("title", "What", "Riya's birthday, car insurance…", required = true),
            ChoiceSpec("kind", "Kind", listOf("Birthday", "Anniversary", "Renewal", "Appointment", "Other"), required = true),
            DateSpec("date", "On", required = true),
            MembersSpec("people", "Who it's for"),
            ChoiceSpec("repeat", "Repeat", listOf("Never", "Monthly", "Yearly"), essential = false),
            ChoiceSpec("remind", "Remind", REMINDERS, essential = false),
            TextSpec("note", "Note", multiline = true, essential = false),
        ),
    ),
    WISH(
        "Wish", "Add to a family list", Icons.Outlined.Star, { it.vault }, AppModule.PLANS, "listItems", "title",
        listOf(
            ChoiceSpec("template", "List", ListTemplate.entries.map { it.label }, required = true),
            TextSpec("title", "What", "Dune 2, Bali, a new sofa…", required = true),
            // Set by hand, it wins over anything looked up online, so no TMDB key is needed.
            ChoiceSpec(
                "platform", "Where to watch", listOf("Netflix", "Prime Video", "Hotstar", "SonyLIV", "Zee5", "YouTube", "Theatre"),
                allowCustom = true,
                visibleWhen = { template(it) == ListTemplate.WATCH },
            ),
            ChoiceSpec(
                "platform", "Listen on", listOf("YouTube Music", "Spotify", "JioSaavn", "Apple Music"),
                allowCustom = true,
                visibleWhen = { template(it) == ListTemplate.MUSIC },
            ),
            TextSpec(
                "creator", "By", "Author or artist",
                visibleWhen = { template(it) in setOf(ListTemplate.BOOKS, ListTemplate.MUSIC) }, essential = false,
            ),
            TextSpec("place", "Where", "City, restaurant, store", visibleWhen = { template(it) in setOf(ListTemplate.TRAVEL, ListTemplate.EAT_OUT) }),
            MembersSpec("forWhom", "For", visibleWhen = { template(it) == ListTemplate.GIFTS }),
            // Links the gift to a birthday or anniversary, which then shows "🎁 2 gift ideas".
            ChoiceSpec("event", "For the occasion", emptyList(), dynamicOptions = "events", hideWithoutDynamic = true, visibleWhen = { template(it) == ListTemplate.GIFTS }),
            TextSpec("link", "Link", "Instagram reel, website…", isLink = true, essential = false),
            TextSpec("thingsToTry", "Things to try or buy there", multiline = true, essential = false, visibleWhen = { template(it) in setOf(ListTemplate.TRAVEL, ListTemplate.EAT_OUT) }),
            // Movies, books and music don't need a budget or a deadline; trips, buys and gifts do.
            MoneySpec("budget", "Rough budget", required = false, essential = false, visibleWhen = { template(it)?.isMedia != true }),
            DateSpec("targetDate", "By when", essential = false, visibleWhen = { template(it)?.isMedia != true }),
            TextSpec("note", "Note", multiline = true, essential = false),
        ),
    ),
    RECIPE(
        "Recipe", "Save a recipe", Icons.Outlined.RestaurantMenu, { it.stock }, AppModule.STOCK, "recipes", "title",
        listOf(
            TextSpec("title", "Dish", "Paneer tikka…", required = true),
            IngredientsSpec(),
            // Quantities are written for this many people; cooking scales them to who's eating.
            ChoiceSpec("servings", "Quantities above are for (people)", listOf("1", "2", "4"), required = true),
            ChoiceSpec("mood", "Mood", FLAVOURS, multi = true, allowCustom = true),
            TextSpec("link", "Reel or recipe link", isLink = true),
            ChoiceSpec("mealType", "Meal", listOf("Breakfast", "Lunch", "Dinner", "Snack", "Dessert"), essential = false),
            TextSpec("prepTime", "Prep time", "20 min", essential = false),
            TextSpec("macros", "Protein · carbs · fat · fibre", "e.g. 25g · 30g · 12g · 4g", essential = false),
            ChoiceSpec("rating", "How was it?", RATING, essential = false),
        ),
    ),
    PLACE(
        "Place", "Save a place", Icons.Outlined.Place, { it.plans }, AppModule.PLANS, "places", "title",
        listOf(
            TextSpec("title", "Name", "Toit, Spar, Dr. Mehta's clinic…", required = true),
            ChoiceSpec("kind", "Kind", PLACE_KINDS, required = true),
            TextSpec("area", "Area or address", "Indiranagar"),
            ChoiceSpec("rating", "How is it?", listOf("❤️ Love it", "👍 Good", "👎 Not great", "Want to try"), essential = false),
            TextSpec("goodFor", "Things to try or buy there", "Butter chicken, fresh paneer…", multiline = true, essential = false),
            TextSpec("phone", "Phone", essential = false),
            TextSpec("mapLink", "Google Maps link", isLink = true, essential = false),
            TextSpec("note", "Note", essential = false),
        ),
    ),
    KEPT(
        "Where it's kept", "Remember where it's kept", Icons.Outlined.Inventory2, { it.vault }, AppModule.VAULT, "keptAt", "title",
        listOf(
            TextSpec("title", "What", "Gold necklace, spare car keys, passports…", required = true),
            TextSpec("location", "Where", "Bank locker, blue drawer in the bedroom…", required = true),
            ChoiceSpec("kind", "Kind", KEPT_KINDS),
            ChoiceSpec("room", "Room or place", listOf("Bedroom", "Wardrobe", "Locker", "Bank locker", "Kitchen", "Storeroom", "Car", "Office"), allowCustom = true, essential = false),
            MembersSpec("knows", "Who else should know", essential = false),
            TextSpec("note", "Note", essential = false),
        ),
    ),
    HOUSEHOLD(
        "Supplies", "Add household supplies", Icons.Outlined.Handyman, { it.stock }, AppModule.STOCK, "stock", "title",
        listOf(
            TextSpec("title", "Item", "AAA batteries, sunscreen, detergent…", required = true),
            QuantitySpec(units = listOf("units", "pcs", "packs", "boxes", "rolls", "L", "kg")),
            ChoiceSpec("supplyType", "Type", SUPPLY_TYPES, allowCustom = true),
            DateSpec("expiry", "Expires"),
            TextSpec("keptAt", "Kept at", "Drawer under the TV", essential = false),
            MoneySpec("cost", "Cost (adds to budget)", required = false, essential = false),
            ChoiceSpec("restock", "Remind to restock", RESTOCK, essential = false),
            TextSpec("note", "Note", essential = false),
        ),
    ),
    INVESTMENT(
        "Investment", "Add an investment", Icons.AutoMirrored.Outlined.TrendingUp, { it.money }, AppModule.MONEY, "investments", "title",
        listOf(
            TextSpec("title", "Name", "HDFC FD, Axis Bluechip SIP…", required = true),
            ChoiceSpec("kind", "Kind", INVESTMENT_KINDS, required = true),
            MoneySpec(label = "Amount", required = false),
            ChoiceSpec("fundedFrom", "Where the money comes from", listOf("Gullak", "Already invested"), required = true),
            ChoiceSpec("forDream", "Saving for", listOf("Nothing specific"), dynamicOptions = "dreams"),
            TextSpec("rate", "Interest rate %", "7.1", essential = false),
            DateSpec("maturity", "Matures on", essential = false),
            TextSpec("note", "Note", essential = false),
        ),
        listed = false,
    ),
    GOAL(
        "Goal", "New weekly goal", Icons.Outlined.EmojiEvents, { it.plans }, AppModule.PLANS, "weeklyGoals", "title",
        listOf(
            TextSpec("title", "Goal", "Finish a book, 3 workouts…", required = true),
            ChoiceSpec("kind", "Kind", listOf("Learning", "To get done", "Health", "Money")),
            MembersSpec("who", "Who"),
            LevelSpec("progress", "Progress", "% done"),
            DateSpec("by", "By when", essential = false),
        ),
    ),
    TRIP(
        "Trip", "Start a trip or outing", Icons.Outlined.Luggage, { it.money }, AppModule.MONEY, "trips", "title",
        listOf(
            TextSpec("title", "Name", "Sunday outing, Goa trip…", required = true),
            DateSpec("start", "Starts", required = true),
            DateSpec("end", "Ends"),
            MoneySpec("budget", "Budget for it", required = false),
            // Trips sit on the vision board under the dream they make come true.
            ChoiceSpec("forDream", "Part of a dream", listOf("Not linked"), dynamicOptions = "dreams", hideWithoutDynamic = true),
            MembersSpec("who", "Who's going", essential = false),
            TextSpec("note", "Note", essential = false),
        ),
    ),
    ACTIVITY(
        "Activity", "Log an activity", Icons.AutoMirrored.Outlined.DirectionsWalk, { it.plans }, AppModule.PLANS, "activities", "kind",
        listOf(
            ChoiceSpec("kind", "What", ACTIVITY_KINDS, allowCustom = true, required = true),
            ChoiceSpec("minutes", "How long (minutes)", listOf("15", "30", "45", "60", "90"), allowCustom = true, required = true),
            MembersSpec("who", "Who"),
            DateSpec("date", "When", past = true, required = true),
            TextSpec("steps", "Steps", "8000", essential = false),
            TextSpec("note", "Note", essential = false),
        ),
    ),
    DREAM(
        "Dream or gullak", "New dream or gullak", Icons.Outlined.AutoAwesome, { it.vault }, AppModule.PLANS, "dreams", "title",
        listOf(
            TextSpec("title", "Name", "Bali trip, emergency fund, Diwali…", required = true),
            ChoiceSpec("kind", "Kind", DREAM_KINDS.keys.toList(), required = true),
            MoneySpec(label = "Target (optional)", required = false),
            DateSpec("targetDate", "By when"),
            ChoiceSpec("horizon", "Short, medium or long term", com.houseaffairs.app.core.data.HORIZON_CHOICES),
            ChoiceSpec("emoji", "Picture", DREAM_KINDS.values.distinct() + listOf("🌴", "🎉", "⭐"), essential = false),
            MembersSpec("who", "For", essential = false),
            TextSpec("link", "Inspiration link", "Reel, listing, website…", isLink = true, essential = false),
            TextSpec("note", "Note", multiline = true, essential = false),
        ),
    ),
    CREDIT(
        "Credit", "Add a gift card or coupon", Icons.Outlined.Redeem, { it.money }, AppModule.MONEY, "credits", "title",
        listOf(
            TextSpec("title", "Name", "Amazon gift card, HDFC points…", required = true),
            ChoiceSpec("kind", "Kind", CREDIT_KINDS, required = true),
            MoneySpec(label = "Worth"),
            ChoiceSpec("earmark", "Save it for", listOf("Anything") + EXPENSE_CATEGORIES.filter { it != "Rent" }, dynamicOptions = "dreams"),
            DateSpec("expiry", "Expires"),
            TextSpec("code", "Code or card number", essential = false),
            TextSpec("note", "Note", essential = false),
        ),
    ),
    DOCUMENT(
        "Document", "Add a document", Icons.Outlined.Description, { it.vault }, AppModule.VAULT, "documents", "title",
        emptyList(),
        available = false,
    ),
    ;

    /** Pre-filled values for a fresh form. */
    fun defaults(myUid: String, currency: String, today: LocalDate = LocalDate.now()): FormValues = buildMap {
        fields.filterIsInstance<MoneySpec>().forEach { put(it.key, MoneyValue("", currency)) }
        fields.filterIsInstance<QuantitySpec>().forEach { put(it.key, QuantityValue("", it.units.first())) }
        when (this@EntryType) {
            EXPENSE -> {
                put("status", "Paid")
                put("date", today)
                put("paidBy", listOf(myUid))
            }
            RECIPE -> {
                put("servings", "1")
                put("ingredients", listOf(Ingredient("", null, "g")))
            }
            SUBSCRIPTION -> {
                put("frequency", "Monthly")
                put("nextDate", today.plusMonths(1))
                put("remind", "1 day before")
            }
            GROCERY -> put("level", 100)
            TASK -> {
                put("assignee", listOf(myUid))
                put("priority", "Low")
            }
            EVENT -> {
                put("kind", "Birthday")
                put("repeat", "Yearly")
                put("remind", "1 day before")
            }
            DREAM -> put("kind", "Travel")
            ACTIVITY -> {
                put("kind", "Walk")
                put("minutes", "30")
                put("who", listOf(myUid))
                put("date", today)
            }
            TRIP -> {
                put("forDream", "Not linked")
                put("start", today)
                put("who", listOf(myUid))
            }
            HOUSEHOLD -> put("level", 100)
            INVESTMENT -> {
                put("kind", "Fixed deposit")
                put("fundedFrom", "Gullak")
                put("forDream", "Nothing specific")
            }
            PLACE -> put("kind", "Restaurant")
            CREDIT -> {
                put("kind", "Gift card")
                put("earmark", "Anything")
            }
            GOAL -> {
                put("who", listOf(myUid))
                put("progress", 0)
            }
            else -> Unit
        }
    }
}
