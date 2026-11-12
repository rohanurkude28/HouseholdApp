package com.houseaffairs.app.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.houseaffairs.app.core.data.DishIdea
import com.houseaffairs.app.core.data.Recipe
import com.houseaffairs.app.core.designsystem.LocalHouseColors
import com.houseaffairs.app.feature.add.FormValues

/** A dish suggestion: what it rescues from the pantry and how much of it you already have. */
@Composable
fun DishCard(idea: DishIdea, onClick: () -> Unit, modifier: Modifier = Modifier, why: String? = null) {
    val stock = LocalHouseColors.current.stock
    HaCard(modifier.width(220.dp), onClick = onClick) {
        IconBlob(Icons.Outlined.RestaurantMenu, stock, size = 40.dp)
        Spacer(Modifier.height(10.dp))
        Text(idea.recipe.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        why?.let { Text("✨ $it", style = MaterialTheme.typography.bodySmall, color = stock.color, maxLines = 2) }
        if (idea.rescues.isNotEmpty()) {
            Text("Uses ${idea.rescues.joinToString()} before it expires", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 2)
        }
        com.houseaffairs.app.core.data.lastMadeText(idea.lastMade, java.time.LocalDate.now())?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            idea.fit.summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(if (idea.saved) "Cook it →" else "Save recipe →", style = MaterialTheme.typography.labelLarge, color = stock.color)
    }
}

/** Form values that open the recipe form pre-filled from a library dish. */
fun Recipe.asRecipePreset(): FormValues = mapOf(
    "title" to title,
    "ingredients" to ingredients,
    "servings" to servings.toString(),
    "mood" to mood,
)

/**
 * A dish up close: every ingredient with ✓ (you have enough) or ✗ (missing or not enough), how much
 * the pantry has, and one tap to put what's missing on the shopping list.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DishSheet(
    idea: DishIdea,
    pantry: List<com.houseaffairs.app.core.data.PantryStock>,
    onCook: (() -> Unit)?,
    onSave: (() -> Unit)?,
    onAddMissing: (List<String>) -> Unit,
    onDismiss: () -> Unit,
    /** A saved recipe: change its ingredients, photo or link. */
    onEdit: (() -> Unit)? = null,
    /** A saved recipe: delete it (the snackbar offers Undo). */
    onDelete: (() -> Unit)? = null,
) {
    val stock = LocalHouseColors.current.stock
    val checks = androidx.compose.runtime.remember(idea, pantry) { com.houseaffairs.app.core.data.checkIngredients(idea.recipe.ingredients, pantry) }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        // Scrolls, so a long ingredient list never pushes the buttons out of reach.
        androidx.compose.foundation.layout.Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(idea.recipe.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                onEdit?.let { edit -> androidx.compose.material3.IconButton(onClick = { onDismiss(); edit() }) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.Edit, contentDescription = "Edit recipe") } }
                onDelete?.let { del ->
                    androidx.compose.material3.IconButton(onClick = { onDismiss(); del() }) {
                        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.DeleteOutline, contentDescription = "Delete recipe", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Text(
                idea.fit.summary + " · for ${idea.recipe.servings} " + if (idea.recipe.servings == 1) "person" else "people",
                style = MaterialTheme.typography.bodyMedium,
                color = if (idea.fit.canCook) stock.color else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            checks.forEach { c ->
                androidx.compose.foundation.layout.Row(Modifier.padding(vertical = 6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(if (c.have) "✅" else "❌", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.width(12.dp))
                    androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                        Text(c.name + (c.need?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            when {
                                c.inPantry == null -> "Not in the pantry"
                                c.have -> "You have ${c.inPantry}"
                                else -> "Only ${c.inPantry} left"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (c.have) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            if (checks.isEmpty()) Text("No ingredients saved for this dish yet.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            val missing = checks.filter { !it.have }.map { it.name }
            if (missing.isNotEmpty()) {
                androidx.compose.material3.OutlinedButton(
                    onClick = { onAddMissing(missing); onDismiss() },
                    shape = androidx.compose.foundation.shape.CircleShape,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Add missing to shopping list (${missing.size})") }
                Spacer(Modifier.height(8.dp))
            }
            when {
                // Only with everything at home: short of something, the button above adds it to shopping.
                onCook != null && missing.isEmpty() -> androidx.compose.material3.Button(onClick = { onDismiss(); onCook() }, shape = androidx.compose.foundation.shape.CircleShape, modifier = Modifier.fillMaxWidth()) {
                    Text("Cook it")
                }
                onSave != null -> androidx.compose.material3.Button(onClick = { onDismiss(); onSave() }, shape = androidx.compose.foundation.shape.CircleShape, modifier = Modifier.fillMaxWidth()) {
                    Text("Save to my recipes")
                }
            }

        }
    }
}
