@file:OptIn(ExperimentalMaterial3Api::class)

package com.dyd.contable.ui.more

import com.dyd.contable.data.remote.SriCatastro
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dyd.contable.data.AccountingRepository
import com.dyd.contable.data.Contact
import com.dyd.contable.data.ContactType
import com.dyd.contable.data.ContactWithBalance
import com.dyd.contable.ui.AppViewModelProvider
import com.dyd.contable.ui.LocalProfile
import com.dyd.contable.util.EcId
import com.dyd.contable.ui.components.ConfirmDialog
import com.dyd.contable.ui.components.EmptyState
import com.dyd.contable.ui.components.IconBadge
import com.dyd.contable.ui.components.ScreenScaffold
import com.dyd.contable.ui.components.SelectorField
import com.dyd.contable.ui.components.StatusPill
import com.dyd.contable.ui.theme.expenseColor
import com.dyd.contable.ui.theme.incomeColor
import com.dyd.contable.util.Money
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ContactsViewModel(private val repo: AccountingRepository) : ViewModel() {
    val contacts: StateFlow<List<ContactWithBalance>> =
        repo.contactsWithBalance().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(contact: Contact) {
        viewModelScope.launch { repo.saveContact(contact) }
    }

    fun delete(contact: Contact) {
        viewModelScope.launch { repo.deleteContact(contact) }
    }
}

fun contactTypeLabel(t: ContactType) = when (t) {
    ContactType.CLIENT -> "Cliente"
    ContactType.SUPPLIER -> "Proveedor"
    ContactType.BOTH -> "Cliente y proveedor"
}

@Composable
fun ContactsScreen(
    onBack: () -> Unit,
    onOpenContact: (Long) -> Unit,
    viewModel: ContactsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<Contact?>(null) }
    var deleting by remember { mutableStateOf<Contact?>(null) }
    val context = LocalContext.current
    val profile = LocalProfile.current

    val wanted = if (tab == 0) ContactType.CLIENT else ContactType.SUPPLIER
    val shown = contacts.filter {
        (it.contact.type == wanted || it.contact.type == ContactType.BOTH) &&
            (query.isBlank() || it.contact.name.contains(query, ignoreCase = true) || it.contact.taxId.contains(query))
    }

    ScreenScaffold(
        title = "Clientes y proveedores",
        onBack = onBack,
        floatingActionButton = {
            // Clientes: contabilidad los crea con su RUC. Proveedores: también compras.
            val canCreate = if (tab == 0) profile?.canWriteContacts == true else profile?.canWriteSuppliers == true
            if (canCreate) {
                FloatingActionButton(onClick = { editing = Contact(name = "", type = wanted, idType = if (tab == 0) "04" else "") }) {
                    Icon(Icons.Filled.PersonAdd, contentDescription = "Nuevo contacto")
                }
            }
        },
    ) { padding ->
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 96.dp)) {
            item {
                PrimaryTabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Clientes") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Proveedores") })
                }
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Buscar por nombre, RUC o cédula") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
            if (shown.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Filled.Business,
                        if (tab == 0) "Sin clientes" else "Sin proveedores",
                        "Agrega contactos para saber quién te debe y a quién le debes.",
                    )
                }
            }
            items(shown, key = { it.contact.id }) { c ->
                ListItem(
                    modifier = Modifier.clickable { onOpenContact(c.contact.id) },
                    leadingContent = {
                        IconBadge(Icons.Filled.Business, if (c.contact.type == ContactType.SUPPLIER) expenseColor() else incomeColor())
                    },
                    headlineContent = { Text(c.contact.name) },
                    supportingContent = {
                        Column {
                            val info = listOf(contactTypeLabel(c.contact.type), c.contact.taxId).filter { it.isNotBlank() }
                            Text(info.joinToString(" · "))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (c.receivable > 0) StatusPill("Te debe ${Money.format(c.receivable)}", incomeColor())
                                if (c.payable > 0) StatusPill("Le debes ${Money.format(c.payable)}", expenseColor())
                            }
                        }
                    },
                    trailingContent = {
                        if (c.contact.phone.isNotBlank()) {
                            IconButton(onClick = {
                                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + c.contact.phone)))
                            }) { Icon(Icons.Filled.Call, contentDescription = "Llamar") }
                        }
                    },
                )
            }
        }
    }

    editing?.let { contact ->
        ContactDialog(
            initial = contact,
            suppliersOnly = profile?.canWriteContacts != true,
            onDismiss = { editing = null },
            onSave = { viewModel.save(it); editing = null },
            onDelete = if (contact.id != 0L) ({ deleting = contact; editing = null }) else null,
        )
    }
    deleting?.let { contact ->
        ConfirmDialog(
            title = "¿Eliminar a ${contact.name}?",
            message = "Sus movimientos se conservarán, pero quedarán sin contacto asociado.",
            confirmLabel = "Eliminar",
            onConfirm = { viewModel.delete(contact) },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
internal fun ContactDialog(
    initial: Contact,
    suppliersOnly: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (Contact) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(initial.name) }
    var type by remember { mutableStateOf(initial.type) }
    var taxId by remember { mutableStateOf(initial.taxId) }
    var phone by remember { mutableStateOf(initial.phone) }
    var email by remember { mutableStateOf(initial.email) }
    var notes by remember { mutableStateOf(initial.notes) }
    var idType by remember { mutableStateOf(initial.idType) }
    var address by remember { mutableStateOf(initial.address) }
    var error by remember { mutableStateOf(false) }
    var idError by remember { mutableStateOf<String?>(null) }
    // Datos del SRI: solo cambian al consultar el catastro.
    var sri by remember { mutableStateOf(initial) }
    var consulting by remember { mutableStateOf(false) }
    var sriMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun consultSri() {
        val ruc = taxId.trim()
        if (!EcId.isValidRuc(ruc)) { idError = EcId.error("04", ruc) ?: "RUC no válido"; return }
        consulting = true; sriMessage = null
        scope.launch {
            try {
                val c = SriCatastro.consultar(ruc)
                name = c.razonSocial.ifBlank { name }
                if (address.isBlank()) address = c.direccion
                sri = sri.copy(
                    actividadEconomica = c.actividadEconomica, sriEstado = c.estado, sriTipo = c.tipo,
                    sriRegimen = c.regimen, sriObligado = c.obligadoContabilidad, sriData = c.raw.toString(),
                    sriCheckedAt = java.time.OffsetDateTime.now().toString(),
                )
                sriMessage = if (c.estado.isNotEmpty() && c.estado != "ACTIVO") "Atención: el RUC está ${c.estado}" else null
            } catch (e: Exception) {
                sriMessage = e.message ?: "No se pudo consultar el SRI"
            } finally {
                consulting = false
            }
        }
    }
    val typeOptions = if (suppliersOnly) listOf(ContactType.SUPPLIER, ContactType.BOTH) else ContactType.entries
    // Los clientes se facturan: necesitan identificación válida. En proveedores es opcional.
    fun validateId(): String? = when {
        idType.isEmpty() && taxId.isBlank() && type == ContactType.SUPPLIER -> null
        idType.isEmpty() && taxId.isBlank() -> "Los clientes necesitan RUC, cédula o pasaporte para facturarles"
        else -> EcId.error(idType, taxId.trim())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "Nuevo contacto" else "Editar contacto") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it; error = false },
                    label = { Text("Nombre o razón social") }, isError = error, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                SelectorField(
                    label = "Tipo", selected = type, options = typeOptions,
                    optionLabel = ::contactTypeLabel, onSelect = { if (it != null) type = it },
                )
                SelectorField(
                    label = "Identificación", selected = idType.ifEmpty { null }, options = EcId.TYPES.keys.toList(),
                    optionLabel = { EcId.TYPES[it].orEmpty() }, allowNone = true, noneLabel = "Sin identificación",
                    onSelect = {
                        idType = it.orEmpty(); idError = null
                        if (it == "07") { taxId = EcId.CONSUMIDOR_FINAL; if (name.isBlank()) name = "CONSUMIDOR FINAL" }
                    },
                )
                OutlinedTextField(
                    value = taxId,
                    onValueChange = { v ->
                        taxId = v.trim(); idError = null
                        if (idType.isEmpty() && v.length >= 10) idType = EcId.guessType(v.trim())
                    },
                    label = { Text(EcId.TYPES[idType] ?: "RUC o cédula") },
                    isError = idError != null,
                    supportingText = idError?.let { { Text(it) } },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = if (idType in listOf("04", "05")) KeyboardType.Number else KeyboardType.Text),
                )
                if (idType == "04") {
                    OutlinedButton(onClick = ::consultSri, enabled = !consulting, modifier = Modifier.fillMaxWidth()) {
                        Text(if (consulting) "Consultando al SRI…" else "Consultar SRI (razón social y actividad)")
                    }
                }
                if (sri.sriCheckedAt != null || sri.actividadEconomica.isNotEmpty()) {
                    SriInfoCard(sri)
                }
                sriMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                OutlinedTextField(
                    value = address, onValueChange = { address = it }, label = { Text("Dirección") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = phone, onValueChange = { phone = it }, label = { Text("Teléfono") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = email, onValueChange = { email = it }, label = { Text("Correo") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it }, label = { Text("Notas") },
                    minLines = 2, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                idError = validateId()
                if (name.isBlank()) error = true
                else if (idError == null) onSave(
                    initial.copy(
                        name = name.trim(), type = type, taxId = taxId.trim(),
                        phone = phone.trim(), email = email.trim(), notes = notes.trim(),
                        idType = idType, address = address.trim(),
                        actividadEconomica = sri.actividadEconomica, sriEstado = sri.sriEstado, sriTipo = sri.sriTipo,
                        sriRegimen = sri.sriRegimen, sriObligado = sri.sriObligado, sriData = sri.sriData,
                        sriCheckedAt = sri.sriCheckedAt,
                    )
                )
            }) { Text("Guardar") }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}

/** Datos del catastro del SRI de un contacto (solo lectura: vienen de la consulta al SRI). */
@Composable
internal fun SriInfoCard(c: Contact) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Datos del SRI", style = MaterialTheme.typography.labelLarge)
            if (c.actividadEconomica.isNotEmpty()) Text(c.actividadEconomica, style = MaterialTheme.typography.bodyMedium)
            val extra = listOf(c.sriEstado, c.sriTipo, c.sriRegimen.let { if (it.isNotEmpty()) "Régimen $it" else "" },
                c.sriObligado.let { if (it.isNotEmpty()) "Obligado a llevar contabilidad: $it" else "" }).filter { it.isNotEmpty() }
            if (extra.isNotEmpty()) Text(extra.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            c.sriCheckedAt?.let { Text("Consultado el ${it.take(10)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
