package com.dyd.contable.util

import java.text.NumberFormat
import java.util.Locale

/** Cantidades de inventario (admiten decimales: 2,5 kg). */
object Quantity {
    fun format(value: Double, unit: String? = null): String {
        val nf = NumberFormat.getNumberInstance(Locale.getDefault()).apply { maximumFractionDigits = 3 }
        return nf.format(value) + (if (unit.isNullOrBlank()) "" else " $unit")
    }

    fun toInput(value: Double): String =
        if (value == 0.0) "" else java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

    /** Acepta "1.234,5", "1234.5" o "0,125" (hasta 3 decimales). */
    fun parse(input: String): Double? =
        Money.parseDecimal(input)?.setScale(3, java.math.RoundingMode.HALF_UP)?.toDouble()
}
