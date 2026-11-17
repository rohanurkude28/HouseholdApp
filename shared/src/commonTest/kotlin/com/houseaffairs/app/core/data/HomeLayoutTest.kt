package com.houseaffairs.app.core.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomeLayoutTest {
    @Test
    fun `a new phone shows everything but at a glance`() {
        val layout = parseLayout(null)
        assertEquals(HomeSection.entries, layout.map { it.section })
        assertEquals(listOf(HomeSection.GLANCE), layout.filter { !it.visible }.map { it.section })
    }

    @Test
    fun `old names and the saved order survive a round trip`() {
        val layout = parseLayout("TIMELINE:0,ATTENTION:1")
        val shown = layout.map { it.section }
        assertTrue(shown.indexOf(HomeSection.COMING_UP) < shown.indexOf(HomeSection.TODAY))
        assertEquals(false, layout.first { it.section == HomeSection.COMING_UP }.visible)
        assertEquals(layout, parseLayout(encodeLayout(layout)))
    }

    @Test
    fun `moving stops at the ends and toggling flips one`() {
        val layout = parseLayout(null)
        assertEquals(layout, layout.moved(HomeSection.STATS, up = true))
        assertEquals(HomeSection.TODAY, layout.moved(HomeSection.TODAY, up = true).first().section)
        assertEquals(false, layout.toggled(HomeSection.FAMILY).first { it.section == HomeSection.FAMILY }.visible)
    }
}
