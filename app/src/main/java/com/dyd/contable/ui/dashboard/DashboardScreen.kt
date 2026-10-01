@file:OptIn(ExperimentalMaterial3Api::class)

package com.dyd.contable.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dyd.contable.data.AccountType
import com.dyd.contable.data.AccountWithBalance
import com.dyd.contable.data.EntryType
import com.dyd.contable.ui.AppViewModelProvider
import com.dyd.contable.ui.components.CategoryDonut
import com.dyd.contable.ui.components.EmptyState
import com.dyd.contable.ui.components.EntryRow
import com.dyd.contable.ui.components.IconBadge
import com.dyd.contable.ui.components.IncomeExpenseBarChart
import com.dyd.contable.ui.components.ScreenScaffold
import com.dyd.contable.ui.components.SectionCard
import com.dyd.contable.ui.theme.AmountStyle
import com.dyd.contable.ui.theme.expenseColor
import com.dyd.contable.ui.theme.incomeColor
import com.dyd.contable.ui.theme.transferColor
import com.dyd.contable.util.Dates
import com.dyd.contable.util.Money

@Composable
fun DashboardScreen(
    onNewEntry: (EntryType) -> Unit,
    onOpenEntry: (Long) -> Unit,
    onSeeAll: () -> Unit,
    onOpenPending: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenInventory: () -> Unit,
    onOpenContact: (Long) -> Unit,
    viewModel: DashboardViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ScreenScaffold(
        title = "DYD Contable",
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onNewEntry(EntryType.EXPENSE) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Registrar") },
            )
        },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { BalanceHero(state) }
            item { QuickActions(onNewEntry) }
            item { AccountsRow(state.accounts, onOpenAccounts) }
            if (state.receivable > 0 || state.payable > 0) {
                item { PendingSummary(state, onOpenPending) }
            }
            item { InventoryCard(state, onOpenInventory) }
            if (state.followUps.isNotEmpty()) {
                item { FollowUpsCard(state, onOpenContact) }
            }
            item {
                SectionCard(title = "Ingresos vs. gastos · 6 meses") {
                    IncomeExpenseBarChart(state.bars, Modifier.padding(horizontal = 16.dp))
                }
            }
            item {
                SectionCard(title = "Gastos de ${Dates.monthName(state.month).lowercase()}") {
                    if (state.expenseByCategory.isEmpty()) {
                        EmptyState(
                            Icons.Filled.PieChart,
                            "Sin gastos este mes",
                            "Cuando registres gastos verás aquí en qué se va el dinero.",
                        )
                    } else {
                        CategoryDonut(state.expenseByCategory, "gastado", Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
            item {
                SectionCard(
                    title = "Últimos movimientos",
                    action = { TextButton(onClick = onSeeAll) { Text("Ver todos") } },
                ) {
                    if (state.recent.isEmpty()) {
                        EmptyState(
                            Icons.AutoMirrored.Filled.ReceiptLong,
                            "Aún no hay movimientos",
                            "Toca \"Registrar\" para anotar tu primera venta o gasto.",
                        )
                    } else {
                        state.recent.forEach { EntryRow(it, onClick = { onOpenEntry(it.entry.id) }) }
                    }
                }
            }
        }
    }
}

@Composable
private fun BalanceHero(state: DashboardState) {
    val primary = MaterialTheme.colorScheme.primary
    val gradient = Brush.linearGradient(listOf(primary, primary.copy(alpha = 0.78f), Color(0xFF123F36)))
    Box(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(gradient)
            .padding(20.dp)
    ) {
        val onHero = MaterialTheme.colorScheme.onPrimary
        Column {
            Text("Saldo disponible", style = MaterialTheme.typography.labelLarge, color = onHero.copy(alpha = 0.85f))
            Text(
                Money.format(state.totalBalance),
                style = MaterialTheme.typography.displaySmall.merge(AmountStyle),
                color = onHero,
                maxLines = 1,
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HeroStat(Icons.Filled.ArrowDownward, "Ingresos del mes", state.monthIncome, Modifier.weight(1f))
                HeroStat(Icons.Filled.ArrowUpward, "Gastos del mes", state.monthExpense, Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            val result = state.monthResult
            Text(
                (if (result >= 0) "Utilidad del mes: " else "Pérdida del mes: ") + Money.format(kotlin.math.abs(result)),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = onHero,
            )
        }
    }
}

@Composable
private fun HeroStat(icon: ImageVector, label: String, amount: Long, modifier: Modifier) {
    val onHero = MaterialTheme.colorScheme.onPrimary
    Row(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(onHero.copy(alpha = 0.12f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = onHero, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = onHero.copy(alpha = 0.85f))
            Text(Money.format(amount), style = MaterialTheme.typography.titleSmall.merge(AmountStyle), color = onHero, maxLines = 1)
        }
    }
}

@Composable
private fun QuickActions(onNewEntry: (EntryType) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        QuickAction("Venta", Icons.Filled.ArrowDownward, incomeColor(), Modifier.weight(1f)) { onNewEntry(EntryType.INCOME) }
        QuickAction("Gasto", Icons.Filled.ArrowUpward, expenseColor(), Modifier.weight(1f)) { onNewEntry(EntryType.EXPENSE) }
        QuickAction("Transferir", Icons.Filled.SwapHoriz, transferColor(), Modifier.weight(1f)) { onNewEntry(EntryType.TRANSFER) }
    }
}

@Composable
private fun QuickAction(label: String, icon: ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = modifier, shape = MaterialTheme.shapes.large) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IconBadge(icon, color, size = 36.dp)
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

fun accountIcon(type: AccountType): ImageVector = when (type) {
    AccountType.CASH -> Icons.Filled.Payments
    AccountType.BANK -> Icons.Filled.AccountBalance
    AccountType.CARD -> Icons.Filled.CreditCard
    AccountType.OTHER -> Icons.Filled.AccountBalanceWallet
}

@Composable
private fun AccountsRow(accounts: List<AccountWithBalance>, onOpenAccounts: () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Mis cuentas", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onOpenAccounts) { Text("Administrar") }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(accounts, key = { it.account.id }) { a ->
                Card(
                    onClick = onOpenAccounts,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.width(170.dp),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        IconBadge(accountIcon(a.account.type), Color(a.account.color), size = 32.dp)
                        Spacer(Modifier.height(10.dp))
                        Text(a.account.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            Money.format(a.balance),
                            style = MaterialTheme.typography.titleMedium.merge(AmountStyle),
                            color = if (a.balance < 0) expenseColor() else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingSummary(state: DashboardState, onOpenPending: () -> Unit) {
    Card(
        onClick = onOpenPending,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Cuentas pendientes",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.weight(1f),
                )
                if (state.overdueCount > 0) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${state.overdueCount} vencida${if (state.overdueCount == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row {
                Column(Modifier.weight(1f)) {
                    Text("Por cobrar", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    Text(Money.format(state.receivable), style = MaterialTheme.typography.titleMedium.merge(AmountStyle), color = incomeColor())
                }
                Column(Modifier.weight(1f)) {
                    Text("Por pagar", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    Text(Money.format(state.payable), style = MaterialTheme.typography.titleMedium.merge(AmountStyle), color = expenseColor())
                }
            }
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(onClick = onOpenPending) { Text("Gestionar pendientes") }
        }
    }
}

@Composable
private fun InventoryCard(state: DashboardState, onOpenInventory: () -> Unit) {
    Card(onClick = onOpenInventory, shape = MaterialTheme.shapes.large) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Filled.Inventory2, MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Inventario", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (state.lowStock.isEmpty()) "Todo sobre el mínimo"
                    else "Bajo mínimo: " + state.lowStock.take(3).joinToString { it.item.name } + if (state.lowStock.size > 3) "…" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.lowStock.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(Money.format(state.inventoryValue), style = MaterialTheme.typography.titleMedium.merge(AmountStyle))
        }
    }
}

@Composable
private fun FollowUpsCard(state: DashboardState, onOpenContact: (Long) -> Unit) {
    SectionCard(title = "Clientes por contactar esta semana") {
        state.followUps.take(5).forEach { d ->
            val i = d.interaction
            val overdue = (i.followUp ?: 0) < Dates.startOf(Dates.today())
            ListItem(
                modifier = Modifier.clickable { onOpenContact(i.contactId) },
                leadingContent = { IconBadge(Icons.Filled.Event, if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary, size = 34.dp) },
                headlineContent = { Text(d.contactName) },
                supportingContent = { Text(i.note.ifBlank { "Seguimiento" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                trailingContent = {
                    Text(
                        i.followUp?.let { Dates.formatShort(it) }.orEmpty(),
                        color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
            )
        }
    }
}
