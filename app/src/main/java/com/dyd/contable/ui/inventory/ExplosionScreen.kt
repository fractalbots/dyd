@file:OptIn(ExperimentalMaterial3Api::class)

package com.dyd.contable.ui.inventory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dyd.contable.data.AccountingRepository
import com.dyd.contable.data.ExplosionRow
import com.dyd.contable.data.ItemKind
import com.dyd.contable.data.ItemWithStock
import com.dyd.contable.ui.AppViewModelProvider
import com.dyd.contable.ui.components.EmptyState
import com.dyd.contable.ui.components.ScreenScaffold
import com.dyd.contable.ui.components.SectionCard
import com.dyd.contable.ui.components.SelectorField
import com.dyd.contable.ui.theme.AmountStyle
import com.dyd.contable.ui.theme.expenseColor
import com.dyd.contable.ui.theme.incomeColor
import com.dyd.contable.util.Money
import com.dyd.contable.util.Quantity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ExplosionViewModel(savedStateHandle: SavedStateHandle, private val repo: AccountingRepository) : ViewModel() {
    val products: StateFlow<List<ItemWithStock>> = repo.items()
        .map { list -> list.filter { it.item.kind == ItemKind.PRODUCT } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var productId by mutableStateOf(savedStateHandle.get<Long>("productId") ?: 0L)
    var quantityText by mutableStateOf("100")
    var rows by mutableStateOf<List<ExplosionRow>>(emptyList())
        private set

    fun calculate() {
        val qty = Quantity.parse(quantityText) ?: 0.0
        if (productId == 0L || qty <= 0) {
            rows = emptyList(); return
        }
        viewModelScope.launch { rows = repo.explode(productId, qty) }
    }
}

@Composable
fun ExplosionScreen(
    onBack: () -> Unit,
    viewModel: ExplosionViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val products by viewModel.products.collectAsStateWithLifecycle()
    val product = products.firstOrNull { it.item.id == viewModel.productId }
    val quantity = Quantity.parse(viewModel.quantityText) ?: 0.0
    LaunchedEffect(viewModel.productId, viewModel.quantityText) { viewModel.calculate() }

    val rows = viewModel.rows
    val materials = rows.sumOf { it.cost }
    val labor = Math.round((product?.item?.laborCost ?: 0) * quantity)
    val overhead = Math.round((product?.item?.overheadCost ?: 0) * quantity)
    val total = materials + labor + overhead
    val toBuy = rows.sumOf { Math.round(it.shortage * it.unitCost) }
    val revenue = Math.round((product?.item?.salePrice ?: 0) * quantity)

    ScreenScaffold(title = "Explosión de materiales", onBack = onBack) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SelectorField(
                    label = "Producto",
                    selected = product,
                    options = products,
                    optionLabel = { it.item.name },
                    onSelect = { viewModel.productId = it?.item?.id ?: 0 },
                    leadingIcon = Icons.Filled.Inventory2,
                )
            }
            item {
                OutlinedTextField(
                    value = viewModel.quantityText,
                    onValueChange = { viewModel.quantityText = it },
                    label = { Text("Cantidad a fabricar" + (product?.let { " (${it.item.unit})" } ?: "")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (product == null || rows.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Filled.AccountTree,
                        if (product == null) "Elige un producto" else "Sin lista de materiales",
                        if (product == null) "Verás los materiales necesarios, lo que tienes y lo que falta comprar."
                        else "Agrega los materiales del producto desde su ficha en Inventario.",
                    )
                }
            } else {
                item {
                    SectionCard(title = "Materiales requeridos") {
                        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row {
                                Header("Material", Modifier.weight(1.6f))
                                Header("Necesita", Modifier.weight(1f))
                                Header("Tiene", Modifier.weight(1f))
                                Header("Falta", Modifier.weight(1f))
                            }
                            rows.forEach { r ->
                                Row {
                                    Column(Modifier.weight(1.6f)) {
                                        Text(r.materialName, style = MaterialTheme.typography.bodyMedium)
                                        Text(Money.format(r.cost), style = MaterialTheme.typography.bodySmall.merge(AmountStyle), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(Quantity.format(r.required, r.unit), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                    Text(Quantity.format(r.available), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                    Text(
                                        if (r.shortage > 0) Quantity.format(r.shortage) else "—",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = if (r.shortage > 0) FontWeight.Bold else null,
                                        color = if (r.shortage > 0) expenseColor() else incomeColor(),
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
                item {
                    SectionCard(title = "Costo del lote") {
                        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Line("Materiales", materials)
                            Line("Mano de obra", labor)
                            Line("Costos indirectos", overhead)
                            HorizontalDivider()
                            Line("Costo total", total, bold = true)
                            if (quantity > 0) Line("Costo por unidad", Math.round(total / quantity))
                            if (revenue > 0) {
                                Line("Venta esperada", revenue)
                                Line("Utilidad esperada", revenue - total, bold = true)
                            }
                            if (toBuy > 0) {
                                HorizontalDivider()
                                Text(
                                    "Para producir este lote debes comprar material por aprox. ${Money.format(toBuy)}.",
                                    color = expenseColor(),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            } else {
                                Text("Tienes todo el material para este lote.", color = incomeColor(), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(text: String, modifier: Modifier) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

@Composable
private fun Line(label: String, amount: Long, bold: Boolean = false) {
    Row {
        Text(label, modifier = Modifier.weight(1f), fontWeight = if (bold) FontWeight.Bold else null)
        Text(Money.format(amount), style = AmountStyle, fontWeight = if (bold) FontWeight.Bold else null)
    }
}
