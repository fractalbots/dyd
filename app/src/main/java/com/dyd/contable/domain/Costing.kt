package com.dyd.contable.domain

import kotlin.math.roundToLong

/** Un material dentro de la lista de materiales de un producto. */
data class BomInput(
    val quantityPerUnit: Double,
    val wastePercent: Double,
    /** Costo unitario del material en centavos. */
    val unitCost: Long,
)

/** Costo de fabricar una unidad de producto, desglosado. */
data class UnitCost(
    val materials: Long,
    val labor: Long,
    val overhead: Long,
) {
    val total: Long get() = materials + labor + overhead

    /** Utilidad por unidad al precio dado. */
    fun profitAt(price: Long): Long = price - total

    /** Margen sobre el precio de venta, en porcentaje (null si no hay precio). */
    fun marginAt(price: Long): Double? = if (price > 0) (price - total) * 100.0 / price else null

    /** Precio necesario para lograr un margen objetivo (en %) sobre el precio de venta. */
    fun priceForMargin(marginPercent: Double): Long? =
        if (marginPercent < 100) (total / (1 - marginPercent / 100)).roundToLong() else null
}

object Costing {
    /** Cantidad real que se consume de un material, incluyendo desperdicio. */
    fun consumption(quantityPerUnit: Double, wastePercent: Double, units: Double): Double =
        quantityPerUnit * (1 + wastePercent / 100) * units

    fun unitCost(bom: List<BomInput>, laborPerUnit: Long, overheadPerUnit: Long): UnitCost = UnitCost(
        materials = bom.sumOf { (consumption(it.quantityPerUnit, it.wastePercent, 1.0) * it.unitCost).roundToLong() },
        labor = laborPerUnit,
        overhead = overheadPerUnit,
    )

    /** Costo promedio ponderado después de una entrada de inventario (igual que en la base de datos). */
    fun weightedAverage(stockBefore: Double, costBefore: Long, quantityIn: Double, costIn: Long): Long {
        val base = stockBefore.coerceAtLeast(0.0)
        if (base + quantityIn <= 0) return costIn
        return ((base * costBefore + quantityIn * costIn) / (base + quantityIn)).roundToLong()
    }
}
