package com.dyd.contable.ui.invoices

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dyd.contable.data.AccountingRepository
import com.dyd.contable.data.Invoice
import com.dyd.contable.data.InvoiceStatus
import com.dyd.contable.ui.AppViewModelProvider
import com.dyd.contable.ui.LocalProfile
import com.dyd.contable.ui.components.EmptyState
import com.dyd.contable.ui.components.IconBadge
import com.dyd.contable.ui.components.ScreenScaffold
import com.dyd.contable.ui.components.StatusPill
import com.dyd.contable.ui.theme.AmountStyle
import com.dyd.contable.ui.theme.expenseColor
import com.dyd.contable.ui.theme.incomeColor
import com.dyd.contable.util.Dates
import com.dyd.contable.util.Money
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class InvoicesViewModel(private val repo: AccountingRepository) : ViewModel() {
    val invoices: StateFlow<List<Invoice>?> =
        repo.invoices().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun refresh() = repo.refresh()
}

@Composable
fun statusColor(s: InvoiceStatus): Color = when (s) {
    InvoiceStatus.AUTORIZADA -> incomeColor()
    InvoiceStatus.DEVUELTA, InvoiceStatus.NO_AUTORIZADA -> expenseColor()
    InvoiceStatus.ANULADA -> MaterialTheme.colorScheme.outline
    else -> MaterialTheme.colorScheme.tertiary
}

@Composable
fun InvoicesScreen(
    onBack: () -> Unit,
    onNew: () -> Unit,
    onOpen: (Long) -> Unit,
    viewModel: InvoicesViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val invoices by viewModel.invoices.collectAsStateWithLifecycle()
    val canInvoice = LocalProfile.current?.canInvoice == true

    ScreenScaffold(
        title = "Facturación electrónica",
        onBack = onBack,
        actions = { IconButton(onClick = viewModel::refresh) { Icon(Icons.Filled.Refresh, contentDescription = "Actualizar") } },
        floatingActionButton = {
            if (canInvoice) {
                ExtendedFloatingActionButton(
                    onClick = onNew,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Nueva factura") },
                )
            }
        },
    ) { padding ->
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 96.dp)) {
            val list = invoices.orEmpty()
            if (invoices != null && list.isEmpty()) {
                item {
                    EmptyState(
                        Icons.AutoMirrored.Filled.ReceiptLong, "Sin facturas todavía",
                        "Emite tu primera factura electrónica: se numera, se firma y se envía al SRI desde aquí.",
                    )
                }
            }
            items(list, key = { it.id }) { inv ->
                ListItem(
                    modifier = Modifier.clickable { onOpen(inv.id) },
                    leadingContent = { IconBadge(Icons.AutoMirrored.Filled.ReceiptLong, statusColor(inv.status)) },
                    headlineContent = { Text(inv.buyerName) },
                    supportingContent = {
                        androidx.compose.foundation.layout.Column {
                            Text("${inv.number} · ${Dates.formatShort(inv.date)}")
                            StatusPill(inv.status.label + if (inv.ambiente == 1) " · pruebas" else "", statusColor(inv.status))
                        }
                    },
                    trailingContent = {
                        Text(Money.format(inv.total), style = MaterialTheme.typography.titleSmall.merge(AmountStyle), modifier = Modifier.padding(start = 8.dp))
                    },
                )
            }
        }
    }
}
