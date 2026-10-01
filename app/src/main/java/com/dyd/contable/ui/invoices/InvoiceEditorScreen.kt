package com.dyd.contable.ui.invoices

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dyd.contable.data.Account
import com.dyd.contable.data.AccountingRepository
import com.dyd.contable.data.Contact
import com.dyd.contable.data.ContactType
import com.dyd.contable.data.DraftLine
import com.dyd.contable.data.InvoiceDraft
import com.dyd.contable.data.ItemWithStock
import com.dyd.contable.data.Sri
import com.dyd.contable.ui.AppViewModelProvider
import com.dyd.contable.ui.components.DateField
import com.dyd.contable.ui.components.ScreenScaffold
import com.dyd.contable.ui.components.SectionCard
import com.dyd.contable.ui.components.SelectorField
import com.dyd.contable.ui.theme.AmountStyle
import com.dyd.contable.util.Dates
import com.dyd.contable.util.EcId
import com.dyd.contable.util.Money
import com.dyd.contable.util.Quantity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.pow

/** Totales como los calcula create_invoice: IVA por tarifa sobre la base de cada una. */
data class DraftTotals(val subtotal: Long, val discount: Long, val taxByCode: Map<String, Long>, val tax: Long, val total: Long)

fun draftTotals(lines: List<DraftLine>): DraftTotals {
    val bases = lines.groupBy { it.ivaCode }.mapValues { (_, l) -> l.sumOf { it.subtotal } }
    val taxes = bases.mapValues { (code, base) -> Math.round(base * Sri.ivaRate(code) / 100.0) }
    val subtotal = bases.values.sum()
    val tax = taxes.values.sum()
    return DraftTotals(subtotal, lines.sumOf { it.discount }, taxes, tax, subtotal + tax)
}

/** Cuota fija del sistema francés (tasa anual %, n cuotas mensuales). */
fun frenchQuota(total: Long, annualRate: Double, n: Int): Long {
    if (n <= 1) return total
    val r = annualRate / 1200
    return if (r == 0.0) Math.ceil(total.toDouble() / n).toLong()
    else Math.round(total * r / (1 - (1 + r).pow(-n)))
}

class InvoiceEditorViewModel(private val repo: AccountingRepository) : ViewModel() {
    /** Solo clientes con identificación válida: el SRI la exige. */
    val clients: StateFlow<List<Contact>> = repo.contacts()
        .map { list -> list.filter { it.type != ContactType.SUPPLIER && EcId.isValid(it.idType, it.taxId) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val accounts: StateFlow<List<Account>> = repo.accounts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val items: StateFlow<List<ItemWithStock>> = repo.items().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var contactId by mutableStateOf<Long?>(null)
    var date by mutableStateOf(Dates.toMillis(Dates.today()))
    var accountId by mutableStateOf<Long?>(null)
    var paymentMethod by mutableStateOf("01")
    var credit by mutableStateOf(false)
    var termDays by mutableStateOf("30")
    var installments by mutableStateOf("1")
    var financeRate by mutableStateOf("")
    var note by mutableStateOf("")
    val lines = mutableStateListOf<DraftLine>()
    var error by mutableStateOf<String?>(null)
    var saving by mutableStateOf(false)
        private set
    var createdId by mutableStateOf<Long?>(null)
        private set

    init {
        viewModelScope.launch { if (accountId == null) accountId = repo.accounts().first().firstOrNull()?.id }
    }

    val installmentCount: Int get() = if (credit) (installments.toIntOrNull() ?: 1).coerceIn(1, 60) else 1
    val rate: Double get() = Money.parseDecimal(financeRate)?.toDouble() ?: 0.0

    fun save() {
        val contact = contactId
        val account = accountId
        error = when {
            contact == null -> "Elige el cliente"
            account == null -> "Elige la cuenta donde entra el dinero"
            lines.isEmpty() -> "Agrega al menos un producto"
            else -> null
        }
        val total = draftTotals(lines).total
        val client = clients.value.firstOrNull { it.id == contact }
        if (error == null && client?.idType == "07" && total > 5000) {
            error = "A consumidor final se factura hasta USD 50,00. Registra los datos del cliente."
        }
        if (error != null || contact == null || account == null) return
        viewModelScope.launch {
            saving = true
            createdId = repo.createInvoice(
                InvoiceDraft(
                    contactId = contact, date = date, accountId = account, paymentMethod = paymentMethod,
                    termDays = if (credit && installmentCount == 1) termDays.toIntOrNull() ?: 0 else 0,
                    installments = installmentCount, financeRate = if (installmentCount > 1) rate else 0.0,
                    note = note.trim(), lines = lines.toList(),
                )
            )
            saving = false
        }
    }
}

@Composable
fun InvoiceEditorScreen(
    onBack: () -> Unit,
    onCreated: (Long) -> Unit,
    viewModel: InvoiceEditorViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val clients by viewModel.clients.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    var editingLine by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(viewModel.createdId) { viewModel.createdId?.let(onCreated) }
    val totals = draftTotals(viewModel.lines)

    ScreenScaffold(title = "Nueva factura", onBack = onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SelectorField(
                label = "Cliente", selected = clients.firstOrNull { it.id == viewModel.contactId }, options = clients,
                optionLabel = { "${it.name} · ${it.taxId}" }, onSelect = { viewModel.contactId = it?.id },
            )
            if (clients.isEmpty()) {
                Text(
                    "Solo aparecen clientes con RUC, cédula o pasaporte válido. Contabilidad los registra en Clientes.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DateField("Fecha de emisión", viewModel.date, { viewModel.date = it })

            SectionCard(title = "Productos", action = {
                IconButton(onClick = { viewModel.lines.add(DraftLine()); editingLine = viewModel.lines.lastIndex }) {
                    Icon(Icons.Filled.Add, contentDescription = "Agregar producto")
                }
            }) {
                if (viewModel.lines.isEmpty()) {
                    Text("Toca + para agregar productos o servicios.", Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                viewModel.lines.forEachIndexed { index, l ->
                    ListItem(
                        modifier = Modifier.clickable { editingLine = index },
                        headlineContent = { Text(l.description.ifBlank { "Sin descripción" }) },
                        supportingContent = {
                            Text("${Quantity.format(l.quantity)} × ${Money.format(l.unitPrice)}" +
                                (if (l.discount > 0) " − ${Money.format(l.discount)}" else "") + " · ${Sri.IVA_CODES[l.ivaCode]}")
                        },
                        trailingContent = { Text(Money.format(l.subtotal), style = MaterialTheme.typography.titleSmall.merge(AmountStyle)) },
                    )
                }
            }

            SectionCard(title = "Totales") {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TotalRow("Subtotal sin impuestos", totals.subtotal)
                    if (totals.discount > 0) TotalRow("Descuentos", totals.discount)
                    totals.taxByCode.filter { Sri.ivaRate(it.key) > 0 }.forEach { (code, v) -> TotalRow(Sri.IVA_CODES[code].orEmpty(), v) }
                    HorizontalDivider()
                    TotalRow("Total", totals.total, bold = true)
                }
            }

            SelectorField(
                label = "Cuenta donde entra el dinero", selected = accounts.firstOrNull { it.id == viewModel.accountId },
                options = accounts, optionLabel = { it.name }, onSelect = { viewModel.accountId = it?.id },
            )
            SelectorField(
                label = "Forma de pago (SRI)", selected = viewModel.paymentMethod, options = Sri.PAYMENT_METHODS.keys.toList(),
                optionLabel = { Sri.PAYMENT_METHODS[it].orEmpty() }, onSelect = { if (it != null) viewModel.paymentMethod = it },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !viewModel.credit, onClick = { viewModel.credit = false }, label = { Text("De contado") })
                FilterChip(selected = viewModel.credit, onClick = { viewModel.credit = true }, label = { Text("A crédito") })
            }
            if (viewModel.credit) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        viewModel.installments, { viewModel.installments = it.filter(Char::isDigit) }, label = { Text("Cuotas mensuales") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f),
                    )
                    if (viewModel.installmentCount > 1) {
                        OutlinedTextField(
                            viewModel.financeRate, { viewModel.financeRate = it }, label = { Text("Interés anual %") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f),
                        )
                    } else {
                        OutlinedTextField(
                            viewModel.termDays, { viewModel.termDays = it.filter(Char::isDigit) }, label = { Text("Plazo en días") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (viewModel.installmentCount > 1 && totals.total > 0) {
                    val quota = frenchQuota(totals.total, viewModel.rate, viewModel.installmentCount)
                    Text(
                        "${viewModel.installmentCount} cuotas de aprox. ${Money.format(quota)} · intereses ${Money.format(quota * viewModel.installmentCount - totals.total)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            OutlinedTextField(viewModel.note, { viewModel.note = it }, label = { Text("Nota interna (opcional)") }, modifier = Modifier.fillMaxWidth())

            viewModel.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = viewModel::save, enabled = !viewModel.saving, modifier = Modifier.fillMaxWidth()) {
                if (viewModel.saving) CircularProgressIndicator(Modifier.padding(end = 8.dp).then(Modifier), strokeWidth = 2.dp)
                Text("Emitir factura")
            }
            Text(
                "Al emitir se asigna el número, se registra la venta y se descuenta el inventario. Después la envías al SRI.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    editingLine?.let { index ->
        if (index in viewModel.lines.indices) {
            LineDialog(
                initial = viewModel.lines[index],
                items = items,
                onDismiss = {
                    if (viewModel.lines[index].description.isBlank()) viewModel.lines.removeAt(index)
                    editingLine = null
                },
                onDelete = { viewModel.lines.removeAt(index); editingLine = null },
                onSave = { viewModel.lines[index] = it; editingLine = null },
            )
        }
    }
}

@Composable
private fun TotalRow(label: String, value: Long, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontWeight = if (bold) FontWeight.Bold else null)
        Text(Money.format(value), style = MaterialTheme.typography.bodyLarge.merge(AmountStyle), fontWeight = if (bold) FontWeight.Bold else null)
    }
}

@Composable
private fun LineDialog(
    initial: DraftLine,
    items: List<ItemWithStock>,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSave: (DraftLine) -> Unit,
) {
    var itemId by remember { mutableStateOf(initial.itemId) }
    var description by remember { mutableStateOf(initial.description) }
    var quantity by remember { mutableStateOf(Quantity.toInput(initial.quantity)) }
    var price by remember { mutableStateOf(Money.toInput(initial.unitPrice)) }
    var discount by remember { mutableStateOf(Money.toInput(initial.discount)) }
    var ivaCode by remember { mutableStateOf(initial.ivaCode) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Producto o servicio") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SelectorField(
                    label = "Del inventario (opcional)", selected = items.firstOrNull { it.item.id == itemId }, options = items,
                    optionLabel = { "${it.item.name} · stock ${Quantity.format(it.stock, it.item.unit)}" }, allowNone = true, noneLabel = "Servicio u otro",
                    onSelect = { s ->
                        itemId = s?.item?.id
                        if (s != null) {
                            description = s.item.name
                            ivaCode = s.item.ivaCode
                            if (price.isBlank() && s.item.salePrice > 0) price = Money.toInput(s.item.salePrice)
                        }
                    },
                )
                OutlinedTextField(description, { description = it }, label = { Text("Descripción") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(quantity, { quantity = it }, label = { Text("Cantidad") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    OutlinedTextField(price, { price = it }, label = { Text("Precio unitario sin IVA") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                }
                OutlinedTextField(discount, { discount = it }, label = { Text("Descuento (valor)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                SelectorField(
                    label = "IVA", selected = ivaCode, options = Sri.IVA_CODES.keys.toList(),
                    optionLabel = { Sri.IVA_CODES[it].orEmpty() }, onSelect = { if (it != null) ivaCode = it },
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val q = Quantity.parse(quantity)
                val p = Money.parse(price)
                val d = if (discount.isBlank()) 0L else Money.parse(discount)
                error = when {
                    description.isBlank() -> "Escribe la descripción"
                    q == null || q <= 0 -> "Cantidad no válida"
                    p == null || p < 0 -> "Precio no válido"
                    d == null || d < 0 || d > Math.round(q * p) -> "Descuento no válido"
                    else -> null
                }
                if (error == null && q != null && p != null && d != null) {
                    onSave(DraftLine(itemId, description.trim(), q, p, d, ivaCode))
                }
            }) { Text("Listo") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = null); Text("Quitar") }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}
