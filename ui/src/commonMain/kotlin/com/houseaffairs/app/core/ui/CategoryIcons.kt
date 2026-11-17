package com.houseaffairs.app.core.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Weekend
import androidx.compose.ui.graphics.vector.ImageVector

fun categoryIcon(category: String): ImageVector = when (category) {
    "Rent" -> Icons.Outlined.Home
    "Groceries" -> Icons.Outlined.ShoppingCart
    "Dining" -> Icons.Outlined.Restaurant
    "Transport" -> Icons.Outlined.DirectionsCar
    "Bills" -> Icons.Outlined.Receipt
    "Health" -> Icons.Outlined.LocalHospital
    "Shopping" -> Icons.Outlined.ShoppingBag
    "Entertainment" -> Icons.Outlined.Movie
    "Home" -> Icons.Outlined.Weekend
    "Travel" -> Icons.Outlined.Flight
    "Gifts" -> Icons.Outlined.CardGiftcard
    else -> Icons.Outlined.MoreHoriz
}
