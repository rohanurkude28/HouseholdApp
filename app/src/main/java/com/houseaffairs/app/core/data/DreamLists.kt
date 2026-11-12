package com.houseaffairs.app.core.data

import com.houseaffairs.app.feature.quickadd.ListTemplate

/**
 * Which list a dream also shows in, so "Bali trip" is in Travel as well as on the vision board.
 * It's the same record in both places, not a copy, so saving towards it or marking it achieved
 * shows everywhere. Savings goals (emergency, festivals) aren't on any list.
 */
fun Dream.list(): ListTemplate? = when (kind) {
    "Travel" -> ListTemplate.TRAVEL
    "Car", "Home", "Gadget" -> ListTemplate.BUY
    "Education" -> ListTemplate.STUDY
    else -> null
}

/** Open dreams by the list they belong to. */
fun dreamsByList(dreams: List<Dream>): Map<ListTemplate, List<Dream>> =
    dreams.filter { !it.achieved }.mapNotNull { d -> d.list()?.let { it to d } }.groupBy({ it.first }, { it.second })
