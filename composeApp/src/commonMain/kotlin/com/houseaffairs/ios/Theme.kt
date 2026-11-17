package com.houseaffairs.ios

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.houseaffairs.app.core.designsystem.HouseTheme
import com.houseaffairs.app.core.ui.HouseImages
import com.houseaffairs.app.core.ui.LocalHouseImages

/** Android's colours, type, shapes and module accents (from :ui), following the phone's light or dark. */
@Composable
fun HouseAffairsTheme(content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalHouseImages provides IosImages) {
        // Settings → Look & sound: the phone's choice, or always light or dark.
        val dark = when (themeMode) { com.houseaffairs.app.core.data.ThemeMode.LIGHT -> false; com.houseaffairs.app.core.data.ThemeMode.DARK -> true; else -> isSystemInDarkTheme() }
        HouseTheme(dark, content = content)
    }

/**
 * Web pictures (a trip stop's photo, a cover) through Coil 3. Family photos live on the phone
 * that took them, so the iPhone shows the plain tile for those.
 */
private object IosImages : HouseImages {
    override val web: Boolean get() = true

    @Composable
    override fun Web(url: String, description: String?, modifier: Modifier) {
        AsyncImage(
            // Wikimedia turns away image requests that don't say which app they're from.
            model = ImageRequest.Builder(LocalPlatformContext.current).data(url)
                .httpHeaders(NetworkHeaders.Builder().set("User-Agent", "HouseAffairs/0.1 (family planner)").build())
                .crossfade(true).build(),
            contentDescription = description,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    }
}
