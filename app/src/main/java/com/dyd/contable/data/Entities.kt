package com.dyd.contable.data


/** Tipo de movimiento contable. */
enum class EntryType { INCOME, EXPENSE, TRANSFER }

enum class AccountType { CASH, BANK, CARD, OTHER }

enum class ContactType { CLIENT, SUPPLIER, BOTH }

/** Cuenta donde vive el dinero: caja, banco, tarjeta... */
data class Account(
    val id: Long = 0,
    val name: String,
    val type: AccountType = AccountType.CASH,
    /** Saldo inicial en centavos. */
    val initialBalance: Long = 0,
    val color: Long = 0xFF0B5D4B,
    val archived: Boolean = false,
)

/** Categoría de ingreso o gasto (el "rubro" contable). */
data class Category(
    val id: Long = 0,
    val name: String,
    /** Solo INCOME o EXPENSE. */
    val type: EntryType,
    val icon: String = "label",
    val color: Long = 0xFF607D8B,
)

/** Cliente o proveedor. */
data class Contact(
    val id: Long = 0,
    val name: String,
    val type: ContactType = ContactType.CLIENT,
    val taxId: String = "",
    val phone: String = "",
    val email: String = "",
    val notes: String = "",
    /** Tipo de identificación del SRI (04 RUC, 05 cédula, 06 pasaporte, 07 consumidor final, 08 exterior). */
    val idType: String = "",
    val address: String = "",
    /** Datos del catastro del SRI (se llenan con «Consultar SRI»). */
    val actividadEconomica: String = "",
    val sriEstado: String = "",
    val sriTipo: String = "",
    val sriRegimen: String = "",
    val sriObligado: String = "",
    /** Respuesta completa del SRI en JSON; null si nunca se consultó. */
    val sriData: String? = null,
    val sriCheckedAt: String? = null,
)

/**
 * Un movimiento: ingreso, gasto o transferencia entre cuentas.
 * Los importes se guardan en centavos para evitar errores de redondeo.
 * Un movimiento no pagado (isPaid = false) es una cuenta por cobrar (ingreso)
 * o por pagar (gasto) y no afecta los saldos hasta que se marque como pagado.
 */
data class Entry(
    val id: Long = 0,
    val type: EntryType,
    val amount: Long,
    val date: Long,
    val accountId: Long,
    val toAccountId: Long? = null,
    val categoryId: Long? = null,
    val contactId: Long? = null,
    val description: String = "",
    val reference: String = "",
    val isPaid: Boolean = true,
    val dueDate: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** IVA incluido en [amount], en centavos: no es ingreso ni gasto, es impuesto. */
    val taxAmount: Long = 0,
    val invoiceId: Long? = null,
    val isInterest: Boolean = false,
) {
    /** Valor sin IVA: el que cuenta en el estado de resultados. */
    val net: Long get() = amount - taxAmount
}

/** Movimiento con los nombres de sus relaciones, listo para mostrar. */
data class EntryDetail(
    val entry: Entry,
    val categoryName: String?,
    val categoryIcon: String?,
    val categoryColor: Long?,
    val accountName: String?,
    val toAccountName: String?,
    val contactName: String?,
)

data class AccountWithBalance(
    val account: Account,
    val balance: Long,
)

data class ContactWithBalance(
    val contact: Contact,
    val receivable: Long,
    val payable: Long,
)

data class CategoryTotal(
    val categoryId: Long,
    val name: String,
    val icon: String,
    val color: Long,
    val total: Long,
)

data class PeriodTotals(
    val income: Long,
    val expense: Long,
)
