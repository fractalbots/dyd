package com.dyd.contable.ui.entries

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dyd.contable.data.AccountingRepository
import com.dyd.contable.data.EntryDetail
import com.dyd.contable.data.EntryType
import com.dyd.contable.util.Dates
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class DayGroup(val date: LocalDate, val entries: List<EntryDetail>, val net: Long)

data class EntriesState(
    val month: YearMonth = YearMonth.now(),
    val query: String = "",
    val filter: EntryType? = null,
    val groups: List<DayGroup> = emptyList(),
    val income: Long = 0,
    val expense: Long = 0,
    val isEmptyMonth: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class EntriesViewModel(private val repo: AccountingRepository) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.now())
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow<EntryType?>(null)

    private val monthEntries = month.flatMapLatest { m ->
        val (from, to) = Dates.monthRange(m)
        repo.entries(from, to)
    }

    val state: StateFlow<EntriesState> = combine(month, query, filter, monthEntries) { m, q, f, all ->
        val needle = q.trim().lowercase()
        val filtered = all.filter { d ->
            (f == null || d.entry.type == f) &&
                (needle.isEmpty() || listOfNotNull(
                    d.entry.description, d.entry.reference, d.categoryName, d.contactName, d.accountName,
                ).any { it.lowercase().contains(needle) })
        }
        EntriesState(
            month = m,
            query = q,
            filter = f,
            groups = filtered
                .groupBy { Dates.toLocalDate(it.entry.date) }
                .map { (date, list) ->
                    DayGroup(date, list, list.sumOf { signed(it) })
                }
                .sortedByDescending { it.date },
            income = filtered.filter { it.entry.type == EntryType.INCOME }.sumOf { it.entry.net },
            expense = filtered.filter { it.entry.type == EntryType.EXPENSE }.sumOf { it.entry.net },
            isEmptyMonth = all.isEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EntriesState())

    fun setMonth(m: YearMonth) = month.update { m }
    fun setQuery(q: String) = query.update { q }
    fun setFilter(t: EntryType?) = filter.update { t }

    fun delete(id: Long) {
        viewModelScope.launch { repo.deleteEntry(id) }
    }

    private fun signed(d: EntryDetail): Long = when (d.entry.type) {
        EntryType.INCOME -> d.entry.amount
        EntryType.EXPENSE -> -d.entry.amount
        EntryType.TRANSFER -> 0
    }
}
