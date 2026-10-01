@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.dyd.contable.ui.entries

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.East
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dyd.contable.data.ContactType
import com.dyd.contable.data.EntryType
import com.dyd.contable.data.ItemKind
import com.dyd.contable.util.Quantity
import com.dyd.contable.ui.AppViewModelProvider
import com.dyd.contable.ui.LocalProfile
import com.dyd.contable.ui.components.CategoryIcons
import com.dyd.contable.ui.components.ConfirmDialog
import com.dyd.contable.ui.components.DateField
import com.dyd.contable.ui.components.ScreenScaffold
import com.dyd.contable.ui.components.SelectorField
import com.dyd.contable.ui.components.colorFor
import com.dyd.contable.ui.theme.AmountStyle

@Composable
fun EntryEditorScreen(
    onDone: () -> Unit,
    viewModel: EntryEditorViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel.finished) { if (viewModel.finished) onDone() }

    val type = viewModel.type
    val isTransfer = type == EntryType.TRANSFER
    val accent = colorFor(type)

    ScreenScaffold(
        title = if (viewModel.isEditing) "Editar movimiento" else "Nuevo movimiento",
        onBack = onDone,
        actions = {
            if (viewModel.isEditing) {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Eliminar")
                }
            }
        },
    ) { padding ->
        if (viewModel.loaded) Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val allowed = LocalProfile.current?.allowedEntryTypes ?: EntryType.entries
            val types = listOf(EntryType.INCOME to "Ingreso", EntryType.EXPENSE to "Gasto", EntryType.TRANSFER to "Transferencia")
                .filter { it.first in allowed }
            LaunchedEffect(allowed) { if (type !in allowed && allowed.isNotEmpty()) viewModel.changeType(allowed.first()) }
            if (types.size > 1) SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                types.forEachIndexed { index, (t, label) ->
                    SegmentedButton(
                        selected = type == t,
                        onClick = { viewModel.changeType(t) },
                        shape = SegmentedButtonDefaults.itemShape(index, types.size),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = colorFor(t).copy(alpha = 0.18f),
                            activeContentColor = colorFor(t),
                        ),
                    ) { Text(label, maxLines = 1) }
                }
            }

            OutlinedTextField(
                value = viewModel.amountText,
                onValueChange = viewModel::changeAmount,
                label = { Text("Monto") },
                placeholder = { Text("0") },
                textStyle = MaterialTheme.typography.headlineMedium.merge(AmountStyle).copy(color = accent, fontWeight = FontWeight.Bold),
                isError = viewModel.amountError != null,
                supportingText = if (viewModel.amountError != null) { { Text(viewModel.amountError.orEmpty()) } } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            AnimatedVisibility(visible = !isTransfer) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = viewModel.taxText,
                        onValueChange = viewModel::changeTax,
                        label = { Text("IVA incluido (opcional)") },
                        placeholder = { Text("0") },
                        isError = viewModel.taxError != null,
                        supportingText = { Text(viewModel.taxError ?: if (type == EntryType.EXPENSE) "Crédito tributario" else "IVA cobrado") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    FilledTonalButton(onClick = { viewModel.taxFromAmount(15) }) { Text("15 %") }
                }
            }

            OutlinedTextField(
                value = viewModel.description,
                onValueChange = { viewModel.description = it },
                label = { Text(if (isTransfer) "Nota (opcional)" else "Descripción") },
                placeholder = {
                    Text(
                        when (type) {
                            EntryType.INCOME -> "Ej. 50 pallets estándar a Cliente X"
                            EntryType.EXPENSE -> "Ej. Compra de madera de pino"
                            EntryType.TRANSFER -> "Ej. Consignación del efectivo"
                        }
                    )
                },
                leadingIcon = { Icon(Icons.Filled.Description, contentDescription = null) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            AnimatedVisibility(visible = !isTransfer) {
                Column {
                    Text("Categoría", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        categories.filter { it.type == type }.forEach { c ->
                            val selected = viewModel.categoryId == c.id
                            val color = Color(c.color)
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.categoryId = if (selected) null else c.id },
                                label = { Text(c.name) },
                                leadingIcon = {
                                    Icon(
                                        if (selected) Icons.Filled.Check else CategoryIcons.of(c.icon),
                                        contentDescription = null,
                                        tint = color,
                                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = color.copy(alpha = 0.18f)),
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = !isTransfer) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val selectedItem = items.firstOrNull { it.item.id == viewModel.itemId }
                    SelectorField(
                        label = if (type == EntryType.INCOME) "Producto vendido (opcional)" else "Material comprado (opcional)",
                        selected = selectedItem,
                        // En ventas se listan primero los productos; en compras, los materiales.
                        options = items.sortedBy { (it.item.kind == ItemKind.PRODUCT) != (type == EntryType.INCOME) },
                        optionLabel = { it.item.name + " · " + Quantity.format(it.stock, it.item.unit) },
                        onSelect = viewModel::changeItem,
                        leadingIcon = Icons.Filled.Inventory2,
                        allowNone = true,
                        noneLabel = "No afecta el inventario",
                    )
                    if (selectedItem != null) {
                        OutlinedTextField(
                            value = viewModel.quantityText,
                            onValueChange = viewModel::changeQuantity,
                            label = { Text("Cantidad (${selectedItem.item.unit})") },
                            isError = viewModel.quantityError != null,
                            supportingText = {
                                Text(
                                    viewModel.quantityError ?: if (type == EntryType.INCOME)
                                        "Sale del inventario. Disponible: ${Quantity.format(selectedItem.stock, selectedItem.item.unit)}"
                                    else "Entra al inventario y actualiza el costo promedio."
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            SelectorField(
                label = if (isTransfer) "Desde la cuenta" else "Cuenta",
                selected = accounts.firstOrNull { it.id == viewModel.accountId },
                options = accounts,
                optionLabel = { it.name },
                onSelect = { viewModel.changeAccount(it?.id) },
                leadingIcon = Icons.Filled.AccountBalanceWallet,
                isError = viewModel.accountError != null && viewModel.accountId == null,
            )

            AnimatedVisibility(visible = isTransfer) {
                SelectorField(
                    label = "Hacia la cuenta",
                    selected = accounts.firstOrNull { it.id == viewModel.toAccountId },
                    options = accounts.filter { it.id != viewModel.accountId },
                    optionLabel = { it.name },
                    onSelect = { viewModel.changeToAccount(it?.id) },
                    leadingIcon = Icons.Filled.East,
                    isError = viewModel.accountError != null,
                )
            }
            viewModel.accountError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            AnimatedVisibility(visible = !isTransfer) {
                val preferred = if (type == EntryType.INCOME) ContactType.CLIENT else ContactType.SUPPLIER
                SelectorField(
                    label = if (type == EntryType.INCOME) "Cliente (opcional)" else "Proveedor (opcional)",
                    selected = contacts.firstOrNull { it.id == viewModel.contactId },
                    // Se muestran primero los contactos del tipo esperado.
                    options = contacts.sortedBy { it.type != preferred && it.type != ContactType.BOTH },
                    optionLabel = { it.name },
                    onSelect = { viewModel.contactId = it?.id },
                    leadingIcon = Icons.Filled.Person,
                    allowNone = true,
                    noneLabel = "Sin contacto",
                )
            }

            DateField("Fecha", viewModel.date, onChange = { viewModel.date = it })

            OutlinedTextField(
                value = viewModel.reference,
                onValueChange = { viewModel.reference = it },
                label = { Text("N.º de factura o recibo (opcional)") },
                leadingIcon = { Icon(Icons.Filled.Tag, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            AnimatedVisibility(visible = !isTransfer) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (type == EntryType.INCOME) "Ya lo cobré" else "Ya lo pagué",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                if (viewModel.isPaid) "Afecta el saldo de la cuenta hoy mismo."
                                else if (type == EntryType.INCOME) "Quedará en cuentas por cobrar."
                                else "Quedará en cuentas por pagar.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = viewModel.isPaid, onCheckedChange = { viewModel.isPaid = it })
                    }
                    AnimatedVisibility(visible = !viewModel.isPaid) {
                        DateField("Fecha de vencimiento", viewModel.dueDate, onChange = { viewModel.dueDate = it })
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = viewModel::save,
                enabled = !viewModel.saving,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(if (viewModel.isEditing) "Guardar cambios" else "Guardar")
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "¿Eliminar movimiento?",
            message = "Esta acción no se puede deshacer y los saldos se recalcularán.",
            confirmLabel = "Eliminar",
            onConfirm = viewModel::delete,
            onDismiss = { confirmDelete = false },
        )
    }
}
