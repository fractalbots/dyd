package com.dyd.contable.util

import com.dyd.contable.data.EntryDetail
import com.dyd.contable.data.EntryType
import java.math.BigDecimal

/** Genera un CSV compatible con Excel / Google Sheets. */
object Csv {
    fun entries(rows: List<EntryDetail>): String = buildString {
        append('﻿') // BOM para que Excel respete las tildes
        appendLine("Fecha;Tipo;Descripción;Categoría;Cuenta;Cuenta destino;Contacto;Referencia;Estado;Vencimiento;Monto")
        rows.sortedBy { it.entry.date }.forEach { r ->
            val e = r.entry
            val sign = if (e.type == EntryType.EXPENSE) -1 else 1
            listOf(
                Dates.isoDate(e.date),
                typeLabel(e.type),
                e.description,
                r.categoryName.orEmpty(),
                r.accountName.orEmpty(),
                r.toAccountName.orEmpty(),
                r.contactName.orEmpty(),
                e.reference,
                if (e.isPaid) "Pagado" else "Pendiente",
                e.dueDate?.let { Dates.isoDate(it) }.orEmpty(),
                BigDecimal.valueOf(sign * e.amount, 2).toPlainString(),
            ).joinTo(this, ";") { escape(it) }
            appendLine()
        }
    }

    fun typeLabel(t: EntryType) = when (t) {
        EntryType.INCOME -> "Ingreso"
        EntryType.EXPENSE -> "Gasto"
        EntryType.TRANSFER -> "Transferencia"
    }

    private fun escape(v: String): String =
        if (v.any { it == ';' || it == '"' || it == '\n' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
}
