package com.houseaffairs.app.core.data

import com.houseaffairs.app.core.time.*
import com.houseaffairs.app.core.util.*
import kotlinx.datetime.DayOfWeek

import kotlin.test.assertEquals
import kotlin.test.Test

class GalleryTest {
    @Test
    fun `photos are grouped in a fixed order - missing files and repeats left out`() {
        val photos = listOf(
            GalleryPhoto("a", "Keys", "Where it's kept", "KEPT", "k1"),
            GalleryPhoto("b", "Bali", "Vision board", "DREAM", "d1"),
            GalleryPhoto("c", "Sofa", "Wishes & lists", "WISH", "w1"),
            GalleryPhoto("b", "Bali", "Vision board", "DREAM", "d1"),
            GalleryPhoto("gone", "Old", "Recipes", "RECIPE", "r1"),
        )
        val groups = galleryGroups(photos) { it != "gone" }
        assertEquals(listOf("Vision board", "Wishes & lists", "Where it's kept"), groups.map { it.first })
        assertEquals(1, groups.first().second.size)
    }
}
