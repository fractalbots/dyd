package com.dyd.contable.ui.inventory

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dyd.contable.data.AccountingRepository
import com.dyd.contable.data.BomLine
import com.dyd.contable.data.BomLineDetail
import com.dyd.contable.data.ExplosionRow
import com.dyd.contable.data.Item
import com.dyd.contable.data.ItemKind
import com.dyd.contable.data.ItemWithStock
import com.dyd.contable.data.StockMove
import com.dyd.contable.domain.BomInput
import com.dyd.contable.domain.Costing
import com.dyd.contable.domain.UnitCost
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Línea del kardex con el saldo acumulado. */
data class KardexLine(val move: StockMove, val balance: Double)

data class ItemDetailState(
    val loading: Boolean = true,
    val item: ItemWithStock? = null,
    val bom: List<BomLineDetail> = emptyList(),
    val kardex: List<KardexLine> = emptyList(),
    val usedIn: List<ItemWithStock> = emptyList(),
    val materials: List<ItemWithStock> = emptyList(),
) {
    val unitCost: UnitCost?
        get() = item?.takeIf { it.item.kind == ItemKind.PRODUCT }?.let { p ->
            Costing.unitCost(
                bom.map { BomInput(it.line.quantity, it.line.wastePercent, it.materialCost) },
                p.item.laborCost,
                p.item.overheadCost,
            )
        }
}

class ItemDetailViewModel(savedStateHandle: SavedStateHandle, private val repo: AccountingRepository) : ViewModel() {
    val itemId: Long = savedStateHandle.get<Long>("id") ?: 0L

    private val kardex = repo.moves(itemId).map { moves ->
        var running = 0.0
        moves.map { m -> running += m.quantity; KardexLine(m, running) }.reversed()
    }

    val state: StateFlow<ItemDetailState> = combine(
        repo.item(itemId),
        repo.bom(itemId),
        kardex,
        repo.productsUsing(itemId),
        repo.items(),
    ) { item, bom, lines, usedIn, all ->
        ItemDetailState(
            loading = false,
            item = item,
            bom = bom,
            kardex = lines,
            usedIn = usedIn,
            materials = all.filter { it.item.kind == ItemKind.MATERIAL && it.item.id != itemId },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ItemDetailState())

    var preview by mutableStateOf<List<ExplosionRow>>(emptyList())
        private set
    var working by mutableStateOf(false)
        private set

    fun save(item: Item) {
        viewModelScope.launch { repo.saveItem(item) }
    }

    fun remove(item: Item, onDone: (Boolean) -> Unit) {
        viewModelScope.launch { onDone(repo.removeItem(item)) }
    }

    fun adjust(item: Item, delta: Double, note: String) {
        viewModelScope.launch { repo.adjustStock(item, delta, note) }
    }

    fun saveBomLine(line: BomLine) {
        viewModelScope.launch { repo.saveBomLine(line) }
    }

    fun deleteBomLine(id: Long) {
        viewModelScope.launch { repo.deleteBomLine(id) }
    }

    fun loadPreview(quantity: Double) {
        if (quantity <= 0) {
            preview = emptyList(); return
        }
        viewModelScope.launch { preview = repo.explode(itemId, quantity) }
    }

    fun produce(quantity: Double, date: Long, note: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            working = true
            val ok = repo.produce(itemId, quantity, date, note)
            working = false
            onDone(ok)
        }
    }

    fun uploadImage(item: Item, bytes: ByteArray, extension: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            working = true
            val url = repo.uploadCatalogImage(item.id, bytes, extension)
            val ok = url != null && repo.saveItem(item.copy(imageUrl = url))
            working = false
            onDone(ok)
        }
    }
}
