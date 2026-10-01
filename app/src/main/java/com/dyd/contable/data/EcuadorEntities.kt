package com.dyd.contable.data

/** Catálogos del SRI usados en facturación. */
object Sri {
    val IVA_CODES = linkedMapOf(
        "4" to "IVA 15 %",
        "0" to "IVA 0 %",
        "5" to "IVA 5 %",
        "6" to "No objeto de IVA",
        "7" to "Exento de IVA",
    )

    fun ivaRate(code: String): Int = when (code) { "4" -> 15; "5" -> 5; else -> 0 }

    val PAYMENT_METHODS = linkedMapOf(
        "01" to "Efectivo (sin sistema financiero)",
        "20" to "Transferencia u otros con sistema financiero",
        "16" to "Tarjeta de débito",
        "19" to "Tarjeta de crédito",
        "17" to "Dinero electrónico",
        "15" to "Compensación de deudas",
        "21" to "Endoso de títulos",
    )

    val REGIMES = linkedMapOf(
        "GENERAL" to "Régimen general",
        "RIMPE_EMPRENDEDOR" to "RIMPE Emprendedor",
        "RIMPE_NEGOCIO_POPULAR" to "RIMPE Negocio popular",
    )
}

/** Datos del emisor para las facturas electrónicas. */
data class Company(
    val ruc: String = "",
    val razonSocial: String = "",
    val nombreComercial: String = "",
    val dirMatriz: String = "",
    val dirEstablecimiento: String = "",
    val estab: String = "001",
    val ptoEmi: String = "001",
    val nextSecuencial: Long = 1,
    /** 1 = pruebas, 2 = producción. */
    val ambiente: Int = 1,
    val obligadoContabilidad: Boolean = false,
    val contribuyenteEspecial: String = "",
    val agenteRetencion: String = "",
    val regimen: String = "GENERAL",
    val email: String = "",
    val phone: String = "",
    /** Interés anual (%) que se cobra a clientes por mora. */
    val lateInterestRate: Double = 0.0,
)

enum class InvoiceStatus(val label: String) {
    PENDIENTE("Sin enviar al SRI"),
    RECIBIDA("Recibida, esperando autorización"),
    DEVUELTA("Devuelta por el SRI"),
    AUTORIZADA("Autorizada"),
    NO_AUTORIZADA("No autorizada"),
    ANULADA("Anulada"),
}

data class SriMessage(val identificador: String, val mensaje: String, val informacionAdicional: String, val tipo: String)

data class Invoice(
    val id: Long,
    val contactId: Long,
    val number: String,
    val date: Long,
    val status: InvoiceStatus,
    val ambiente: Int,
    val claveAcceso: String,
    val buyerName: String,
    val buyerId: String,
    val buyerEmail: String,
    val subtotal: Long,
    val discount: Long,
    val tax: Long,
    val total: Long,
    val paymentMethod: String,
    val termDays: Int,
    val installments: Int,
    val financeRate: Double,
    val note: String,
    val authorizationNumber: String?,
    val authorizedAt: String?,
    val messages: List<SriMessage>,
)

data class InvoiceLine(
    val id: Long,
    val itemId: Long?,
    val code: String,
    val description: String,
    val quantity: Double,
    val unitPrice: Long,
    val discount: Long,
    val ivaCode: String,
    val subtotal: Long,
    val tax: Long,
)

data class Installment(val number: Int, val dueDate: Long, val capital: Long, val interest: Long, val total: Long)

data class InvoiceFull(val invoice: Invoice, val lines: List<InvoiceLine>, val installments: List<Installment>)

/** Línea de una factura que se está armando. */
data class DraftLine(
    val itemId: Long? = null,
    val description: String = "",
    val quantity: Double = 1.0,
    val unitPrice: Long = 0,
    val discount: Long = 0,
    val ivaCode: String = "4",
) {
    val subtotal: Long get() = Math.round(quantity * unitPrice) - discount
}

data class InvoiceDraft(
    val contactId: Long,
    val date: Long,
    val accountId: Long,
    val paymentMethod: String,
    val termDays: Int,
    val installments: Int,
    val financeRate: Double,
    val note: String,
    val lines: List<DraftLine>,
)

/** Resultado del envío al SRI. */
data class SriResult(val status: InvoiceStatus?, val messages: List<SriMessage>, val error: String?, val pending: Boolean)

/** Cuenta por cobrar vencida con el interés de mora acumulado. */
data class LateReceivable(
    val entryId: Long,
    val contactId: Long?,
    val description: String,
    val amount: Long,
    val dueDate: Long,
    val days: Int,
    val daysOverdue: Int,
    val interest: Long,
)

/** Resumen mensual de IVA (base del formulario 104). */
data class IvaMonth(
    val month: Long,
    val salesBase: Long,
    val salesTax: Long,
    val purchasesBase: Long,
    val purchasesTax: Long,
    /** Positivo: IVA a pagar. Negativo: crédito tributario. */
    val taxDue: Long,
    val dueDate: Long,
)

data class SriCharges(val months: Int, val interest: Long, val fine: Long, val total: Long, val missingRates: Boolean)

/** Tasa de interés por mora tributaria de un trimestre (% mensual). */
data class SriRate(val quarterStart: Long, val monthlyRate: Double)
