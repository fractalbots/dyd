package com.dyd.contable.data


/** Material (materia prima) o producto terminado. */
enum class ItemKind { MATERIAL, PRODUCT }

enum class StockMoveType { PURCHASE, SALE, PRODUCTION_IN, PRODUCTION_OUT, ADJUSTMENT }

enum class InteractionKind { CALL, VISIT, QUOTE, MESSAGE, OTHER }

/**
 * Artículo de inventario. El stock no se guarda aquí: se calcula sumando los movimientos
 * de inventario (kardex), así nunca se descuadra.
 */
data class Item(
    val id: Long = 0,
    val name: String,
    val kind: ItemKind,
    val unit: String = "und",
    val sku: String = "",
    /** Costo promedio ponderado por unidad, en centavos. */
    val unitCost: Long = 0,
    /** Precio de venta sugerido por unidad (productos), en centavos. */
    val salePrice: Long = 0,
    val minStock: Double = 0.0,
    /** Mano de obra por unidad fabricada (productos), en centavos. */
    val laborCost: Long = 0,
    /** Costos indirectos de fabricación por unidad (energía, desgaste...), en centavos. */
    val overheadCost: Long = 0,
    val archived: Boolean = false,
    // Catálogo web
    val isPublic: Boolean = false,
    val description: String = "",
    val dimensions: String = "",
    val imageUrl: String = "",
    /** Código de IVA del SRI: 4 = 15 %, 0 = 0 %, 5 = 5 %, 6 = no objeto, 7 = exento. */
    val ivaCode: String = "4",
)

/** Una línea de la lista de materiales (BOM): cuánto material lleva una unidad de producto. */
data class BomLine(
    val id: Long = 0,
    val productId: Long,
    val materialId: Long,
    val quantity: Double,
    /** Porcentaje de desperdicio que se suma al consumo. */
    val wastePercent: Double = 0.0,
)

/** Movimiento de inventario (una línea del kardex). La cantidad es positiva si entra y negativa si sale. */
data class StockMove(
    val id: Long = 0,
    val itemId: Long,
    val type: StockMoveType,
    val quantity: Double,
    /** Costo unitario del movimiento en centavos. */
    val unitCost: Long,
    /** Precio unitario de venta en centavos (solo ventas). */
    val unitPrice: Long = 0,
    val date: Long,
    val entryId: Long? = null,
    val productionId: Long? = null,
    val contactId: Long? = null,
    val note: String = "",
)

/** Orden de producción ya fabricada, con su costo real. */
data class ProductionOrder(
    val id: Long = 0,
    val productId: Long,
    val quantity: Double,
    val date: Long,
    val materialCost: Long,
    val laborCost: Long,
    val overheadCost: Long,
    val note: String = "",
    val qualityStatus: QualityStatus = QualityStatus.PENDIENTE,
    val qualityNote: String = "",
) {
    val totalCost: Long get() = materialCost + laborCost + overheadCost
    val unitCost: Long get() = if (quantity > 0) Math.round(totalCost / quantity) else 0
}

enum class QualityStatus { PENDIENTE, APROBADO, RECHAZADO }

/** Seguimiento comercial con un cliente o proveedor (CRM). */
data class Interaction(
    val id: Long = 0,
    val contactId: Long,
    val kind: InteractionKind,
    val date: Long,
    val note: String,
    val followUp: Long? = null,
    val done: Boolean = false,
)

data class ItemWithStock(
    val item: Item,
    val stock: Double,
) {
    val value: Long get() = if (stock > 0) Math.round(stock * item.unitCost) else 0
    val isLow: Boolean get() = item.minStock > 0 && stock < item.minStock
}

data class BomLineDetail(
    val line: BomLine,
    val materialName: String,
    val unit: String,
    val materialCost: Long,
    val materialStock: Double,
)

data class ProductionDetail(
    val order: ProductionOrder,
    val productName: String,
    val unit: String,
)

data class InteractionDetail(
    val interaction: Interaction,
    val contactName: String,
)

/** Ventas de un producto en un periodo, con su costo de lo vendido. */
data class ProductSales(
    val itemId: Long,
    val name: String,
    val unit: String,
    val quantity: Double,
    val revenue: Long,
    val cost: Long,
) {
    val margin: Long get() = revenue - cost
}

/** Indicadores comerciales de un cliente o proveedor. */
data class ContactStats(
    val sales: Long,
    val expenses: Long,
    val cogs: Long,
    val receivable: Long,
    val payable: Long,
)

/** Una fila de la explosión de materiales. */
data class ExplosionRow(
    val materialId: Long,
    val materialName: String,
    val unit: String,
    val required: Double,
    val available: Double,
    val shortage: Double,
    val unitCost: Long,
    val cost: Long,
)

enum class AppRole { admin, contador, vendedor, bodega, produccion, calidad, id, auditor, compras }

/** Usuario de la app con su rol. */
data class Profile(
    val id: String,
    val fullName: String,
    val role: AppRole,
    val active: Boolean,
) {
    private fun isAny(vararg roles: AppRole) = active && role in roles

    // Mismas reglas que las funciones perm_* de supabase/schema.sql; la base de datos es la que manda.
    val isAdmin: Boolean get() = isAny(AppRole.admin)
    val canManageAccounting: Boolean get() = isAny(AppRole.admin, AppRole.contador)
    val canReadAccounting: Boolean get() = isAny(AppRole.admin, AppRole.contador, AppRole.auditor)
    /** Ve la pestaña de movimientos: contabilidad completa o sus propias ventas. */
    val canSeeEntries: Boolean get() = isAny(AppRole.admin, AppRole.contador, AppRole.vendedor, AppRole.auditor, AppRole.compras)
    val canCreateEntries: Boolean get() = isAny(AppRole.admin, AppRole.contador, AppRole.vendedor, AppRole.compras)
    val canReadCrm: Boolean get() = isAny(AppRole.admin, AppRole.contador, AppRole.vendedor, AppRole.auditor, AppRole.compras)
    /** Crear y editar clientes con su RUC: contabilidad. Compras solo proveedores. */
    val canWriteContacts: Boolean get() = isAny(AppRole.admin, AppRole.contador)
    val canWriteSuppliers: Boolean get() = isAny(AppRole.admin, AppRole.contador, AppRole.compras)
    val canPurchase: Boolean get() = isAny(AppRole.admin, AppRole.contador, AppRole.compras)
    val canInvoice: Boolean get() = isAny(AppRole.admin, AppRole.contador, AppRole.vendedor)
    val canSeeInvoices: Boolean get() = isAny(AppRole.admin, AppRole.contador, AppRole.vendedor, AppRole.auditor)
    /** Tipos de movimiento que puede registrar. */
    val allowedEntryTypes: List<EntryType> get() = when {
        canManageAccounting -> EntryType.entries
        role == AppRole.vendedor -> listOf(EntryType.INCOME)
        role == AppRole.compras -> listOf(EntryType.EXPENSE)
        else -> emptyList()
    }
    val canEditItems: Boolean get() = isAny(AppRole.admin, AppRole.contador, AppRole.id)
    /** Precio de venta del producto terminado: lo asigna contabilidad (I+D asigna costos). */
    val canSetPrices: Boolean get() = isAny(AppRole.admin, AppRole.contador)
    val canProduce: Boolean get() = isAny(AppRole.admin, AppRole.contador, AppRole.produccion)
    val canAdjustStock: Boolean get() = isAny(AppRole.admin, AppRole.contador, AppRole.bodega)
    val canInspect: Boolean get() = isAny(AppRole.admin, AppRole.calidad)
}
