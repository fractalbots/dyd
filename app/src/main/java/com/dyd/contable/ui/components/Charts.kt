package com.dyd.contable.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dyd.contable.data.CategoryTotal
import com.dyd.contable.ui.theme.expenseColor
import com.dyd.contable.ui.theme.incomeColor
import com.dyd.contable.util.Money
import kotlin.math.max

data class MonthBar(val label: String, val income: Long, val expense: Long)

/** Barras agrupadas de ingresos vs. gastos por mes, animadas al aparecer. */
@Composable
fun IncomeExpenseBarChart(data: List<MonthBar>, modifier: Modifier = Modifier) {
    val income = incomeColor()
    val expense = expenseColor()
    val grid = MaterialTheme.colorScheme.outlineVariant
    val maxValue = max(1L, data.maxOfOrNull { max(it.income, it.expense) } ?: 1L)
    val progress = remember { Animatable(0f) }
    LaunchedEffect(data) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(700))
    }

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            Text(
                Money.compact(maxValue),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .padding(top = 4.dp)
        ) {
            val lines = 4
            repeat(lines + 1) { i ->
                val y = size.height * i / lines
                drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            if (data.isEmpty()) return@Canvas
            val groupWidth = size.width / data.size
            val barWidth = (groupWidth * 0.28f).coerceAtMost(28.dp.toPx())
            val gap = 4.dp.toPx()
            data.forEachIndexed { i, m ->
                val center = groupWidth * i + groupWidth / 2
                val hIn = size.height * (m.income.toFloat() / maxValue) * progress.value
                val hEx = size.height * (m.expense.toFloat() / maxValue) * progress.value
                drawRoundRect(
                    income,
                    topLeft = Offset(center - gap / 2 - barWidth, size.height - hIn),
                    size = Size(barWidth, hIn),
                    cornerRadius = CornerRadius(6.dp.toPx()),
                )
                drawRoundRect(
                    expense,
                    topLeft = Offset(center + gap / 2, size.height - hEx),
                    size = Size(barWidth, hEx),
                    cornerRadius = CornerRadius(6.dp.toPx()),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            data.forEach {
                Text(
                    it.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            LegendDot(income, "Ingresos")
            Spacer(Modifier.width(20.dp))
            LegendDot(expense, "Gastos")
        }
    }
}

@Composable
fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

/** Dona de distribución por categoría con leyenda y porcentajes. */
@Composable
fun CategoryDonut(
    items: List<CategoryTotal>,
    centerLabel: String,
    modifier: Modifier = Modifier,
    maxLegend: Int = 5,
) {
    val total = items.sumOf { it.total }.coerceAtLeast(1)
    val track = MaterialTheme.colorScheme.surfaceVariant
    val progress = remember { Animatable(0f) }
    LaunchedEffect(items) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(800))
    }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(132.dp)) {
                val stroke = 18.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                var start = -90f
                items.forEach { item ->
                    val sweep = 360f * item.total / total * progress.value
                    if (sweep > 0.5f) {
                        drawArc(
                            Color(item.color), start, (sweep - 1.5f).coerceAtLeast(0.5f), false,
                            Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Butt),
                        )
                    }
                    start += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(Money.compact(total), style = MaterialTheme.typography.titleMedium)
                Text(centerLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val shown = items.take(maxLegend)
            val rest = items.drop(maxLegend).sumOf { it.total }
            shown.forEach { LegendRow(Color(it.color), it.name, it.total * 100 / total) }
            if (rest > 0) LegendRow(MaterialTheme.colorScheme.outline, "Otras", rest * 100 / total)
        }
    }
}

@Composable
private fun LegendRow(color: Color, name: String, percent: Long) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(
            name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text("$percent%", style = MaterialTheme.typography.labelMedium)
    }
}

/** Barra horizontal de proporción, usada en reportes. */
@Composable
fun ProportionBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(6.dp)
                .clip(CircleShape)
                .background(color)
        )
    }
}
