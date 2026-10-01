package com.dyd.contable.ui.entries

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dyd.contable.data.Account
import com.dyd.contable.data.AccountingRepository
import com.dyd.contable.data.Category
import com.dyd.contable.data.Contact
import com.dyd.contable.data.Entry
import com.dyd.contable.data.EntryType
import com.dyd.contable.data.ItemWithStock
import com.dyd.contable.util.Quantity
import com.dyd.contable.util.Dates
import com.dyd.contable.util.Money
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EntryEditorViewModel(
    savedStateHandle: SavedStateHandle,
    private val repo: AccountingRepository,
) : ViewModel() {
    private val entryId: Long = savedStateHandle.get<Long>("id") ?: 0L
    val isEditing: Boolean get() = entryId != 0L
    private var original: Entry? = null

    val accounts: StateFlow<List<Account>> =
        repo.accounts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories: StateFlow<List<Category>> =
        repo.categories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val contacts: StateFlow<List<Contact>> =
        repo.contacts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val items: StateFlow<List<ItemWithStock>> =
        repo.items().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Estado del formulario
    var type by mutableStateOf(
        savedStateHandle.get<String>("type")?.let { runCatching { EntryType.valueOf(it) }.getOrNull() } ?: EntryType.EXPENSE
    )
        private set
    var amountText by mutableStateOf("")
        private set
    /** IVA incluido en el monto (crédito tributario en compras, IVA cobrado en ventas). */
    var taxText by mutableStateOf("")
        private set
    var taxError by mutableStateOf<String?>(null)
        private set
    var description by mutableStateOf("")
    var reference by mutableStateOf("")
    var date by mutableStateOf(Dates.toMillis(Dates.today()))
    var accountId by mutableStateOf<Long?>(null)
    var toAccountId by mutableStateOf<Long?>(null)
    var categoryId by mutableStateOf<Long?>(null)
    var contactId by mutableStateOf<Long?>(null)
    var isPaid by mutableStateOf(true)
    var dueDate by mutableStateOf(Dates.toMillis(Dates.today().plusDays(30)))
    /** Artículo vendido (ingreso) o comprado (gasto), opcional. */
    var itemId by mutableStateOf(savedStateHandle.get<Long>("itemId")?.takeIf { it > 0 })
        private set
    var quantityText by mutableStateOf("")
        private set
    var quantityError by mutableStateOf<String?>(null)
        private set

    // Errores de validación
    var amountError by mutableStateOf<String?>(null)
        private set
    var accountError by mutableStateOf<String?>(null)
        private set
    var loaded by mutableStateOf(!isEditing)
        private set
    var finished by mutableStateOf(false)
        private set
    var saving by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            if (isEditing) {
                repo.entry(entryId)?.let { e ->
                    original = e
                    type = e.type
                    amountText = Money.toInput(e.amount)
                    taxText = Money.toInput(e.taxAmount)
                    description = e.description
                    reference = e.reference
                    date = e.date
                    accountId = e.accountId
                    toAccountId = e.toAccountId
                    categoryId = e.categoryId
                    contactId = e.contactId
                    isPaid = e.isPaid
                    e.dueDate?.let { dueDate = it }
                }
                repo.stockMoveForEntry(entryId)?.let { m ->
                    itemId = m.itemId
                    quantityText = Quantity.toInput(kotlin.math.abs(m.quantity))
                }
                loaded = true
            } else {
                val first = repo.accounts().first()
                if (accountId == null) accountId = first.firstOrNull()?.id
            }
        }
    }

    fun changeType(t: EntryType) {
        if (t == type) return
        type = t
        // La categoría depende del tipo; al cambiarlo se limpia.
        categoryId = null
        if (t == EntryType.TRANSFER) {
            isPaid = true
            contactId = null
            itemId = null
        }
    }

    fun changeItem(item: ItemWithStock?) {
        itemId = item?.item?.id
        quantityError = null
        if (item != null && description.isBlank()) {
            description = (if (type == EntryType.INCOME) "Venta de " else "Compra de ") + item.item.name
        }
        suggestAmount()
    }

    fun changeQuantity(text: String) {
        quantityText = text.filter { it.isDigit() || it == '.' || it == ',' }
        quantityError = null
        suggestAmount()
    }

    /** En ventas, propone el monto con el precio de lista si aún no se ha escrito. */
    private fun suggestAmount() {
        if (type != EntryType.INCOME || amountText.isNotBlank()) return
        val item = items.value.firstOrNull { it.item.id == itemId }?.item ?: return
        val qty = Quantity.parse(quantityText) ?: return
        if (item.salePrice > 0 && qty > 0) amountText = Money.toInput(Math.round(item.salePrice * qty))
    }

    fun changeAmount(text: String) {
        amountText = text.filter { it.isDigit() || it == '.' || it == ',' }
        amountError = null
    }

    fun changeTax(text: String) {
        taxText = text.filter { it.isDigit() || it == '.' || it == ',' }
        taxError = null
    }

    /** Calcula el IVA de un monto que ya lo incluye: monto × tarifa / (100 + tarifa). */
    fun taxFromAmount(ratePercent: Int) {
        val amount = Money.parse(amountText) ?: return
        taxText = Money.toInput(Math.round(amount * ratePercent / (100.0 + ratePercent)))
        taxError = null
    }

    fun changeAccount(id: Long?) {
        accountId = id
        accountError = null
    }

    fun changeToAccount(id: Long?) {
        toAccountId = id
        accountError = null
    }

    fun save() {
        val amount = Money.parse(amountText)
        amountError = when {
            amount == null -> "Escribe un monto válido"
            amount <= 0 -> "El monto debe ser mayor que cero"
            else -> null
        }
        accountError = when {
            accountId == null -> "Elige una cuenta"
            type == EntryType.TRANSFER && toAccountId == null -> "Elige la cuenta destino"
            type == EntryType.TRANSFER && toAccountId == accountId -> "Las cuentas deben ser distintas"
            else -> null
        }
        val tax = if (type == EntryType.TRANSFER || taxText.isBlank()) 0L else Money.parse(taxText)
        taxError = when {
            tax == null || tax < 0 -> "Escribe un valor válido"
            amount != null && tax > amount -> "El IVA no puede superar el monto"
            else -> null
        }
        val quantity = Quantity.parse(quantityText)
        quantityError = if (itemId != null && type != EntryType.TRANSFER && (quantity == null || quantity <= 0)) "Escribe la cantidad" else null
        if (amountError != null || accountError != null || quantityError != null || taxError != null || amount == null) return
        val account = accountId ?: return

        val isTransfer = type == EntryType.TRANSFER
        val entry = Entry(
            id = entryId,
            type = type,
            amount = amount,
            date = date,
            accountId = account,
            toAccountId = if (isTransfer) toAccountId else null,
            categoryId = if (isTransfer) null else categoryId,
            contactId = if (isTransfer) null else contactId,
            description = description.trim(),
            reference = reference.trim(),
            isPaid = isTransfer || isPaid,
            dueDate = if (!isTransfer && !isPaid) dueDate else null,
            taxAmount = tax ?: 0,
        )
        viewModelScope.launch {
            saving = true
            val ok = repo.saveEntry(entry, if (isTransfer) null else itemId, quantity)
            saving = false
            if (ok) finished = true
        }
    }

    fun delete() {
        if (!isEditing) return
        viewModelScope.launch {
            if (repo.deleteEntry(entryId)) finished = true
        }
    }
}
