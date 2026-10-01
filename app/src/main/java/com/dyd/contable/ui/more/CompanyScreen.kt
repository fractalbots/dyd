package com.dyd.contable.ui.more

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
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dyd.contable.data.AccountingRepository
import com.dyd.contable.data.Company
import com.dyd.contable.data.Sri
import com.dyd.contable.ui.AppViewModelProvider
import com.dyd.contable.ui.LocalProfile
import com.dyd.contable.ui.components.ScreenScaffold
import com.dyd.contable.ui.components.SelectorField
import com.dyd.contable.util.EcId
import com.dyd.contable.util.Money
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CompanyViewModel(private val repo: AccountingRepository) : ViewModel() {
    val company: StateFlow<Company?> = repo.company().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun save(c: Company, onDone: (Boolean) -> Unit) { viewModelScope.launch { onDone(repo.saveCompany(c)) } }
}

/** Datos del emisor que van en cada factura electrónica, y la tasa de mora para clientes. */
@Composable
fun CompanyScreen(onBack: () -> Unit, viewModel: CompanyViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val loaded by viewModel.company.collectAsStateWithLifecycle()
    val canEdit = LocalProfile.current?.canManageAccounting == true
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var c by remember { mutableStateOf(Company()) }
    var seq by remember { mutableStateOf("1") }
    var rate by remember { mutableStateOf("") }
    var rucError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(loaded) {
        loaded?.let { c = it; seq = it.nextSecuencial.toString(); rate = if (it.lateInterestRate > 0) it.lateInterestRate.toString() else "" }
    }

    @Composable
    fun Field(label: String, value: String, hint: String? = null, keyboard: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) =
        OutlinedTextField(
            value, onChange, label = { Text(label) }, enabled = canEdit, singleLine = true,
            supportingText = hint?.let { { Text(it) } }, keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            modifier = Modifier.fillMaxWidth(),
        )

    ScreenScaffold(title = "Empresa y SRI", onBack = onBack, snackbarHostState = snackbar) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Datos del emisor", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                c.ruc, { c = c.copy(ruc = it.filter(Char::isDigit).take(13)); rucError = null }, label = { Text("RUC") },
                isError = rucError != null, supportingText = rucError?.let { { Text(it) } }, enabled = canEdit, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
            )
            Field("Razón social", c.razonSocial) { c = c.copy(razonSocial = it) }
            Field("Nombre comercial", c.nombreComercial) { c = c.copy(nombreComercial = it) }
            Field("Dirección matriz", c.dirMatriz) { c = c.copy(dirMatriz = it) }
            Field("Dirección del establecimiento", c.dirEstablecimiento, "Si es la misma de la matriz, déjala vacía") { c = c.copy(dirEstablecimiento = it) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) { Field("Establecimiento", c.estab, keyboard = KeyboardType.Number) { c = c.copy(estab = it.filter(Char::isDigit).take(3)) } }
                Column(Modifier.weight(1f)) { Field("Punto de emisión", c.ptoEmi, keyboard = KeyboardType.Number) { c = c.copy(ptoEmi = it.filter(Char::isDigit).take(3)) } }
            }
            Field("Próximo secuencial", seq, "Número de la próxima factura", KeyboardType.Number) { seq = it.filter(Char::isDigit).take(9) }
            SelectorField(
                label = "Régimen", selected = c.regimen, options = Sri.REGIMES.keys.toList(),
                optionLabel = { Sri.REGIMES[it].orEmpty() }, onSelect = { if (it != null && canEdit) c = c.copy(regimen = it) },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Obligado a llevar contabilidad", Modifier.weight(1f))
                Switch(c.obligadoContabilidad, { c = c.copy(obligadoContabilidad = it) }, enabled = canEdit)
            }
            Field("Contribuyente especial (número de resolución)", c.contribuyenteEspecial) { c = c.copy(contribuyenteEspecial = it) }
            Field("Agente de retención (número de resolución)", c.agenteRetencion) { c = c.copy(agenteRetencion = it) }
            Field("Correo", c.email, keyboard = KeyboardType.Email) { c = c.copy(email = it) }
            Field("Teléfono", c.phone, keyboard = KeyboardType.Phone) { c = c.copy(phone = it) }

            Text("Ambiente del SRI", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = c.ambiente == 1, onClick = { if (canEdit) c = c.copy(ambiente = 1) }, label = { Text("Pruebas") })
                FilterChip(selected = c.ambiente == 2, onClick = { if (canEdit) c = c.copy(ambiente = 2) }, label = { Text("Producción") })
            }
            Text(
                "Empieza en pruebas. Cuando el SRI autorice tus facturas de prueba, cambia a producción: desde ahí tienen validez tributaria.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text("Intereses por mora", style = MaterialTheme.typography.titleMedium)
            Field("Tasa anual de mora a clientes (%)", rate, "No debe superar la tasa máxima que publica el Banco Central", KeyboardType.Decimal) { rate = it }

            if (canEdit) Button(
                onClick = {
                    rucError = if (EcId.isValidRuc(c.ruc)) null else "RUC no válido"
                    if (rucError == null) {
                        val updated = c.copy(
                            nextSecuencial = seq.toLongOrNull()?.coerceIn(1, 999_999_999) ?: 1,
                            lateInterestRate = Money.parseDecimal(rate)?.toDouble() ?: 0.0,
                        )
                        viewModel.save(updated) { ok -> scope.launch { snackbar.showSnackbar(if (ok) "Datos guardados" else "No se pudo guardar") } }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Guardar") }
        }
    }
}
