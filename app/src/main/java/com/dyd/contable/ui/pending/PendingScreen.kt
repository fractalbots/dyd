@file:OptIn(ExperimentalMaterial3Api::class)

package com.dyd.contable.ui.pending

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dyd.contable.data.EntryDetail
import com.dyd.contable.data.EntryType
import com.dyd.contable.ui.AppViewModelProvider
import com.dyd.contable.ui.components.AmountText
import com.dyd.contable.ui.components.CategoryIcons
import com.dyd.contable.ui.components.EmptyState
import com.dyd.contable.ui.components.IconBadge
import com.dyd.contable.ui.components.ScreenScaffold
import com.dyd.contable.ui.components.StatusPill
import com.dyd.contable.ui.theme.AmountStyle
import com.dyd.contable.ui.theme.expenseColor
import com.dyd.contable.ui.theme.incomeColor
import com.dyd.contable.util.Dates
import com.dyd.contable.util.Money
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color

@Composable
fun PendingScreen(
    onNewEntry: (EntryType) -> Unit,
    onOpenEntry: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: PendingViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val isReceivable = tab == 0
    val list = if (isReceivable) state.receivables else state.payables

    ScreenScaffold(
        title = "Pendientes",
        onBack = onBack,
        snackbarHostState = snackbar,
        floatingActionButton = {
            FloatingActionButton(onClick = { onNewEntry(if (isReceivable) EntryType.INCOME else EntryType.EXPENSE) }) {
                Icon(Icons.Filled.Add, contentDescription = "Nuevo pendiente")
            }
        },
    ) { padding ->
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 96.dp)) {
            item {
                PrimaryTabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Por cobrar") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Por pagar") })
                }
            }
            item {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        if (isReceivable) "Tus clientes te deben" else "Debes a proveedores",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        Money.format(if (isReceivable) state.totalReceivable else state.totalPayable),
                        style = MaterialTheme.typography.headlineMedium.merge(AmountStyle),
                        color = if (isReceivable) incomeColor() else expenseColor(),
                    )
                }
            }
            if (list.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Filled.EventAvailable,
                        "¡Todo al día!",
                        if (isReceivable) "No tienes ventas pendientes de cobro."
                        else "No tienes compras pendientes de pago.",
                    )
                }
            }
            items(list, key = { it.entry.id }) { d ->
                PendingRow(
                    d,
                    onClick = { onOpenEntry(d.entry.id) },
                    onMarkPaid = {
                        viewModel.markPaid(d.entry.id)
                        scope.launch {
                            snackbar.showSnackbar(
                                if (isReceivable) "Cobro registrado: ${Money.format(d.entry.amount)}"
                                else "Pago registrado: ${Money.format(d.entry.amount)}"
                            )
                        }
                    },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun PendingRow(d: EntryDetail, onClick: () -> Unit, onMarkPaid: () -> Unit, modifier: Modifier = Modifier) {
    val e = d.entry
    val days = PendingViewModel.daysUntil(e.dueDate)
    val (dueText, dueColor) = when {
        days == null -> "Sin vencimiento" to MaterialTheme.colorScheme.outline
        days < 0 -> "Vencido hace ${-days} día${if (days == -1L) "" else "s"}" to MaterialTheme.colorScheme.error
        days == 0L -> "Vence hoy" to MaterialTheme.colorScheme.error
        days <= 7 -> "Vence en $days día${if (days == 1L) "" else "s"}" to MaterialTheme.colorScheme.tertiary
        else -> "Vence el ${Dates.formatShort(e.dueDate ?: e.date)}" to MaterialTheme.colorScheme.outline
    }
    Column(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(CategoryIcons.of(d.categoryIcon), d.categoryColor?.let { Color(it) } ?: MaterialTheme.colorScheme.outline)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    d.contactName ?: e.description.ifBlank { d.categoryName ?: "Sin detalle" },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val sub = listOfNotNull(
                    e.description.takeIf { it.isNotBlank() && d.contactName != null },
                    e.reference.takeIf { it.isNotBlank() }?.let { "Fact. $it" },
                    Dates.formatShort(e.date),
                ).joinToString(" · ")
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            AmountText(e.amount, e.type)
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 52.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            StatusPill(dueText, dueColor)
            FilledTonalButton(onClick = onMarkPaid) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(if (e.type == EntryType.INCOME) "Cobrado" else "Pagado")
            }
        }
    }
}
