package com.houseaffairs.app.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RestaurantMenu
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
            if (idea.fit.canCook) "You have everything" else "You have ${idea.fit.have} of ${idea.fit.total}",
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
