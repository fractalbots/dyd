package com.dyd.contable.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dyd.contable.data.AccountWithBalance
import com.dyd.contable.data.AccountingRepository
import com.dyd.contable.data.CategoryTotal
import com.dyd.contable.data.EntryDetail
import com.dyd.contable.data.EntryType
import com.dyd.contable.data.InteractionDetail
import com.dyd.contable.data.ItemWithStock
import com.dyd.contable.ui.components.MonthBar
import com.dyd.contable.util.Dates
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth

data class DashboardState(
    val loading: Boolean = true,
    val month: YearMonth = YearMonth.now(),
    val totalBalance: Long = 0,
    val accounts: List<AccountWithBalance> = emptyList(),
    val monthIncome: Long = 0,
    val monthExpense: Long = 0,
    val receivable: Long = 0,
    val payable: Long = 0,
    val overdueCount: Int = 0,
    val bars: List<MonthBar> = emptyList(),
    val expenseByCategory: List<CategoryTotal> = emptyList(),
    val recent: List<EntryDetail> = emptyList(),
    val inventoryValue: Long = 0,
    val lowStock: List<ItemWithStock> = emptyList(),
    val followUps: List<InteractionDetail> = emptyList(),
) {
    val monthResult: Long get() = monthIncome - monthExpense
}

class DashboardViewModel(repo: AccountingRepository) : ViewModel() {
    private val month = YearMonth.now()
    private val monthRange = Dates.monthRange(month)
    private val firstChartMonth = month.minusMonths(5)
    private val chartRange = Dates.startOf(firstChartMonth.atDay(1)) to monthRange.second

    private val base = combine(
        repo.accountsWithBalance(),
        repo.totals(monthRange.first, monthRange.second),
        repo.pendingEntries(),
        repo.entries(chartRange.first, chartRange.second),
        repo.totalsByCategory(EntryType.EXPENSE, monthRange.first, monthRange.second),
    ) { accounts, totals, pending, chartEntries, byCategory ->
        val today = Dates.startOf(Dates.today())
        DashboardState(
            loading = false,
            month = month,
            totalBalance = accounts.sumOf { it.balance },
            accounts = accounts,
            monthIncome = totals.income,
            monthExpense = totals.expense,
            receivable = pending.filter { it.entry.type == EntryType.INCOME }.sumOf { it.entry.amount },
            payable = pending.filter { it.entry.type == EntryType.EXPENSE }.sumOf { it.entry.amount },
            overdueCount = pending.count { (it.entry.dueDate ?: Long.MAX_VALUE) < today },
            bars = (0..5).map { offset ->
                val m = firstChartMonth.plusMonths(offset.toLong())
                val inMonth = chartEntries.filter { YearMonth.from(Dates.toLocalDate(it.entry.date)) == m }
                MonthBar(
                    label = Dates.monthShort(m),
                    income = inMonth.filter { it.entry.type == EntryType.INCOME }.sumOf { it.entry.net },
                    expense = inMonth.filter { it.entry.type == EntryType.EXPENSE }.sumOf { it.entry.net },
                )
            },
            expenseByCategory = byCategory,
        )
    }

    val state: StateFlow<DashboardState> = combine(
        base, repo.recentEntries(6), repo.items(), repo.pendingFollowUps(),
    ) { s, recent, items, followUps ->
        val limit = Dates.endOf(Dates.today().plusDays(7))
        s.copy(
            recent = recent,
            inventoryValue = items.sumOf { it.value },
            lowStock = items.filter { it.isLow },
            followUps = followUps.filter { (it.interaction.followUp ?: Long.MAX_VALUE) <= limit },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState())
}
