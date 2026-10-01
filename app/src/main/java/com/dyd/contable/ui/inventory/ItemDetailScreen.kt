@file:OptIn(ExperimentalMaterial3Api::class)

package com.dyd.contable.ui.inventory

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dyd.contable.data.BomLine
import com.dyd.contable.data.BomLineDetail
import com.dyd.contable.data.Item
import com.dyd.contable.data.ItemKind
import com.dyd.contable.data.ItemWithStock
import com.dyd.contable.data.Profile
import com.dyd.contable.data.StockMoveType
import com.dyd.contable.domain.Costing
import com.dyd.contable.domain.UnitCost
import com.dyd.contable.ui.AppViewModelProvider
import com.dyd.contable.ui.components.ConfirmDialog
import com.dyd.contable.ui.components.DateField
import com.dyd.contable.ui.components.IconBadge
import com.dyd.contable.ui.components.ScreenScaffold
import com.dyd.contable.ui.components.SectionCard
import com.dyd.contable.ui.components.SelectorField
import com.dyd.contable.ui.components.StatusPill
import com.dyd.contable.ui.theme.AmountStyle
import com.dyd.contable.ui.theme.expenseColor
import com.dyd.contable.ui.theme.incomeColor
import com.dyd.contable.util.Dates
import com.dyd.contable.util.Money
import com.dyd.contable.util.Quantity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

fun moveTypeLabel(t: StockMoveType) = when (t) {
    StockMoveType.PURCHASE -> "Compra"
    StockMoveType.SALE -> "Venta"
    StockMoveType.PRODUCTION_IN -> "Producción"
    StockMoveType.PRODUCTION_OUT -> "Consumo en producción"
    StockMoveType.ADJUSTMENT -> "Ajuste"
}

@Composable
fun ItemDetailScreen(
    onBack: () -> Unit,
    onPurchase: (Long) -> Unit,
    onExplosion: (Long) -> Unit,
    onOpenItem: (Long) -> Unit,
    profile: Profile?,
    viewModel: ItemDetailViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var editing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var adjusting by remember { mutableStateOf(false) }
    var producing by remember { mutableStateOf(false) }
    var bomEditing by remember { mutableStateOf<BomLine?>(null) }
    fun notify(msg: String) { scope.launch { snackbar.showSnackbar(msg) } }

    val current = state.item
    val item = current?.item
    val isProduct = item?.kind == ItemKind.PRODUCT
    val canEdit = profile?.canEditItems == true
    val canProduce = profile?.canProduce == true
    val canPurchase = profile?.canManageAccounting == true
    val canAdjust = profile?.canAdjustStock == true

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null || item == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            }
            if (bytes == null) {
                notify("No se pudo leer la imagen")
            } else {
                viewModel.uploadImage(item, bytes, "jpg") { ok -> notify(if (ok) "Foto publicada en el catálogo" else "No se pudo subir la foto") }
            }
        }
    }

    ScreenScaffold(
        title = item?.name ?: "Artículo",
        onBack = onBack,
        snackbarHostState = snackbar,
        actions = {
            if (canEdit && item != null) {
                IconButton(onClick = { editing = true }) { Icon(Icons.Filled.Edit, contentDescription = "Editar") }
                IconButton(onClick = { deleting = true }) { Icon(Icons.Filled.Delete, contentDescription = "Eliminar") }
            }
        },
    ) { padding ->
        if (current != null && item != null) LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { StockHeader(current) }

            if (isProduct) {
                state.unitCost?.let { cost -> item { CostCard(cost, item.salePrice) } }
                item {
                    BomCard(
                        bom = state.bom,
                        canEdit = canEdit,
                        onAdd = { bomEditing = BomLine(productId = item.id, materialId = 0, quantity = 1.0) },
                        onEdit = { bomEditing = it.line },
                    )
                }
            } else if (state.usedIn.isNotEmpty()) {
                item {
                    SectionCard(title = "Se usa en") {
                        state.usedIn.forEach { p ->
                            ListItem(
                                modifier = Modifier.clickable { onOpenItem(p.item.id) },
                                leadingContent = { IconBadge(Icons.Filled.Inventory2, MaterialTheme.colorScheme.primary, size = 32.dp) },
                                headlineContent = { Text(p.item.name) },
                                supportingContent = { Text("Stock: ${Quantity.format(p.stock, p.item.unit)}") },
                            )
                        }
                    }
                }
            }

            if (isProduct || canPurchase) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        if (isProduct) {
                            if (canProduce) Button(onClick = { producing = true }, modifier = Modifier.weight(1f), enabled = state.bom.isNotEmpty()) {
                                Icon(Icons.Filled.Factory, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Producir")
                            }
                            FilledTonalButton(onClick = { onExplosion(item.id) }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.AccountTree, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Explosión")
                            }
                        } else if (canPurchase) {
                            Button(onClick = { onPurchase(item.id) }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.AddShoppingCart, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Registrar compra")
                            }
                        }
                    }
                }
            }
            if (canAdjust || (canEdit && isProduct && item.isPublic)) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        if (canAdjust) OutlinedButton(onClick = { adjusting = true }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Tune, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Ajustar stock")
                        }
                        if (canEdit && isProduct && item.isPublic) {
                            OutlinedButton(
                                onClick = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                enabled = !viewModel.working,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Filled.Image, contentDescription = null); Spacer(Modifier.width(6.dp))
                                Text(if (item.imageUrl.isBlank()) "Subir foto" else "Cambiar foto")
                            }
                        }
                    }
                }
            }

            item { Text("Kardex", style = MaterialTheme.typography.titleMedium) }
            if (state.kardex.isEmpty()) {
                item { Text("Sin movimientos todavía.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(state.kardex, key = { it.move.id }) { line ->
                val m = line.move
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(moveTypeLabel(m.type), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            listOf(Dates.formatShort(m.date), m.note).filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            (if (m.quantity > 0) "+" else "") + Quantity.format(m.quantity),
                            color = if (m.quantity > 0) incomeColor() else expenseColor(),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            "Saldo ${Quantity.format(line.balance)} · ${Money.format(m.unitCost)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider(Modifier.padding(top = 8.dp))
            }
        }
    }

    if (editing && item != null) {
        ItemDialog(initial = item, onDismiss = { editing = false }, canSetPrice = profile?.canSetPrices == true, onSave = { saved, _ -> viewModel.save(saved); editing = false })
    }
    if (deleting && item != null) {
        ConfirmDialog(
            title = "¿Eliminar ${item.name}?",
            message = "Si tiene movimientos en el kardex se archivará para conservar el historial.",
            confirmLabel = "Eliminar",
            onConfirm = { viewModel.remove(item) { onBack() } },
            onDismiss = { deleting = false },
        )
    }
    if (adjusting && item != null) {
        AdjustDialog(item, onDismiss = { adjusting = false }) { delta, note ->
            viewModel.adjust(item, delta, note); adjusting = false
        }
    }
    bomEditing?.let { line ->
        BomLineDialog(
            initial = line,
            materials = state.materials,
            onDismiss = { bomEditing = null },
            onSave = { viewModel.saveBomLine(it); bomEditing = null },
            onDelete = if (line.id != 0L) ({ viewModel.deleteBomLine(line.id); bomEditing = null }) else null,
        )
    }
    if (producing && item != null) {
        ProduceDialog(
            item = item,
            viewModel = viewModel,
            onDismiss = { producing = false },
            onProduced = { qty ->
                producing = false
                notify("Producción registrada: ${Quantity.format(qty, item.unit)}")
            },
        )
    }
}

@Composable
private fun StockHeader(i: ItemWithStock) {
    SectionCard {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (i.item.kind == ItemKind.PRODUCT) "Producto terminado" else "Material",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                if (i.item.isPublic) StatusPill("En catálogo", MaterialTheme.colorScheme.primary)
                if (i.isLow) {
                    Spacer(Modifier.width(6.dp))
                    StatusPill("Bajo mínimo", MaterialTheme.colorScheme.error)
                }
            }
            Row {
                Metric("Stock", Quantity.format(i.stock, i.item.unit), Modifier.weight(1f), if (i.stock < 0) expenseColor() else null)
                Metric("Costo unitario", Money.format(i.item.unitCost), Modifier.weight(1f))
                Metric("Valor", Money.format(i.value), Modifier.weight(1f))
            }
            if (i.item.minStock > 0 || i.item.sku.isNotBlank()) {
                Text(
                    listOfNotNull(
                        i.item.sku.takeIf { it.isNotBlank() }?.let { "Código $it" },
                        if (i.item.minStock > 0) "Mínimo ${Quantity.format(i.item.minStock, i.item.unit)}" else null,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier, color: Color? = null) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium.merge(AmountStyle), color = color ?: MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

@Composable
private fun CostCard(cost: UnitCost, price: Long) {
    SectionCard(title = "Costo de fabricación por unidad") {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CostLine("Materiales", cost.materials)
            CostLine("Mano de obra", cost.labor)
            CostLine("Costos indirectos", cost.overhead)
            HorizontalDivider()
            CostLine("Costo total", cost.total, bold = true)
            if (price > 0) {
                CostLine("Precio de venta", price)
                val profit = cost.profitAt(price)
                Row {
                    Text("Utilidad por unidad", modifier = Modifier.weight(1f))
                    Text(
                        Money.format(profit) + (cost.marginAt(price)?.let { " (" + String.format(Locale.getDefault(), "%.1f", it) + " %)" } ?: ""),
                        color = if (profit >= 0) incomeColor() else expenseColor(),
                        fontWeight = FontWeight.SemiBold,
                        style = AmountStyle,
                    )
                }
            } else if (cost.total > 0) {
                Text(
                    "Sin precio de venta. Para ganar un 30 % deberías vender a ${Money.format(cost.priceForMargin(30.0) ?: 0)}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CostLine(label: String, amount: Long, bold: Boolean = false) {
    Row {
        Text(label, modifier = Modifier.weight(1f), fontWeight = if (bold) FontWeight.Bold else null)
        Text(Money.format(amount), style = AmountStyle, fontWeight = if (bold) FontWeight.Bold else null)
    }
}

@Composable
private fun BomCard(bom: List<BomLineDetail>, canEdit: Boolean, onAdd: () -> Unit, onEdit: (BomLineDetail) -> Unit) {
    SectionCard(
        title = "Lista de materiales (por unidad)",
        action = {
            if (canEdit) IconButton(onClick = onAdd) { Icon(Icons.Filled.Add, contentDescription = "Agregar material") }
        },
    ) {
        if (bom.isEmpty()) {
            Text(
                "Agrega los materiales que lleva una unidad para calcular el costo y poder producir.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        bom.forEach { b ->
            val qty = Costing.consumption(b.line.quantity, b.line.wastePercent, 1.0)
            ListItem(
                modifier = if (canEdit) Modifier.clickable { onEdit(b) } else Modifier,
                headlineContent = { Text(b.materialName) },
                supportingContent = {
                    Text(
                        Quantity.format(b.line.quantity, b.unit) +
                            (if (b.line.wastePercent > 0) " + ${Quantity.format(b.line.wastePercent)} % desperdicio" else "") +
                            " · ${Money.format(b.materialCost)}/${b.unit}"
                    )
                },
                trailingContent = { Text(Money.format(Math.round(qty * b.materialCost)), style = AmountStyle) },
            )
        }
    }
}

@Composable
private fun AdjustDialog(item: Item, onDismiss: () -> Unit, onSave: (Double, String) -> Unit) {
    var inbound by remember { mutableStateOf(true) }
    var qty by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajustar stock") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(selected = inbound, onClick = { inbound = true }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Entrada") }
                    SegmentedButton(selected = !inbound, onClick = { inbound = false }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Salida") }
                }
                OutlinedTextField(
                    value = qty, onValueChange = { qty = it }, label = { Text("Cantidad (${item.unit})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = note, onValueChange = { note = it }, label = { Text("Motivo") },
                    placeholder = { Text("Conteo físico, daño, merma…") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val q = Quantity.parse(qty) ?: return@TextButton
                if (q > 0) onSave(if (inbound) q else -q, note.trim())
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun BomLineDialog(
    initial: BomLine,
    materials: List<ItemWithStock>,
    onDismiss: () -> Unit,
    onSave: (BomLine) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var materialId by remember { mutableStateOf(initial.materialId) }
    var qty by remember { mutableStateOf(Quantity.toInput(initial.quantity)) }
    var waste by remember { mutableStateOf(Quantity.toInput(initial.wastePercent)) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "Agregar material" else "Editar material") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SelectorField(
                    label = "Material",
                    selected = materials.firstOrNull { it.item.id == materialId },
                    options = materials,
                    optionLabel = { it.item.name },
                    onSelect = { materialId = it?.item?.id ?: 0 },
                )
                val unit = materials.firstOrNull { it.item.id == materialId }?.item?.unit ?: "und"
                OutlinedTextField(
                    value = qty, onValueChange = { qty = it }, label = { Text("Cantidad por unidad ($unit)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = waste, onValueChange = { waste = it }, label = { Text("Desperdicio (%)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val q = Quantity.parse(qty)
                when {
                    materialId == 0L -> error = "Elige un material"
                    q == null || q <= 0 -> error = "Escribe una cantidad mayor que cero"
                    else -> onSave(initial.copy(materialId = materialId, quantity = q, wastePercent = Quantity.parse(waste) ?: 0.0))
                }
            }) { Text("Guardar") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Quitar", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}

@Composable
private fun ProduceDialog(item: Item, viewModel: ItemDetailViewModel, onDismiss: () -> Unit, onProduced: (Double) -> Unit) {
    var qty by remember { mutableStateOf("10") }
    var date by remember { mutableStateOf(Dates.toMillis(Dates.today())) }
    var note by remember { mutableStateOf("") }
    val quantity = Quantity.parse(qty) ?: 0.0
    LaunchedEffect(quantity) { viewModel.loadPreview(quantity) }
    val rows = viewModel.preview
    val shortage = rows.any { it.shortage > 0 }
    val materialCost = rows.sumOf { it.cost }
    val total = materialCost + Math.round(item.laborCost * quantity) + Math.round(item.overheadCost * quantity)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Producir ${item.name}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = qty, onValueChange = { qty = it }, label = { Text("Cantidad a fabricar (${item.unit})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                DateField("Fecha", date, onChange = { date = it })
                OutlinedTextField(
                    value = note, onValueChange = { note = it }, label = { Text("Nota (lote, pedido…)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Text("Materiales que se consumirán", style = MaterialTheme.typography.labelLarge)
                rows.forEach { r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (r.shortage > 0) {
                            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(r.materialName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(
                            Quantity.format(r.required, r.unit) + if (r.shortage > 0) " (faltan ${Quantity.format(r.shortage)})" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (r.shortage > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider()
                CostLine("Materiales", materialCost)
                CostLine("Costo total del lote", total, bold = true)
                if (quantity > 0) CostLine("Costo por unidad", Math.round(total / quantity))
                if (shortage) {
                    Text(
                        "No hay material suficiente. Registra la compra o ajusta el stock antes de producir.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = quantity > 0 && !shortage && rows.isNotEmpty() && !viewModel.working,
                onClick = { viewModel.produce(quantity, date, note.trim()) { ok -> if (ok) onProduced(quantity) } },
            ) { Text("Producir") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
