@file:OptIn(ExperimentalMaterial3Api::class)

package com.dyd.contable.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dyd.contable.data.Account
import com.dyd.contable.data.AccountType
import com.dyd.contable.data.AccountWithBalance
import com.dyd.contable.data.AccountingRepository
import com.dyd.contable.ui.AppViewModelProvider
import com.dyd.contable.ui.components.CategoryIcons
import com.dyd.contable.ui.components.ColorPicker
import com.dyd.contable.ui.components.ConfirmDialog
import com.dyd.contable.ui.components.EmptyState
import com.dyd.contable.ui.components.IconBadge
import com.dyd.contable.ui.components.ScreenScaffold
import com.dyd.contable.ui.components.SelectorField
import com.dyd.contable.ui.dashboard.accountIcon
import com.dyd.contable.ui.theme.AmountStyle
import com.dyd.contable.ui.theme.expenseColor
import com.dyd.contable.util.Money
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AccountsViewModel(private val repo: AccountingRepository) : ViewModel() {
    val accounts: StateFlow<List<AccountWithBalance>> =
        repo.accountsWithBalance().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(account: Account) {
        viewModelScope.launch { repo.saveAccount(account) }
    }

    /** Devuelve por callback si la cuenta se borró (true) o solo se archivó (false). */
    fun remove(account: Account, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(repo.removeAccount(account)) }
    }
}

fun accountTypeLabel(t: AccountType) = when (t) {
    AccountType.CASH -> "Efectivo / caja"
    AccountType.BANK -> "Cuenta bancaria"
    AccountType.CARD -> "Tarjeta de crédito"
    AccountType.OTHER -> "Otra"
}

@Composable
fun AccountsScreen(
    onBack: () -> Unit,
    viewModel: AccountsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Account?>(null) }
    var deleting by remember { mutableStateOf<Account?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    ScreenScaffold(
        title = "Cuentas",
        onBack = onBack,
        snackbarHostState = snackbar,
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = Account(name = "") }) {
                Icon(Icons.Filled.Add, contentDescription = "Nueva cuenta")
            }
        },
    ) { padding ->
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 96.dp)) {
            item {
                ListItem(
                    headlineContent = { Text("Saldo total") },
                    trailingContent = {
                        Text(
                            Money.format(accounts.sumOf { it.balance }),
                            style = MaterialTheme.typography.titleLarge.merge(AmountStyle),
                        )
                    },
                )
            }
            if (accounts.isEmpty()) {
                item { EmptyState(Icons.Filled.AccountBalance, "Sin cuentas", "Crea tu caja o cuenta bancaria para empezar.") }
            }
            items(accounts, key = { it.account.id }) { a ->
                ListItem(
                    modifier = Modifier.clickable { editing = a.account },
                    leadingContent = { IconBadge(accountIcon(a.account.type), Color(a.account.color)) },
                    headlineContent = { Text(a.account.name) },
                    supportingContent = { Text(accountTypeLabel(a.account.type)) },
                    trailingContent = {
                        Text(
                            Money.format(a.balance),
                            style = MaterialTheme.typography.titleMedium.merge(AmountStyle),
                            color = if (a.balance < 0) expenseColor() else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                )
            }
        }
    }

    editing?.let { account ->
        AccountDialog(
            initial = account,
            onDismiss = { editing = null },
            onSave = { viewModel.save(it); editing = null },
            onDelete = if (account.id != 0L) ({ deleting = account; editing = null }) else null,
        )
    }
    deleting?.let { account ->
        ConfirmDialog(
            title = "¿Eliminar \"${account.name}\"?",
            message = "Si la cuenta tiene movimientos, se archivará para conservar tu historial.",
            confirmLabel = "Eliminar",
            onConfirm = {
                viewModel.remove(account) { deleted ->
                    scope.launch { snackbar.showSnackbar(if (deleted) "Cuenta eliminada" else "La cuenta tenía movimientos: se archivó") }
                }
            },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun AccountDialog(
    initial: Account,
    onDismiss: () -> Unit,
    onSave: (Account) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(initial.name) }
    var type by remember { mutableStateOf(initial.type) }
    var balanceText by remember { mutableStateOf(Money.toInput(initial.initialBalance)) }
    var color by remember { mutableStateOf(initial.color) }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "Nueva cuenta" else "Editar cuenta") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = false },
                    label = { Text("Nombre") },
                    isError = error,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                SelectorField(
                    label = "Tipo",
                    selected = type,
                    options = AccountType.entries,
                    optionLabel = ::accountTypeLabel,
                    onSelect = { if (it != null) type = it },
                )
                OutlinedTextField(
                    value = balanceText,
                    onValueChange = { balanceText = it },
                    label = { Text("Saldo inicial") },
                    supportingText = { Text("Lo que había en la cuenta al empezar a usar la app.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ColorPicker(CategoryIcons.palette, color) { color = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) {
                    error = true
                } else {
                    onSave(
                        initial.copy(
                            name = name.trim(),
                            type = type,
                            initialBalance = Money.parse(balanceText) ?: 0L,
                            color = color,
                        )
                    )
                }
            }) { Text("Guardar") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}
