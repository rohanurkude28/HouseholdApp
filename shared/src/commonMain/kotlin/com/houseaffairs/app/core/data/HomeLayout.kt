package com.houseaffairs.app.core.data

/** Home's sections, in their default order; each person can reorder and switch them (Customise). */
enum class HomeSection(val label: String) {
    STATS("Stats at the top"),
    TODAY("Today: tasks and alerts"),
    FAMILY("What's new at home"),
    TIDY("Tidy up & add"),
    COMING_UP("Coming up"),
    RIGHT_NOW("For right now"),
    USE_IT_UP("What to cook"),
    WATCH("Movies, books & music"),
    SHOPPING("Shopping list"),
    GLANCE("At a glance"),
}

data class SectionPref(val section: HomeSection, val visible: Boolean)

/** Sections renamed when Home was simplified, so saved layouts keep their order and switches. */
private val RENAMED = mapOf("ATTENTION" to HomeSection.TODAY, "TIMELINE" to HomeSection.COMING_UP)

/** A saved layout ("TODAY:1,FAMILY:0,…"), with sections added since slotted in at their place. */
fun parseLayout(raw: String?): List<SectionPref> {
    val saved = raw.orEmpty().split(",").mapNotNull { part ->
        val (name, on) = part.split(":").takeIf { it.size == 2 } ?: return@mapNotNull null
        (HomeSection.entries.firstOrNull { it.name == name } ?: RENAMED[name])?.let { SectionPref(it, on == "1") }
    }.distinctBy { it.section }
    // Sections added in later versions slot in at their default position, switched on.
    val result = saved.toMutableList()
    HomeSection.entries.forEachIndexed { index, section ->
        // "At a glance" repeats the tabs below, so it starts switched off.
        if (result.none { it.section == section }) result.add(index.coerceAtMost(result.size), SectionPref(section, section != HomeSection.GLANCE))
    }
    return result
}

fun encodeLayout(layout: List<SectionPref>) = layout.joinToString(",") { "${it.section.name}:${if (it.visible) 1 else 0}" }

/** [section] switched on or off. */
fun List<SectionPref>.toggled(section: HomeSection) = map { if (it.section == section) it.copy(visible = !it.visible) else it }

/** [section] one place up or down; unchanged at either end. */
fun List<SectionPref>.moved(section: HomeSection, up: Boolean): List<SectionPref> {
    val from = indexOfFirst { it.section == section }
    val to = if (up) from - 1 else from + 1
    return if (from < 0 || to !in indices) this else toMutableList().apply { add(to, removeAt(from)) }
}
