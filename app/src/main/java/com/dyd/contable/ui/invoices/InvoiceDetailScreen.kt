package com.dyd.contable.ui.invoices

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dyd.contable.data.AccountingRepository
import com.dyd.contable.data.InvoiceFull
import com.dyd.contable.data.InvoiceStatus
import com.dyd.contable.data.Sri
import com.dyd.contable.data.SriMessage
import com.dyd.contable.ui.AppViewModelProvider
import com.dyd.contable.ui.LocalProfile
import com.dyd.contable.ui.components.ConfirmDialog
import com.dyd.contable.ui.components.ScreenScaffold
import com.dyd.contable.ui.components.SectionCard
import com.dyd.contable.ui.components.StatusPill
import com.dyd.contable.ui.theme.AmountStyle
import com.dyd.contable.util.Dates
import com.dyd.contable.util.Money
import com.dyd.contable.util.Quantity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class InvoiceDetailViewModel(savedStateHandle: SavedStateHandle, private val repo: AccountingRepository) : ViewModel() {
    private val id: Long = savedStateHandle.get<Long>("id") ?: 0L
    val invoice: StateFlow<InvoiceFull?> = repo.invoice(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    var sending by mutableStateOf(false)
        private set
    /** Resultado del último envío para mostrarlo en pantalla. */
    var lastError by mutableStateOf<String?>(null)
        private set

    fun send(onResult: (String) -> Unit) {
        viewModelScope.launch {
            sending = true
            val r = repo.sendToSri(id)
            sending = false
            lastError = r.error
            onResult(
                when {
                    r.error != null -> r.error
                    r.status == InvoiceStatus.AUTORIZADA -> "¡Factura autorizada por el SRI!"
                    r.pending -> "El SRI la recibió y aún la está procesando. Consulta en un momento."
                    else -> r.status?.label ?: "Respuesta del SRI recibida"
                }
            )
        }
    }

    fun void(onDone: () -> Unit) {
        viewModelScope.launch { if (repo.voidInvoice(id)) onDone() }
    }
}

@Composable
fun InvoiceDetailScreen(
    onBack: () -> Unit,
    viewModel: InvoiceDetailViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val full by viewModel.invoice.collectAsStateWithLifecycle()
    val profile = LocalProfile.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var confirmVoid by remember { mutableStateOf(false) }
    val inv = full?.invoice

    ScreenScaffold(
        title = inv?.number ?: "Factura",
        onBack = onBack,
        snackbarHostState = snackbar,
        actions = {
            if (inv != null) {
                IconButton(onClick = {
                    val text = buildString {
                        appendLine("Factura ${inv.number} · ${Dates.formatShort(inv.date)}")
                        appendLine("Cliente: ${inv.buyerName} (${inv.buyerId})")
                        appendLine("Total: ${Money.format(inv.total)}")
                        appendLine("Estado SRI: ${inv.status.label}")
                        inv.authorizationNumber?.let { appendLine("Autorización: $it") }
                        append("Clave de acceso: ${inv.claveAcceso}")
                    }
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Compartir factura"))
                }) { Icon(Icons.Filled.Share, contentDescription = "Compartir") }
                if (profile?.canManageAccounting == true && inv.status != InvoiceStatus.ANULADA) {
                    IconButton(onClick = { confirmVoid = true }) { Icon(Icons.Filled.Block, contentDescription = "Anular") }
                }
            }
        },
    ) { padding ->
        val f = full
        if (f != null) LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val i = f.invoice
            item {
                SectionCard(title = "Estado en el SRI") {
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusPill(i.status.label + if (i.ambiente == 1) " · ambiente de pruebas" else "", statusColor(i.status))
                        i.authorizationNumber?.let { SelectionContainer { Text("Autorización: $it", style = MaterialTheme.typography.bodySmall) } }
                        SelectionContainer { Text("Clave de acceso: ${i.claveAcceso}", style = MaterialTheme.typography.bodySmall) }
                        i.messages.forEach { MessageText(it) }
                        viewModel.lastError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        val canSend = profile?.canInvoice == true && i.status !in listOf(InvoiceStatus.AUTORIZADA, InvoiceStatus.ANULADA)
                        if (canSend) {
                            Button(
                                onClick = { viewModel.send { msg -> scope.launch { snackbar.showSnackbar(msg) } } },
                                enabled = !viewModel.sending, modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (viewModel.sending) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(end = 8.dp))
                                else Icon(Icons.Filled.CloudUpload, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (i.status == InvoiceStatus.RECIBIDA) "Consultar autorización" else "Firmar y enviar al SRI")
                            }
                        }
                    }
                }
            }
            item {
                SectionCard(title = "Cliente") {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Text(i.buyerName, style = MaterialTheme.typography.titleMedium)
                        Text(i.buyerId, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (i.buyerEmail.isNotBlank()) Text(i.buyerEmail, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                SectionCard(title = "Detalle") {
                    f.lines.forEach { l ->
                        ListItem(
                            headlineContent = { Text(l.description) },
                            supportingContent = {
                                Text("${Quantity.format(l.quantity)} × ${Money.format(l.unitPrice)}" +
                                    (if (l.discount > 0) " − ${Money.format(l.discount)}" else "") + " · ${Sri.IVA_CODES[l.ivaCode]}")
                            },
                            trailingContent = { Text(Money.format(l.subtotal), style = MaterialTheme.typography.titleSmall.merge(AmountStyle)) },
                        )
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Amount("Subtotal sin impuestos", i.subtotal)
                        if (i.discount > 0) Amount("Descuentos", i.discount)
                        Amount("IVA", i.tax)
                        Amount("Total", i.total, bold = true)
                        Text(
                            Sri.PAYMENT_METHODS[i.paymentMethod].orEmpty() + if (i.termDays > 0) " · plazo ${i.termDays} días" else " · de contado",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (f.installments.isNotEmpty()) {
                item {
                    SectionCard(title = "Cuotas · interés ${i.financeRate} % anual") {
                        f.installments.forEach { c ->
                            ListItem(
                                headlineContent = { Text("Cuota ${c.number} · ${Dates.formatShort(c.dueDate)}") },
                                supportingContent = { Text("Capital ${Money.format(c.capital)} · interés ${Money.format(c.interest)}") },
                                trailingContent = { Text(Money.format(c.total), style = MaterialTheme.typography.titleSmall.merge(AmountStyle)) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmVoid && inv != null) {
        ConfirmDialog(
            title = "¿Anular la factura ${inv.number}?",
            message = "Se revierten la venta y el inventario en la app. Si ya está autorizada, también debes solicitar la anulación en el portal del SRI.",
            confirmLabel = "Anular",
            onConfirm = { viewModel.void {} },
            onDismiss = { confirmVoid = false },
        )
    }
}

@Composable
private fun MessageText(m: SriMessage) {
    Text(
        "${m.tipo} ${m.identificador}: ${m.mensaje}" + if (m.informacionAdicional.isNotBlank()) " — ${m.informacionAdicional}" else "",
        style = MaterialTheme.typography.bodySmall,
        color = if (m.tipo == "ERROR") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Amount(label: String, value: Long, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontWeight = if (bold) FontWeight.Bold else null)
        Text(Money.format(value), style = MaterialTheme.typography.bodyLarge.merge(AmountStyle), fontWeight = if (bold) FontWeight.Bold else null)
    }
}
