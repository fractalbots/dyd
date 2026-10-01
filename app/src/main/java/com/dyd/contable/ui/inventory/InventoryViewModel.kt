package com.dyd.contable.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dyd.contable.data.AccountingRepository
import com.dyd.contable.data.Item
import com.dyd.contable.data.ItemKind
import com.dyd.contable.data.ItemWithStock
import com.dyd.contable.data.ProductionDetail
import com.dyd.contable.data.QualityStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class InventoryState(
    val loading: Boolean = true,
    val products: List<ItemWithStock> = emptyList(),
    val materials: List<ItemWithStock> = emptyList(),
    val production: List<ProductionDetail> = emptyList(),
) {
    val totalValue: Long get() = products.sumOf { it.value } + materials.sumOf { it.value }
    val lowCount: Int get() = (products + materials).count { it.isLow }
}

class InventoryViewModel(private val repo: AccountingRepository) : ViewModel() {
    val state: StateFlow<InventoryState> = combine(repo.items(), repo.production()) { items, production ->
        InventoryState(
            loading = false,
            products = items.filter { it.item.kind == ItemKind.PRODUCT },
            materials = items.filter { it.item.kind == ItemKind.MATERIAL },
            production = production,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InventoryState())

    fun create(item: Item, initialStock: Double) {
        viewModelScope.launch { repo.saveItem(item, initialStock) }
    }

    fun inspect(id: Long, status: QualityStatus, note: String) {
        viewModelScope.launch { repo.inspectProduction(id, status, note) }
    }

    fun deleteProduction(id: Long) {
        viewModelScope.launch { repo.deleteProduction(id) }
    }

    fun refresh() = repo.refresh()
}
