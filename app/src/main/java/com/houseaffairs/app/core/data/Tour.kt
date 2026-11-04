package com.houseaffairs.app.core.data

import com.houseaffairs.app.core.model.AppModule

/** Something a tour step can do besides explain: ask for notifications, or open a tab to set it up. */
enum class TourAction { NOTIFICATIONS, OPEN_MONEY, OPEN_KITCHEN }

/** One screen of the first-run tour: one feature, with a line to try in quick add. */
data class TourStep(
    val id: String,
    val emoji: String,
    val title: String,
    val body: String,
    val tryIt: String? = null,
    val action: TourAction? = null,
    /** The part of the app this step is about; steps a person's role can't open are left out. */
    val module: AppModule? = null,
)

private val STEPS = listOf(
    TourStep("welcome", "🏠", "Welcome home", "One place for your family's money, kitchen, medicines, plans and the things you keep. Here's a quick look, one thing at a time."),
    TourStep("quickadd", "➕", "Just say it", "Tap + and type or speak one line. The app works out what it is and fills the form for you.", "450 groceries DMart · milk over · call mom at 5 pm"),
    TourStep("budget", "💰", "Budget and spending", "Set a monthly budget with your own categories, like cook, maid or insurance. Every expense shows what's left.", "paid cook 6000 cash", TourAction.OPEN_MONEY, AppModule.MONEY),
    TourStep("gullak", "🐷", "Gullak and dreams", "Money left at the end of the month goes to the gullak. Save it toward a dream, like a trip or a new car.", "save 5000 for Bali", module = AppModule.MONEY),
    TourStep("kitchen", "🍳", "Kitchen", "Pantry, shopping list and recipes in one place. Cooking a dish takes it out of the pantry, and ideas only show dishes you can make.", "made dal for 4", TourAction.OPEN_KITCHEN, AppModule.STOCK),
    TourStep("medicines", "💊", "Medicines", "Know what's at home, who it's for and when it expires. Say how someone feels to see what helps.", "Riya has a cold", module = AppModule.MEDICINE),
    TourStep("plans", "✅", "Tasks and events", "Tasks can repeat, take turns and ring at the time you pick. Birthdays remind you a day before.", "call mom at 5:30 pm", module = AppModule.PLANS),
    TourStep("lists", "🎬", "Family lists", "Movies, books, music, trips and wishes, with covers and ratings looked up for you.", "want to read Atomic Habits", module = AppModule.PLANS),
    TourStep("things", "📦", "Where things are kept", "Note where things are, then just ask when you can't find them.", "where is the passport?", module = AppModule.VAULT),
    TourStep("reminders", "🔔", "Reminders", "Allow notifications so tasks ring on time, plus one short summary a day. You can change this in Settings.", action = TourAction.NOTIFICATIONS),
    TourStep("done", "🎉", "You're all set", "Start with one thing you need today. You can see this tour again from Settings."),
)

/** The tour for someone who can open [canAccess] parts of the app. */
fun tourSteps(canAccess: (AppModule) -> Boolean): List<TourStep> = STEPS.filter { it.module == null || canAccess(it.module) }

/** Where to pick up a tour left part-way: the saved step, kept inside the tour. */
fun tourResumeAt(saved: Int?, size: Int): Int = (saved ?: 0).coerceIn(0, (size - 1).coerceAtLeast(0))
