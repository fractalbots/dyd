package com.dyd.contable.ui.pending

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dyd.contable.data.AccountingRepository
import com.dyd.contable.data.EntryDetail
import com.dyd.contable.data.EntryType
import com.dyd.contable.util.Dates
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PendingState(
    val receivables: List<EntryDetail> = emptyList(),
    val payables: List<EntryDetail> = emptyList(),
) {
    val totalReceivable: Long get() = receivables.sumOf { it.entry.amount }
    val totalPayable: Long get() = payables.sumOf { it.entry.amount }
}

class PendingViewModel(private val repo: AccountingRepository) : ViewModel() {
    val state: StateFlow<PendingState> = repo.pendingEntries().map { list ->
        PendingState(
            receivables = list.filter { it.entry.type == EntryType.INCOME },
            payables = list.filter { it.entry.type == EntryType.EXPENSE },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PendingState())

    fun markPaid(id: Long) {
        viewModelScope.launch { repo.markPaid(id) }
    }

    companion object {
        /** Días hasta el vencimiento (negativo si ya venció). */
        fun daysUntil(dueDate: Long?): Long? = dueDate?.let {
            java.time.temporal.ChronoUnit.DAYS.between(Dates.today(), Dates.toLocalDate(it))
        }
    }
}
