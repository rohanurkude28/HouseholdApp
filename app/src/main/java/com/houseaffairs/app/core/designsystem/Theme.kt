package com.houseaffairs.app.core.designsystem

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.houseaffairs.app.core.data.ThemeMode

/** Brand colours in light and dark; the user's theme choice wins over the system's. */
@Composable
fun HouseAffairsTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    // enableEdgeToEdge follows the system theme; correct the bar icons when the app overrides it.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    // Shared screens draw pictures through this: Coil for web images and the family's photos.
    androidx.compose.runtime.CompositionLocalProvider(
        com.houseaffairs.app.core.ui.LocalHouseImages provides AndroidImages(view.context),
        com.houseaffairs.app.feature.add.LocalFormHelpers provides AndroidFormHelpers,
    ) {
        HouseTheme(dark, AppTypography, content)
    }
}

/** Coil, with the app's User-Agent (Wikimedia refuses Coil's default), and PhotoStore for family photos. */
private class AndroidImages(private val context: android.content.Context) : com.houseaffairs.app.core.ui.HouseImages {
    override val web: Boolean get() = true
    override fun hasPhoto(id: String): Boolean = com.houseaffairs.app.core.photo.PhotoStore.file(context, id).exists()

    @Composable
    override fun Web(url: String, description: String?, modifier: androidx.compose.ui.Modifier) {
        coil.compose.AsyncImage(
            model = coil.request.ImageRequest.Builder(context).data(url).addHeader("User-Agent", "HouseAffairs/0.1 (family planner)").crossfade(true).build(),
            contentDescription = description,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = modifier,
        )
    }

    @Composable
    override fun Photo(id: String, modifier: androidx.compose.ui.Modifier) {
        coil.compose.AsyncImage(
            model = com.houseaffairs.app.core.photo.PhotoStore.file(context, id), contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = modifier,
        )
    }
}

/** For screens that draw a dark hero behind the status bar; pass false once it scrolls away. */
@Composable
fun LightStatusBarIcons(enabled: Boolean = true) {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(view, enabled) {
        val controller = WindowCompat.getInsetsController((view.context as Activity).window, view)
        val previous = controller.isAppearanceLightStatusBars
        if (enabled) controller.isAppearanceLightStatusBars = false
        onDispose { controller.isAppearanceLightStatusBars = previous }
    }
}


/** Forms' phone parts on Android: the clock, every currency, contacts and the photo editor. */
private object AndroidFormHelpers : com.houseaffairs.app.feature.add.FormHelpers {
    override fun hourNow(): Int = java.time.LocalTime.now().hour

    @Composable
    override fun ContactButton(onChange: (Any?) -> Unit) {
        val context = androidx.compose.ui.platform.LocalContext.current
        // The picked row is readable without the contacts permission.
        val launcher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
            val uri = result.data?.data ?: return@rememberLauncherForActivityResult
            context.contentResolver.query(uri, arrayOf(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)?.use { c ->
                if (c.moveToFirst()) onChange(c.getString(0))
            }
        }
        androidx.compose.material3.IconButton(onClick = {
            launcher.launch(android.content.Intent(android.content.Intent.ACTION_PICK, android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
        }) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.Contacts, contentDescription = "Pick from contacts") }
    }

    @Composable
    override fun CurrencyPicker(selected: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) =
        com.houseaffairs.app.core.ui.CurrencyPickerDialog(selected, onSelect, onDismiss)

    @Composable
    override fun Photos(ids: List<String>, onChange: (Any?) -> Unit) = com.houseaffairs.app.core.photo.PhotosEditor(ids, onChange)
}
