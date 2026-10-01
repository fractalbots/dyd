@file:OptIn(ExperimentalMaterial3Api::class)

package com.dyd.contable.ui.reports

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dyd.contable.data.CategoryTotal
import com.dyd.contable.ui.AppViewModelProvider
import com.dyd.contable.ui.components.CategoryDonut
import com.dyd.contable.ui.components.CategoryIcons
import com.dyd.contable.ui.components.IconBadge
import com.dyd.contable.ui.components.ProportionBar
import com.dyd.contable.ui.components.ScreenScaffold
import com.dyd.contable.ui.components.SectionCard
import com.dyd.contable.ui.theme.AmountStyle
import com.dyd.contable.ui.theme.expenseColor
import com.dyd.contable.ui.theme.incomeColor
import com.dyd.contable.util.Csv
import com.dyd.contable.util.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun ReportsScreen(viewModel: ReportsViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val csv = Csv.entries(state.entries)
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
                }.isSuccess
            }
            snackbar.showSnackbar(if (ok) "Archivo exportado (${state.entries.size} movimientos)" else "No se pudo exportar el archivo")
        }
    }

    ScreenScaffold(
        title = "Reportes",
        snackbarHostState = snackbar,
        actions = {
            IconButton(onClick = {
                val name = "DYD_movimientos_" + state.period.label.replace(" ", "_").lowercase() + ".csv"
                exporter.launch(name)
            }) {
                Icon(Icons.Filled.FileDownload, contentDescription = "Exportar a Excel (CSV)")
            }
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                val modes = listOf(PeriodMode.MONTH to "Mensual", PeriodMode.YEAR to "Anual")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    modes.forEachIndexed { i, (mode, label) ->
                        SegmentedButton(
                            selected = state.period.mode == mode,
                            onClick = { viewModel.setMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                        ) { Text(label) }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = viewModel::previous) { Icon(Icons.Filled.ChevronLeft, contentDescription = "Anterior") }
                    Text(state.period.label, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    IconButton(onClick = viewModel::next) { Icon(Icons.Filled.ChevronRight, contentDescription = "Siguiente") }
                }
            }
            item { ResultCard(state) }
            item {
                StatementSection(
                    title = "Ingresos",
                    items = state.income,
                    uncategorized = state.uncategorizedIncome,
                    total = state.totalIncome,
                    color = incomeColor(),
                )
            }
            item {
                StatementSection(
                    title = "Gastos",
                    items = state.expense,
                    uncategorized = state.uncategorizedExpense,
                    total = state.totalExpense,
                    color = expenseColor(),
                )
            }
            if (state.products.isNotEmpty()) {
                item { ProductProfitCard(state.products) }
            }
            if (state.expense.isNotEmpty()) {
                item {
                    SectionCard(title = "Distribución de gastos") {
                        CategoryDonut(state.expense, "gastos", Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
            item {
                Text(
                    "El estado de resultados incluye ventas y gastos por la fecha en que ocurrieron, " +
                        "estén cobrados/pagados o pendientes. Las transferencias entre cuentas no son ingresos ni gastos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ResultCard(state: ReportState) {
    SectionCard(title = "Estado de resultados") {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StatementLine("Ingresos totales", state.totalIncome, incomeColor())
            StatementLine("(−) Gastos totales", state.totalExpense, expenseColor())
            HorizontalDivider()
            val net = state.netResult
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (net >= 0) "Utilidad neta" else "Pérdida neta",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    Money.format(net),
                    style = MaterialTheme.typography.titleLarge.merge(AmountStyle),
                    fontWeight = FontWeight.Bold,
                    color = if (net >= 0) incomeColor() else expenseColor(),
                )
            }
            state.margin?.let { m ->
                Text(
                    "Margen neto: " + String.format(Locale.getDefault(), "%.1f", m) + " %",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatementLine(label: String, amount: Long, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(Money.format(amount), style = MaterialTheme.typography.bodyLarge.merge(AmountStyle), color = color)
    }
}

@Composable
private fun StatementSection(
    title: String,
    items: List<CategoryTotal>,
    uncategorized: Long,
    total: Long,
    color: Color,
) {
    SectionCard(title = title, action = {
        Text(
            Money.format(total),
            style = MaterialTheme.typography.titleMedium.merge(AmountStyle),
            color = color,
            modifier = Modifier.padding(end = 8.dp),
        )
    }) {
        if (items.isEmpty() && uncategorized == 0L) {
            Text(
                "Sin registros en este periodo.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        val safeTotal = total.coerceAtLeast(1)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items.forEach { c ->
                CategoryLine(CategoryIcons.of(c.icon), Color(c.color), c.name, c.total, c.total.toFloat() / safeTotal)
            }
            if (uncategorized > 0) {
                CategoryLine(CategoryIcons.of(null), MaterialTheme.colorScheme.outline, "Sin categoría", uncategorized, uncategorized.toFloat() / safeTotal)
            }
        }
    }
}

@Composable
private fun CategoryLine(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    name: String,
    amount: Long,
    fraction: Float,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, color, size = 34.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(Money.format(amount), style = MaterialTheme.typography.bodyMedium.merge(AmountStyle))
            }
            Spacer(Modifier.height(4.dp))
            ProportionBar(fraction, color)
            Text(
                String.format(Locale.getDefault(), "%.1f %%", fraction * 100),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProductProfitCard(products: List<com.dyd.contable.data.ProductSales>) {
    SectionCard(title = "Rentabilidad por producto") {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            products.forEach { p ->
                val margin = if (p.revenue > 0) p.margin * 100.0 / p.revenue else 0.0
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(Money.format(p.margin), style = MaterialTheme.typography.bodyLarge.merge(AmountStyle), color = if (p.margin >= 0) incomeColor() else expenseColor())
                    }
                    Text(
                        com.dyd.contable.util.Quantity.format(p.quantity, p.unit) + " vendidos · ventas " + Money.format(p.revenue) +
                            " · costo " + Money.format(p.cost) + " · margen " + String.format(Locale.getDefault(), "%.1f", margin) + " %",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    ProportionBar((margin / 100).toFloat(), if (p.margin >= 0) incomeColor() else expenseColor())
                }
            }
        }
    }
}
