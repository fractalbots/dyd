package com.dyd.contable.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/** Utilidades para trabajar con dinero guardado en centavos. */
object Money {
    /** Ecuador: montos en dólares con el formato local. */
    val LOCALE: Locale = Locale("es", "EC")

    private val currency: NumberFormat
        get() = NumberFormat.getCurrencyInstance(LOCALE).apply { currency = Currency.getInstance("USD") }

    fun format(cents: Long): String = currency.format(BigDecimal.valueOf(cents, 2))

    /** Formato compacto para gráficos: 1,2 M, 350 k... */
    fun compact(cents: Long): String {
        val value = cents / 100.0
        val abs = kotlin.math.abs(value)
        val nf = NumberFormat.getNumberInstance(LOCALE).apply { maximumFractionDigits = 1 }
        return when {
            abs >= 1_000_000_000 -> nf.format(value / 1_000_000_000) + " B"
            abs >= 1_000_000 -> nf.format(value / 1_000_000) + " M"
            abs >= 1_000 -> nf.format(value / 1_000) + " k"
            else -> nf.format(value)
        }
    }

    /** Texto editable (sin símbolo de moneda) a partir de centavos. */
    fun toInput(cents: Long): String {
        if (cents == 0L) return ""
        val bd = BigDecimal.valueOf(cents, 2).stripTrailingZeros()
        return bd.toPlainString()
    }

    /**
     * Convierte lo que escribe el usuario a centavos. Acepta "1234", "1.234,56", "1,234.56",
     * "1234.5" o "$ 1.500". Devuelve null si no es un número válido.
     */
    fun parse(input: String): Long? = try {
        parseDecimal(input)?.setScale(2, RoundingMode.HALF_UP)?.movePointRight(2)?.longValueExact()
    } catch (e: ArithmeticException) {
        null
    }

    /** Lee un número escrito con cualquier separador decimal o de miles. */
    fun parseDecimal(input: String): BigDecimal? {
        var s = input.filter { it.isDigit() || it == '.' || it == ',' || it == '-' }
        if (s.isEmpty() || s == "-") return null
        val lastDot = s.lastIndexOf('.')
        val lastComma = s.lastIndexOf(',')
        s = when {
            lastDot >= 0 && lastComma >= 0 -> {
                val decimalSep = if (lastDot > lastComma) '.' else ','
                val thousandSep = if (decimalSep == '.') ',' else '.'
                s.replace(thousandSep.toString(), "").replace(decimalSep, '.')
            }
            lastDot >= 0 || lastComma >= 0 -> {
                val sep = if (lastDot >= 0) '.' else ','
                val count = s.count { it == sep }
                val decimals = s.length - s.lastIndexOf(sep) - 1
                val intPart = s.substringBefore(sep).trimStart('-')
                if (count == 1 && (decimals in 0..2 || intPart.isEmpty() || intPart == "0")) s.replace(sep, '.')
                else s.replace(sep.toString(), "")
            }
            else -> s
        }
        return try {
            BigDecimal(s)
        } catch (e: NumberFormatException) {
            null
        }
    }
}
