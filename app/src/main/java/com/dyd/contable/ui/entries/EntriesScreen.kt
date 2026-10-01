@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.dyd.contable.ui.entries

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dyd.contable.data.EntryType
import com.dyd.contable.ui.AppViewModelProvider
import com.dyd.contable.ui.components.EmptyState
import com.dyd.contable.ui.components.EntryRow
import com.dyd.contable.ui.components.MonthSelector
import com.dyd.contable.ui.components.ScreenScaffold
import com.dyd.contable.ui.theme.AmountStyle
import com.dyd.contable.ui.theme.expenseColor
import com.dyd.contable.ui.theme.incomeColor
import com.dyd.contable.util.Dates
import com.dyd.contable.util.Money

@Composable
fun EntriesScreen(
    onNewEntry: (() -> Unit)?,
    onOpenEntry: (Long) -> Unit,
    viewModel: EntriesViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ScreenScaffold(
        title = "Movimientos",
        floatingActionButton = {
            if (onNewEntry != null) FloatingActionButton(onClick = onNewEntry) { Icon(Icons.Filled.Add, contentDescription = "Nuevo movimiento") }
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 96.dp),
        ) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = viewModel::setQuery,
                        placeholder = { Text("Buscar por descripción, cliente, factura...") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (state.query.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setQuery("") }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Limpiar")
                                }
                            }
                        },
                        singleLine = true,
                        shape = MaterialTheme.shapes.extraLarge,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    MonthSelector(state.month, viewModel::setMonth)
                }
            }
            item {
                val filters = listOf(null to "Todos", EntryType.INCOME to "Ingresos", EntryType.EXPENSE to "Gastos", EntryType.TRANSFER to "Transferencias")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(filters) { (type, label) ->
                        FilterChip(
                            selected = state.filter == type,
                            onClick = { viewModel.setFilter(type) },
                            label = { Text(label) },
                        )
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SummaryTile("Entradas", state.income, incomeColor(), Modifier.weight(1f))
                    SummaryTile("Salidas", state.expense, expenseColor(), Modifier.weight(1f))
                }
            }
            if (state.groups.isEmpty()) {
                item {
                    if (state.isEmptyMonth) {
                        EmptyState(Icons.AutoMirrored.Filled.ReceiptLong, "Sin movimientos este mes", "Registra una venta, un gasto o una transferencia con el botón +.")
                    } else {
                        EmptyState(Icons.Filled.SearchOff, "Sin resultados", "Prueba con otra búsqueda o cambia el filtro.")
                    }
                }
            }
            state.groups.forEach { group ->
                stickyHeader(key = "h-${group.date}") {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                Dates.formatDayHeader(group.date),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                (if (group.net >= 0) "+ " else "− ") + Money.format(kotlin.math.abs(group.net)),
                                style = MaterialTheme.typography.labelMedium.merge(AmountStyle),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                items(group.entries, key = { it.entry.id }) { d ->
                    EntryRow(d, onClick = { onOpenEntry(d.entry.id) }, modifier = Modifier.animateItem())
                }
                item(key = "d-${group.date}") { HorizontalDivider(Modifier.padding(horizontal = 16.dp)) }
            }
        }
    }
}

@Composable
private fun SummaryTile(label: String, amount: Long, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Surface(
        color = color.copy(alpha = 0.10f),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier,
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(Money.format(amount), style = MaterialTheme.typography.titleMedium.merge(AmountStyle), color = color, maxLines = 1)
        }
    }
}
