package com.houseaffairs.app.core.data

/** One picture in the gallery, with the entry it belongs to (to open it) and its group. */
data class GalleryPhoto(val photoId: String, val title: String, val group: String, val entryType: String, val entryId: String)

/** The gallery's sections, in this order; empty ones are left out. */
val GALLERY_GROUPS = listOf("Vision board", "Wishes & lists", "Recipes", "Where it's kept", "Medicines", "Kitchen & supplies")

/**
 * Every photo the family added, grouped by where it lives and newest entries' photos first
 * within a group (the order they come in). Photos not on this phone are left out: they can't
 * be shown. Pure, so it's tested.
 */
fun galleryGroups(photos: List<GalleryPhoto>, onPhone: (String) -> Boolean): List<Pair<String, List<GalleryPhoto>>> =
    photos.filter { onPhone(it.photoId) }.distinctBy { it.photoId }.groupBy { it.group }
        .toList().sortedBy { (g, _) -> GALLERY_GROUPS.indexOf(g).let { if (it < 0) Int.MAX_VALUE else it } }
