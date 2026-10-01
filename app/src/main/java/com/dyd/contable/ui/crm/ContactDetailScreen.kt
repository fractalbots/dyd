@file:OptIn(ExperimentalMaterial3Api::class)

package com.dyd.contable.ui.crm

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dyd.contable.data.AccountingRepository
import com.dyd.contable.data.Contact
import com.dyd.contable.data.ContactStats
import com.dyd.contable.data.ContactType
import com.dyd.contable.data.EntryDetail
import com.dyd.contable.data.Interaction
import com.dyd.contable.data.InteractionDetail
import com.dyd.contable.data.InteractionKind
import com.dyd.contable.ui.AppViewModelProvider
import com.dyd.contable.ui.LocalProfile
import com.dyd.contable.ui.components.ConfirmDialog
import com.dyd.contable.ui.components.DateField
import com.dyd.contable.ui.components.EntryRow
import com.dyd.contable.ui.components.IconBadge
import com.dyd.contable.ui.components.ScreenScaffold
import com.dyd.contable.ui.components.SectionCard
import com.dyd.contable.ui.more.ContactDialog
import com.dyd.contable.ui.more.contactTypeLabel
import com.dyd.contable.ui.theme.AmountStyle
import com.dyd.contable.ui.theme.expenseColor
import com.dyd.contable.ui.theme.incomeColor
import com.dyd.contable.util.Dates
import com.dyd.contable.util.Money
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

data class ContactDetailState(
    val loading: Boolean = true,
    val contact: Contact? = null,
    val stats: ContactStats = ContactStats(0, 0, 0, 0, 0),
    val interactions: List<InteractionDetail> = emptyList(),
    val entries: List<EntryDetail> = emptyList(),
) {
    /** Utilidad real del cliente: ventas − costo de lo vendido − gastos asociados a él. */
    val profit: Long get() = stats.sales - stats.cogs - stats.expenses
    val margin: Double? get() = if (stats.sales > 0) profit * 100.0 / stats.sales else null
}

class ContactDetailViewModel(savedStateHandle: SavedStateHandle, private val repo: AccountingRepository) : ViewModel() {
    val contactId: Long = savedStateHandle.get<Long>("id") ?: 0L

    val state: StateFlow<ContactDetailState> = combine(
        repo.contact(contactId),
        repo.contactStats(contactId),
        repo.interactions(contactId),
        repo.contactEntries(contactId),
    ) { contact, stats, interactions, entries ->
        ContactDetailState(false, contact, stats, interactions, entries)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ContactDetailState())

    fun saveContact(c: Contact) { viewModelScope.launch { repo.saveContact(c) } }
    fun deleteContact(c: Contact, onDone: () -> Unit) { viewModelScope.launch { if (repo.deleteContact(c)) onDone() } }
    fun saveInteraction(i: Interaction) { viewModelScope.launch { repo.saveInteraction(i) } }
    fun deleteInteraction(id: Long) { viewModelScope.launch { repo.deleteInteraction(id) } }
}

fun interactionLabel(k: InteractionKind) = when (k) {
    InteractionKind.CALL -> "Llamada"
    InteractionKind.VISIT -> "Visita"
    InteractionKind.QUOTE -> "Cotización"
    InteractionKind.MESSAGE -> "Mensaje"
    InteractionKind.OTHER -> "Otro"
}

fun interactionIcon(k: InteractionKind): ImageVector = when (k) {
    InteractionKind.CALL -> Icons.Filled.Call
    InteractionKind.VISIT -> Icons.Filled.Handshake
    InteractionKind.QUOTE -> Icons.Filled.RequestQuote
    InteractionKind.MESSAGE -> Icons.AutoMirrored.Filled.Chat
    InteractionKind.OTHER -> Icons.Filled.Event
}

@Composable
fun ContactDetailScreen(
    onBack: () -> Unit,
    onOpenEntry: (Long) -> Unit,
    viewModel: ContactDetailViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var editing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var interaction by remember { mutableStateOf<Interaction?>(null) }
    val contact = state.contact

    ScreenScaffold(
        title = contact?.name ?: "Contacto",
        onBack = onBack,
        actions = {
            if (contact != null) {
                val profile = LocalProfile.current
                val canEdit = profile?.canWriteContacts == true ||
                    (profile?.canWriteSuppliers == true && contact.type != ContactType.CLIENT)
                if (canEdit) {
                    IconButton(onClick = { editing = true }) { Icon(Icons.Filled.Edit, contentDescription = "Editar") }
                    IconButton(onClick = { deleting = true }) { Icon(Icons.Filled.Delete, contentDescription = "Eliminar") }
                }
            }
        },
    ) { padding ->
        if (contact != null) LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        listOf(contactTypeLabel(contact.type), contact.taxId).filter { it.isNotBlank() }.joinToString(" · "),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (contact.phone.isNotBlank()) {
                            FilledTonalButton(onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + contact.phone))) }) {
                                Icon(Icons.Filled.Call, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Llamar")
                            }
                            FilledTonalButton(onClick = {
                                val digits = contact.phone.filter { it.isDigit() }
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits")))
                            }) {
                                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("WhatsApp")
                            }
                        }
                        if (contact.email.isNotBlank()) {
                            FilledTonalButton(onClick = { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + contact.email))) }) {
                                Icon(Icons.Filled.Email, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Correo")
                            }
                        }
                    }
                }
            }
            if (contact.actividadEconomica.isNotEmpty() || contact.sriCheckedAt != null) {
                item { com.dyd.contable.ui.more.SriInfoCard(contact) }
            }
            item { ProfitCard(state, contact.type) }
            item {
                SectionCard(
                    title = "Seguimientos",
                    action = {
                        IconButton(onClick = {
                            interaction = Interaction(contactId = contact.id, kind = InteractionKind.CALL, date = Dates.toMillis(Dates.today()), note = "")
                        }) { Icon(Icons.Filled.Add, contentDescription = "Nuevo seguimiento") }
                    },
                ) {
                    if (state.interactions.isEmpty()) {
                        Text(
                            "Registra llamadas, visitas y cotizaciones para no perder ninguna venta.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    state.interactions.forEach { d ->
                        val i = d.interaction
                        ListItem(
                            leadingContent = { IconBadge(interactionIcon(i.kind), MaterialTheme.colorScheme.secondary, size = 34.dp) },
                            headlineContent = {
                                Text(
                                    i.note.ifBlank { interactionLabel(i.kind) },
                                    textDecoration = if (i.done) TextDecoration.LineThrough else null,
                                )
                            },
                            supportingContent = {
                                Text(
                                    interactionLabel(i.kind) + " · " + Dates.formatShort(i.date) +
                                        (i.followUp?.let { " · volver a contactar el " + Dates.formatShort(it) } ?: "")
                                )
                            },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (i.followUp != null) {
                                        Checkbox(checked = i.done, onCheckedChange = { viewModel.saveInteraction(i.copy(done = it)) })
                                    }
                                    IconButton(onClick = { interaction = i }) { Icon(Icons.Filled.Edit, contentDescription = "Editar") }
                                }
                            },
                        )
                    }
                }
            }
            item { Text("Movimientos", style = MaterialTheme.typography.titleMedium) }
            if (state.entries.isEmpty()) {
                item { Text("Sin ventas ni gastos registrados con este contacto.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(state.entries, key = { it.entry.id }) { EntryRow(it, onClick = { onOpenEntry(it.entry.id) }) }
        }
    }

    if (editing && contact != null) {
        ContactDialog(
            initial = contact,
            suppliersOnly = LocalProfile.current?.canWriteContacts != true,
            onDismiss = { editing = false },
            onSave = { viewModel.saveContact(it); editing = false },
            onDelete = null,
        )
    }
    if (deleting && contact != null) {
        ConfirmDialog(
            title = "¿Eliminar a ${contact.name}?",
            message = "Sus movimientos se conservan sin contacto; sus seguimientos se borran.",
            confirmLabel = "Eliminar",
            onConfirm = { viewModel.deleteContact(contact, onBack) },
            onDismiss = { deleting = false },
        )
    }
    interaction?.let { i ->
        InteractionDialog(
            initial = i,
            onDismiss = { interaction = null },
            onSave = { viewModel.saveInteraction(it); interaction = null },
            onDelete = if (i.id != 0L) ({ viewModel.deleteInteraction(i.id); interaction = null }) else null,
        )
    }
}

@Composable
private fun ProfitCard(state: ContactDetailState, type: ContactType) {
    val s = state.stats
    SectionCard(title = if (type == ContactType.SUPPLIER) "Resumen con el proveedor" else "Rentabilidad del cliente") {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (type != ContactType.SUPPLIER) {
                StatLine("Ventas", s.sales, incomeColor())
                StatLine("(−) Costo de lo vendido", s.cogs)
                StatLine("(−) Gastos para atenderlo", s.expenses)
                Text(
                    "Transporte, comisiones, descuentos… registrados como gastos con este cliente.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row {
                    Text("Utilidad real", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(
                        Money.format(state.profit) + (state.margin?.let { " (" + String.format(Locale.getDefault(), "%.1f", it) + " %)" } ?: ""),
                        fontWeight = FontWeight.Bold,
                        style = AmountStyle,
                        color = if (state.profit >= 0) incomeColor() else expenseColor(),
                    )
                }
            } else {
                StatLine("Compras", s.expenses, expenseColor())
            }
            if (s.receivable > 0) StatLine("Te debe", s.receivable, incomeColor())
            if (s.payable > 0) StatLine("Le debes", s.payable, expenseColor())
        }
    }
}

@Composable
private fun StatLine(label: String, amount: Long, color: androidx.compose.ui.graphics.Color? = null) {
    Row {
        Text(label, modifier = Modifier.weight(1f))
        Text(Money.format(amount), style = AmountStyle, color = color ?: MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun InteractionDialog(initial: Interaction, onDismiss: () -> Unit, onSave: (Interaction) -> Unit, onDelete: (() -> Unit)?) {
    var kind by remember { mutableStateOf(initial.kind) }
    var note by remember { mutableStateOf(initial.note) }
    var date by remember { mutableStateOf(initial.date) }
    var hasFollowUp by remember { mutableStateOf(initial.followUp != null) }
    var followUp by remember { mutableStateOf(initial.followUp ?: Dates.toMillis(Dates.today().plusDays(7))) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "Nuevo seguimiento" else "Editar seguimiento") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    InteractionKind.entries.take(3).forEach { k ->
                        FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(interactionLabel(k)) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    InteractionKind.entries.drop(3).forEach { k ->
                        FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(interactionLabel(k)) })
                    }
                }
                OutlinedTextField(
                    value = note, onValueChange = { note = it }, label = { Text("¿Qué pasó?") },
                    placeholder = { Text("Ej. Pidió cotización de 200 pallets") }, minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                DateField("Fecha", date, onChange = { date = it })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.TaskAlt, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Programar próximo contacto", modifier = Modifier.weight(1f))
                    Switch(checked = hasFollowUp, onCheckedChange = { hasFollowUp = it })
                }
                if (hasFollowUp) DateField("Volver a contactar", followUp, onChange = { followUp = it })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(initial.copy(kind = kind, note = note.trim(), date = date, followUp = if (hasFollowUp) followUp else null))
            }) { Text("Guardar") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}
