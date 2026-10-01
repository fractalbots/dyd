package com.dyd.contable.ui.inventory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dyd.contable.data.Item
import com.dyd.contable.data.ItemKind
import com.dyd.contable.data.Sri
import com.dyd.contable.ui.components.SelectorField
import com.dyd.contable.util.Money
import com.dyd.contable.util.Quantity

/** Crear o editar un material o producto. */
@Composable
fun ItemDialog(
    initial: Item,
    onDismiss: () -> Unit,
    onSave: (Item, Double) -> Unit,
    canSetStock: Boolean = true,
    canSetPrice: Boolean = true,
) {
    val isNew = initial.id == 0L
    val isProduct = initial.kind == ItemKind.PRODUCT
    var name by remember { mutableStateOf(initial.name) }
    var unit by remember { mutableStateOf(initial.unit) }
    var sku by remember { mutableStateOf(initial.sku) }
    var cost by remember { mutableStateOf(Money.toInput(initial.unitCost)) }
    var price by remember { mutableStateOf(Money.toInput(initial.salePrice)) }
    var labor by remember { mutableStateOf(Money.toInput(initial.laborCost)) }
    var overhead by remember { mutableStateOf(Money.toInput(initial.overheadCost)) }
    var minStock by remember { mutableStateOf(Quantity.toInput(initial.minStock)) }
    var initialStock by remember { mutableStateOf("") }
    var isPublic by remember { mutableStateOf(initial.isPublic) }
    var ivaCode by remember { mutableStateOf(initial.ivaCode) }
    var description by remember { mutableStateOf(initial.description) }
    var dimensions by remember { mutableStateOf(initial.dimensions) }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    isNew && isProduct -> "Nuevo producto"
                    isNew -> "Nuevo material"
                    else -> "Editar ${initial.name}"
                }
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it; error = false }, label = { Text("Nombre") },
                    isError = error, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = unit, onValueChange = { unit = it }, label = { Text("Unidad") },
                        placeholder = { Text("und, kg, m") }, singleLine = true, modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = sku, onValueChange = { sku = it }, label = { Text("Código") },
                        singleLine = true, modifier = Modifier.weight(1f),
                    )
                }
                if (!isProduct) {
                    MoneyField("Costo por unidad", cost) { cost = it }
                }
                if (isProduct) {
                    if (canSetPrice) {
                        MoneyField("Precio de venta", price) { price = it }
                    } else {
                        Text(
                            "Precio de venta: ${if (initial.salePrice > 0) Money.format(initial.salePrice) else "sin asignar"} · lo asigna contabilidad",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    MoneyField("Mano de obra por unidad", labor) { labor = it }
                    MoneyField("Costos indirectos por unidad", overhead, "Energía, desgaste de máquinas, arriendo del taller…") { overhead = it }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = minStock, onValueChange = { minStock = it }, label = { Text("Stock mínimo") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    if (isNew && canSetStock) {
                        OutlinedTextField(
                            value = initialStock, onValueChange = { initialStock = it }, label = { Text("Stock inicial") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                SelectorField(
                    label = "IVA en la factura", selected = ivaCode, options = Sri.IVA_CODES.keys.toList(),
                    optionLabel = { Sri.IVA_CODES[it].orEmpty() }, onSelect = { if (it != null) ivaCode = it },
                )
                if (isProduct) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Mostrar en el catálogo web", style = MaterialTheme.typography.bodyLarge)
                            Text("Tus clientes lo verán con precio y foto.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = isPublic, onCheckedChange = { isPublic = it })
                    }
                    if (isPublic) {
                        OutlinedTextField(
                            value = dimensions, onValueChange = { dimensions = it }, label = { Text("Medidas") },
                            placeholder = { Text("1,20 × 1,00 m") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = description, onValueChange = { description = it }, label = { Text("Descripción para el catálogo") },
                            minLines = 2, modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) {
                    error = true
                } else {
                    onSave(
                        initial.copy(
                            name = name.trim(),
                            unit = unit.trim().ifBlank { "und" },
                            sku = sku.trim(),
                            unitCost = if (isProduct) initial.unitCost else Money.parse(cost) ?: 0,
                            salePrice = if (canSetPrice) Money.parse(price) ?: 0 else initial.salePrice,
                            laborCost = Money.parse(labor) ?: 0,
                            overheadCost = Money.parse(overhead) ?: 0,
                            minStock = Quantity.parse(minStock) ?: 0.0,
                            isPublic = isPublic,
                            description = description.trim(),
                            dimensions = dimensions.trim(),
                            ivaCode = ivaCode,
                        ),
                        Quantity.parse(initialStock) ?: 0.0,
                    )
                }
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
fun MoneyField(label: String, value: String, hint: String? = null, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        supportingText = if (hint != null) { { Text(hint) } } else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}
