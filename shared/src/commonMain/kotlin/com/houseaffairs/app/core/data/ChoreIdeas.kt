package com.houseaffairs.app.core.data

// Shared so the iPhone offers the same chores as Android.

/** A chore to offer when setting up the family's list, with how often it's usually done. */
data class ChoreIdea(val emoji: String, val title: String, val repeat: String)

val CHORE_IDEAS = listOf(
    ChoreIdea("🗑️", "Take out the trash", "Daily"),
    ChoreIdea("🍽️", "Wash the dishes", "Daily"),
    ChoreIdea("🧽", "Wipe the kitchen counters", "Daily"),
    ChoreIdea("💧", "Refill water bottles", "Daily"),
    ChoreIdea("🪴", "Water the plants", "Every 2 days"),
    ChoreIdea("🧹", "Sweep and mop", "Every 2 days"),
    ChoreIdea("👕", "Do laundry", "Every 3 days"),
    ChoreIdea("👔", "Iron clothes", "Weekly"),
    ChoreIdea("🛏️", "Change bedsheets", "Weekly"),
    ChoreIdea("🚿", "Clean the bathroom", "Weekly"),
    ChoreIdea("🪶", "Dust the shelves", "Weekly"),
    ChoreIdea("🛒", "Check what to restock", "Weekly"),
    ChoreIdea("🧊", "Clean the fridge", "Every 2 weeks"),
    ChoreIdea("🌀", "Clean the fans", "Monthly"),
    ChoreIdea("🚗", "Wash the car", "Every 2 weeks"),
)
