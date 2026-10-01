@file:OptIn(ExperimentalMaterial3Api::class)

package com.dyd.contable.ui.inventory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dyd.contable.data.Item
import com.dyd.contable.data.ItemKind
import com.dyd.contable.data.ItemWithStock
import com.dyd.contable.data.ProductionDetail
import com.dyd.contable.data.Profile
import com.dyd.contable.data.QualityStatus
import com.dyd.contable.ui.theme.expenseColor
import com.dyd.contable.ui.theme.incomeColor
import com.dyd.contable.ui.AppViewModelProvider
import com.dyd.contable.ui.components.EmptyState
import com.dyd.contable.ui.components.IconBadge
import com.dyd.contable.ui.components.ScreenScaffold
import com.dyd.contable.ui.components.StatusPill
import com.dyd.contable.ui.theme.AmountStyle
import com.dyd.contable.util.Dates
import com.dyd.contable.util.Money
import com.dyd.contable.util.Quantity

@Composable
fun InventoryScreen(
    onOpenItem: (Long) -> Unit,
    onExplosion: () -> Unit,
    profile: Profile?,
    viewModel: InventoryViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var creating by remember { mutableStateOf<Item?>(null) }
    var inspecting by remember { mutableStateOf<ProductionDetail?>(null) }
    val canEdit = profile?.canEditItems == true
    val canInspect = profile?.canInspect == true

    ScreenScaffold(
        title = "Inventario",
        actions = {
            IconButton(onClick = onExplosion) { Icon(Icons.Filled.AccountTree, contentDescription = "Explosión de materiales") }
            IconButton(onClick = viewModel::refresh) { Icon(Icons.Filled.Refresh, contentDescription = "Actualizar") }
        },
        floatingActionButton = {
            if (canEdit && tab < 2) {
                FloatingActionButton(onClick = {
                    creating = Item(name = "", kind = if (tab == 0) ItemKind.PRODUCT else ItemKind.MATERIAL)
                }) { Icon(Icons.Filled.Add, contentDescription = "Nuevo artículo") }
            }
        },
    ) { padding ->
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 96.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.weight(1f)) {
                        Column(Modifier.padding(14.dp)) {
                            Text("Valor del inventario", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text(Money.format(state.totalValue), style = MaterialTheme.typography.titleLarge.merge(AmountStyle), color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    Surface(
                        color = if (state.lowCount > 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text("Bajo el mínimo", style = MaterialTheme.typography.labelMedium)
                            Text("${state.lowCount} artículo${if (state.lowCount == 1) "" else "s"}", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
            item {
                PrimaryTabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Productos") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Materiales") })
                    Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Producción") })
                }
            }
            when (tab) {
                0, 1 -> {
                    val list = if (tab == 0) state.products else state.materials
                    if (list.isEmpty() && !state.loading) {
                        item {
                            EmptyState(
                                if (tab == 0) Icons.Filled.Inventory2 else Icons.Filled.Forest,
                                if (tab == 0) "Sin productos" else "Sin materiales",
                                if (tab == 0) "Crea tus pallets y define de qué materiales están hechos."
                                else "Registra la madera, tacos, clavos y demás insumos.",
                            )
                        }
                    }
                    items(list, key = { it.item.id }) { ItemRow(it, onClick = { onOpenItem(it.item.id) }) }
                }
                else -> {
                    if (state.production.isEmpty() && !state.loading) {
                        item {
                            EmptyState(Icons.Filled.Factory, "Sin órdenes de producción", "Abre un producto y toca \"Producir\" para fabricar un lote.")
                        }
                    }
                    items(state.production, key = { it.order.id }) { p ->
                        ListItem(
                            modifier = Modifier.clickable { if (canInspect) inspecting = p else onOpenItem(p.order.productId) },
                            leadingContent = { IconBadge(Icons.Filled.Factory, MaterialTheme.colorScheme.tertiary) },
                            headlineContent = { Text("${Quantity.format(p.order.quantity, p.unit)} · ${p.productName}") },
                            supportingContent = {
                                Column {
                                    Text("${Dates.formatShort(p.order.date)} · costo unitario ${Money.format(p.order.unitCost)}" +
                                        if (p.order.note.isNotBlank()) " · ${p.order.note}" else "")
                                    QualityPill(p.order.qualityStatus, p.order.qualityNote)
                                }
                            },
                            trailingContent = { Text(Money.format(p.order.totalCost), style = MaterialTheme.typography.titleSmall.merge(AmountStyle)) },
                        )
                    }
                }
            }
        }
    }

    creating?.let { item ->
        ItemDialog(
            initial = item,
            onDismiss = { creating = null },
            canSetStock = profile?.canAdjustStock == true,
            canSetPrice = profile?.canSetPrices == true,
            onSave = { saved, initialStock ->
                viewModel.create(saved, if (profile?.canAdjustStock == true) initialStock else 0.0); creating = null
            },
        )
    }
    inspecting?.let { p ->
        InspectionDialog(
            order = p,
            onDismiss = { inspecting = null },
            onSave = { status, note -> viewModel.inspect(p.order.id, status, note); inspecting = null },
        )
    }
}

fun qualityLabel(q: QualityStatus) = when (q) {
    QualityStatus.PENDIENTE -> "Calidad pendiente"
    QualityStatus.APROBADO -> "Aprobado por calidad"
    QualityStatus.RECHAZADO -> "Rechazado por calidad"
}

@Composable
fun QualityPill(status: QualityStatus, note: String) {
    val color = when (status) {
        QualityStatus.PENDIENTE -> MaterialTheme.colorScheme.onSurfaceVariant
        QualityStatus.APROBADO -> incomeColor()
        QualityStatus.RECHAZADO -> expenseColor()
    }
    StatusPill(qualityLabel(status) + if (note.isNotBlank()) " · $note" else "", color)
}

/** Control de calidad: aprobar o rechazar un lote producido. */
@Composable
private fun InspectionDialog(order: ProductionDetail, onDismiss: () -> Unit, onSave: (QualityStatus, String) -> Unit) {
    var status by remember { mutableStateOf(order.order.qualityStatus) }
    var note by remember { mutableStateOf(order.order.qualityNote) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Control de calidad") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${Quantity.format(order.order.quantity, order.unit)} · ${order.productName} · ${Dates.formatShort(order.order.date)}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QualityStatus.entries.forEach { q ->
                        FilterChip(
                            selected = status == q,
                            onClick = { status = q },
                            label = { Text(q.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
                OutlinedTextField(
                    note, { note = it },
                    label = { Text("Observaciones") },
                    placeholder = { Text("Ej.: humedad, astillas, medidas") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(status, note.trim()) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
fun ItemRow(i: ItemWithStock, onClick: () -> Unit) {
    val isProduct = i.item.kind == ItemKind.PRODUCT
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            IconBadge(
                if (isProduct) Icons.Filled.Inventory2 else Icons.Filled.Forest,
                if (isProduct) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
            )
        },
        headlineContent = { Text(i.item.name) },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Costo ${Money.format(i.item.unitCost)}/${i.item.unit}")
                if (i.isLow) StatusPill("Bajo mínimo", MaterialTheme.colorScheme.error)
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (i.stock < 0) Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text(Quantity.format(i.stock, i.item.unit), style = MaterialTheme.typography.titleSmall)
                }
                Text(Money.format(i.value), style = MaterialTheme.typography.bodySmall.merge(AmountStyle), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}
