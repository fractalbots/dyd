package com.dyd.contable.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dyd.contable.data.AccountingRepository
import com.dyd.contable.data.CategoryTotal
import com.dyd.contable.data.EntryDetail
import com.dyd.contable.data.EntryType
import com.dyd.contable.data.ProductSales
import com.dyd.contable.util.Dates
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.YearMonth

enum class PeriodMode { MONTH, YEAR }

data class Period(val mode: PeriodMode = PeriodMode.MONTH, val month: YearMonth = YearMonth.now()) {
    val range: Pair<Long, Long>
        get() = if (mode == PeriodMode.MONTH) Dates.monthRange(month) else Dates.yearRange(month.year)
    val label: String
        get() = if (mode == PeriodMode.MONTH) Dates.monthName(month) else "Año ${month.year}"

    fun previous() = copy(month = if (mode == PeriodMode.MONTH) month.minusMonths(1) else month.minusYears(1))
    fun next() = copy(month = if (mode == PeriodMode.MONTH) month.plusMonths(1) else month.plusYears(1))
}

data class ReportState(
    val period: Period = Period(),
    val income: List<CategoryTotal> = emptyList(),
    val expense: List<CategoryTotal> = emptyList(),
    val totalIncome: Long = 0,
    val totalExpense: Long = 0,
    val uncategorizedIncome: Long = 0,
    val uncategorizedExpense: Long = 0,
    val entries: List<EntryDetail> = emptyList(),
    val products: List<ProductSales> = emptyList(),
) {
    val netResult: Long get() = totalIncome - totalExpense
    /** Margen neto en porcentaje (utilidad / ingresos). */
    val margin: Double? get() = if (totalIncome > 0) netResult * 100.0 / totalIncome else null
}

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModel(private val repo: AccountingRepository) : ViewModel() {
    private val period = MutableStateFlow(Period())

    val state: StateFlow<ReportState> = period.flatMapLatest { p ->
        val (from, to) = p.range
        combine(
            repo.totalsByCategory(EntryType.INCOME, from, to),
            repo.totalsByCategory(EntryType.EXPENSE, from, to),
            repo.totals(from, to),
            repo.entries(from, to),
            repo.productSales(from, to),
        ) { income, expense, totals, entries, products ->
            ReportState(
                period = p,
                income = income,
                expense = expense,
                totalIncome = totals.income,
                totalExpense = totals.expense,
                uncategorizedIncome = totals.income - income.sumOf { it.total },
                uncategorizedExpense = totals.expense - expense.sumOf { it.total },
                entries = entries,
                products = products,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportState())

    fun setMode(mode: PeriodMode) = period.update { it.copy(mode = mode) }
    fun previous() = period.update { it.previous() }
    fun next() = period.update { it.next() }
}
