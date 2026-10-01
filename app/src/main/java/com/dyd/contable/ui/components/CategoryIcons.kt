package com.dyd.contable.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Hardware
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector

/** Íconos disponibles para las categorías, guardados en la base por su clave. */
object CategoryIcons {
    val all: List<Pair<String, ImageVector>> = listOf(
        "pallet" to Icons.Filled.Inventory2,
        "build" to Icons.Filled.Build,
        "truck" to Icons.Filled.LocalShipping,
        "savings" to Icons.Filled.Savings,
        "forest" to Icons.Filled.Forest,
        "hardware" to Icons.Filled.Hardware,
        "people" to Icons.Filled.People,
        "home" to Icons.Filled.Home,
        "bolt" to Icons.Filled.Bolt,
        "gavel" to Icons.Filled.Gavel,
        "store" to Icons.Filled.Store,
        "payments" to Icons.Filled.Payments,
        "fuel" to Icons.Filled.LocalGasStation,
        "shopping" to Icons.Filled.ShoppingCart,
        "receipt" to Icons.Filled.Receipt,
        "restaurant" to Icons.Filled.Restaurant,
        "health" to Icons.Filled.LocalHospital,
        "phone" to Icons.Filled.Phone,
        "wifi" to Icons.Filled.Wifi,
        "work" to Icons.Filled.Work,
        "label" to Icons.AutoMirrored.Filled.Label,
    )

    private val byKey = all.toMap()

    fun of(key: String?): ImageVector = byKey[key] ?: Icons.AutoMirrored.Filled.Label

    /** Colores sugeridos al crear categorías o cuentas. */
    val palette: List<Long> = listOf(
        0xFF2E7D32, 0xFF00897B, 0xFF0277BD, 0xFF1565C0, 0xFF5E35B1, 0xFF8E24AA,
        0xFFC62828, 0xFFEF6C00, 0xFFF9A825, 0xFF7CB342, 0xFF8D6E63, 0xFF546E7A,
    )
}
