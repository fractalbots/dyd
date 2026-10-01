@file:OptIn(ExperimentalMaterial3Api::class)

package com.dyd.contable.ui.taxes

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dyd.contable.data.AccountingRepository
import com.dyd.contable.data.Company
import com.dyd.contable.data.IvaMonth
import com.dyd.contable.data.LateReceivable
import com.dyd.contable.data.SriCharges
import com.dyd.contable.data.SriRate
import com.dyd.contable.ui.AppViewModelProvider
import com.dyd.contable.ui.LocalProfile
import com.dyd.contable.ui.components.DateField
import com.dyd.contable.ui.components.EmptyState
import com.dyd.contable.ui.components.ScreenScaffold
import com.dyd.contable.ui.components.SectionCard
import com.dyd.contable.ui.components.StatusPill
import com.dyd.contable.ui.theme.AmountStyle
import com.dyd.contable.ui.theme.expenseColor
import com.dyd.contable.ui.theme.incomeColor
import com.dyd.contable.util.Dates
import com.dyd.contable.util.Money
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth

class InterestsViewModel(private val repo: AccountingRepository) : ViewModel() {
    private fun <T> state(f: kotlinx.coroutines.flow.Flow<T>, initial: T) = f.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)
    val late: StateFlow<List<LateReceivable>> = state(repo.lateReceivables(), emptyList())
    val iva: StateFlow<List<IvaMonth>> = state(repo.ivaMonthly(), emptyList())
    val rates: StateFlow<List<SriRate>> = state(repo.sriRates(), emptyList())
    val company: StateFlow<Company?> = state(repo.company(), null)
    val contacts = state(repo.contacts(), emptyList())

    var charges by mutableStateOf<SriCharges?>(null)
        private set

    fun charge(entryId: Long) { viewModelScope.launch { repo.chargeLateInterest(entryId) } }
    fun calculate(tax: Long, due: Long, pay: Long) { viewModelScope.launch { charges = repo.sriLateCharges(tax, due, pay) } }
    fun saveRate(r: SriRate) { viewModelScope.launch { repo.saveSriRate(r) } }
    fun deleteRate(r: SriRate) { viewModelScope.launch { repo.deleteSriRate(r) } }
}

/** Intereses: mora de clientes, IVA mensual e intereses y multas del SRI. */
@Composable
fun InterestsScreen(onBack: () -> Unit, viewModel: InterestsViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    ScreenScaffold(title = "Intereses e impuestos", onBack = onBack) { padding ->
        Column(Modifier.padding(top = padding.calculateTopPadding())) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(tab == 0, { tab = 0 }, text = { Text("Mora clientes") })
                Tab(tab == 1, { tab = 1 }, text = { Text("IVA y SRI") })
            }
            when (tab) {
                0 -> LateTab(viewModel)
                else -> TaxTab(viewModel)
            }
        }
    }
}

@Composable
private fun LateTab(vm: InterestsViewModel) {
    val late by vm.late.collectAsStateWithLifecycle()
    val company by vm.company.collectAsStateWithLifecycle()
    val contacts by vm.contacts.collectAsStateWithLifecycle()
    val canCharge = LocalProfile.current?.canManageAccounting == true
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            val rate = company?.lateInterestRate ?: 0.0
            Text(
                if (rate > 0) "Tasa de mora: $rate % anual. El interés corre por día desde el vencimiento o desde el último cobro."
                else "Configura la tasa de mora en Más › Empresa y SRI para calcular intereses.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (late.isEmpty()) item { EmptyState(Icons.Filled.CheckCircle, "Sin cuentas vencidas", "Ningún cliente está atrasado.") }
        items(late, key = { it.entryId }) { l ->
            SectionCard {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(contacts.firstOrNull { it.id == l.contactId }?.name ?: "Sin cliente", style = MaterialTheme.typography.titleMedium)
                    Text("${l.description} · ${Money.format(l.amount)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    StatusPill("Vencida hace ${l.daysOverdue} días (${Dates.formatShort(l.dueDate)})", expenseColor())
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Interés por ${l.days} días: ${Money.format(l.interest)}", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        if (canCharge && l.interest > 0) FilledTonalButton(onClick = { vm.charge(l.entryId) }) { Text("Cobrar") }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaxTab(vm: InterestsViewModel) {
    val iva by vm.iva.collectAsStateWithLifecycle()
    val rates by vm.rates.collectAsStateWithLifecycle()
    val canEdit = LocalProfile.current?.canManageAccounting == true
    var taxText by remember { mutableStateOf("") }
    var due by remember { mutableStateOf(Dates.toMillis(Dates.today().minusMonths(1))) }
    var pay by remember { mutableStateOf(Dates.toMillis(Dates.today())) }
    var addingRate by remember { mutableStateOf(false) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SectionCard(title = "IVA por mes (formulario 104)") {
                if (iva.isEmpty()) Text("Aún no hay ventas ni compras con IVA.", Modifier.padding(horizontal = 16.dp))
                iva.forEach { m ->
                    ListItem(
                        modifier = Modifier.clickable { taxText = Money.toInput(maxOf(m.taxDue, 0)); due = m.dueDate },
                        headlineContent = { Text(Dates.monthName(YearMonth.from(Dates.toLocalDate(m.month)))) },
                        supportingContent = {
                            Text("IVA ventas ${Money.format(m.salesTax)} − IVA compras ${Money.format(m.purchasesTax)} · vence ${Dates.formatShort(m.dueDate)}")
                        },
                        trailingContent = {
                            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                                Text(Money.format(kotlin.math.abs(m.taxDue)), style = MaterialTheme.typography.titleSmall.merge(AmountStyle))
                                Text(if (m.taxDue >= 0) "a pagar" else "crédito", style = MaterialTheme.typography.labelSmall,
                                    color = if (m.taxDue >= 0) expenseColor() else incomeColor())
                            }
                        },
                    )
                }
                Text(
                    "Toca un mes para calcular intereses y multa si se paga tarde. Resumen de apoyo: la declaración la presenta tu contador en el SRI.",
                    Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            SectionCard(title = "Intereses y multa por atraso (SRI)") {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(taxText, { taxText = it }, label = { Text("Impuesto a pagar") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    DateField("Fecha de vencimiento", due, { due = it })
                    DateField("Fecha de pago", pay, { pay = it })
                    Button(onClick = { Money.parse(taxText)?.let { vm.calculate(it, due, pay) } }, modifier = Modifier.fillMaxWidth()) { Text("Calcular") }
                    vm.charges?.let { c ->
                        Text("${c.months} mes(es) o fracción de atraso")
                        Text("Interés por mora: ${Money.format(c.interest)}")
                        Text("Multa (3 % mensual, máximo 100 %): ${Money.format(c.fine)}")
                        Text("Total a pagar: ${Money.format(c.total)}", fontWeight = FontWeight.Bold)
                        if (c.missingRates) Text(
                            "Faltan tasas trimestrales del SRI para algunos meses: agrégalas abajo para que el interés sea exacto.",
                            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        item {
            SectionCard(title = "Tasas de mora tributaria (% mensual)", action = {
                if (canEdit) IconButton(onClick = { addingRate = true }) { Icon(Icons.Filled.Add, contentDescription = "Agregar tasa") }
            }) {
                if (rates.isEmpty()) Text(
                    "El SRI publica la tasa cada trimestre. Regístrala aquí para calcular los intereses.",
                    Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall,
                )
                rates.forEach { r ->
                    ListItem(
                        headlineContent = { Text("Trimestre desde ${Dates.formatShort(r.quarterStart)}") },
                        supportingContent = { Text("${r.monthlyRate} % mensual") },
                        trailingContent = { if (canEdit) IconButton(onClick = { vm.deleteRate(r) }) { Icon(Icons.Filled.Delete, contentDescription = "Quitar") } },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (addingRate) {
        var start by remember { mutableStateOf(Dates.toMillis(Dates.today().withDayOfMonth(1))) }
        var rate by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addingRate = false },
            title = { Text("Tasa trimestral del SRI") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DateField("Cualquier día del trimestre", start, { start = it })
                    OutlinedTextField(rate, { rate = it }, label = { Text("% mensual") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val d = Dates.toLocalDate(start)
                    val quarterStart = d.withMonth(((d.monthValue - 1) / 3) * 3 + 1).withDayOfMonth(1)
                    Money.parseDecimal(rate)?.toDouble()?.let { vm.saveRate(SriRate(Dates.toMillis(quarterStart), it)) }
                    addingRate = false
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { addingRate = false }) { Text("Cancelar") } },
        )
    }
}
