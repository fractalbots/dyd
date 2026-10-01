package com.dyd.contable.data.remote

import com.dyd.contable.data.AccountType
import com.dyd.contable.data.AppRole
import com.dyd.contable.data.ContactType
import com.dyd.contable.data.EntryType
import com.dyd.contable.data.InteractionKind
import com.dyd.contable.data.ItemKind
import com.dyd.contable.data.StockMoveType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Filas tal como las devuelve Supabase (nombres de columna en snake_case).

@Serializable
data class NameRef(val name: String = "", val unit: String = "", val icon: String = "label", val color: Long = 0xFF607D8B)

@Serializable
data class AccountRow(
    val id: Long,
    val name: String,
    val type: AccountType,
    @SerialName("initial_balance") val initialBalance: Long,
    val color: Long,
    val archived: Boolean,
    val balance: Long = 0,
)

@Serializable
data class CategoryRow(val id: Long, val name: String, val type: EntryType, val icon: String, val color: Long)

@Serializable
data class ContactRow(
    val id: Long,
    val name: String,
    val type: ContactType,
    @SerialName("tax_id") val taxId: String = "",
    val phone: String = "",
    val email: String = "",
    val notes: String = "",
    @SerialName("id_type") val idType: String = "",
    val address: String = "",
    @SerialName("actividad_economica") val actividadEconomica: String = "",
    @SerialName("sri_estado") val sriEstado: String = "",
    @SerialName("sri_tipo") val sriTipo: String = "",
    @SerialName("sri_regimen") val sriRegimen: String = "",
    @SerialName("sri_obligado_contabilidad") val sriObligado: String = "",
    @SerialName("sri_data") val sriData: kotlinx.serialization.json.JsonElement? = null,
    @SerialName("sri_checked_at") val sriCheckedAt: String? = null,
)

@Serializable
data class ContactStatsRow(
    @SerialName("contact_id") val contactId: Long,
    val sales: Long,
    val expenses: Long,
    val cogs: Long,
    val receivable: Long,
    val payable: Long,
)

@Serializable
data class EntryRow(
    val id: Long,
    val type: EntryType,
    val amount: Long,
    val date: String,
    @SerialName("account_id") val accountId: Long,
    @SerialName("to_account_id") val toAccountId: Long? = null,
    @SerialName("category_id") val categoryId: Long? = null,
    @SerialName("contact_id") val contactId: Long? = null,
    val description: String = "",
    val reference: String = "",
    @SerialName("is_paid") val isPaid: Boolean = true,
    @SerialName("due_date") val dueDate: String? = null,
    @SerialName("tax_amount") val taxAmount: Long = 0,
    @SerialName("invoice_id") val invoiceId: Long? = null,
    @SerialName("is_interest") val isInterest: Boolean = false,
    val category: NameRef? = null,
    val account: NameRef? = null,
    @SerialName("to_account") val toAccount: NameRef? = null,
    val contact: NameRef? = null,
)

@Serializable
data class ItemRow(
    val id: Long,
    val name: String,
    val kind: ItemKind,
    val unit: String = "und",
    val sku: String = "",
    @SerialName("unit_cost") val unitCost: Long = 0,
    @SerialName("sale_price") val salePrice: Long = 0,
    @SerialName("min_stock") val minStock: Double = 0.0,
    @SerialName("labor_cost") val laborCost: Long = 0,
    @SerialName("overhead_cost") val overheadCost: Long = 0,
    val archived: Boolean = false,
    @SerialName("is_public") val isPublic: Boolean = false,
    val description: String = "",
    val dimensions: String = "",
    @SerialName("image_url") val imageUrl: String = "",
    @SerialName("iva_code") val ivaCode: String = "4",
    val stock: Double = 0.0,
)

@Serializable
data class BomRow(
    val id: Long,
    @SerialName("product_id") val productId: Long,
    @SerialName("material_id") val materialId: Long,
    val quantity: Double,
    @SerialName("waste_percent") val wastePercent: Double = 0.0,
)

@Serializable
data class StockMoveRow(
    val id: Long,
    @SerialName("item_id") val itemId: Long,
    val type: StockMoveType,
    val quantity: Double,
    @SerialName("unit_cost") val unitCost: Long = 0,
    @SerialName("unit_price") val unitPrice: Long = 0,
    val date: String,
    @SerialName("entry_id") val entryId: Long? = null,
    @SerialName("production_id") val productionId: Long? = null,
    @SerialName("contact_id") val contactId: Long? = null,
    val note: String = "",
    val item: NameRef? = null,
)

@Serializable
data class ProductionRow(
    val id: Long,
    @SerialName("product_id") val productId: Long,
    val quantity: Double,
    val date: String,
    @SerialName("material_cost") val materialCost: Long,
    @SerialName("labor_cost") val laborCost: Long,
    @SerialName("overhead_cost") val overheadCost: Long,
    val note: String = "",
    @SerialName("quality_status") val qualityStatus: String = "PENDIENTE",
    @SerialName("quality_note") val qualityNote: String = "",
    val product: NameRef? = null,
)

@Serializable
data class ExplosionDto(
    @SerialName("material_id") val materialId: Long,
    @SerialName("material_name") val materialName: String,
    val unit: String,
    val required: Double,
    val available: Double,
    val shortage: Double,
    @SerialName("unit_cost") val unitCost: Long,
    val cost: Long,
)

@Serializable
data class InteractionRow(
    val id: Long,
    @SerialName("contact_id") val contactId: Long,
    val kind: InteractionKind,
    val date: String,
    val note: String = "",
    @SerialName("follow_up") val followUp: String? = null,
    val done: Boolean = false,
    val contact: NameRef? = null,
)

@Serializable
data class ProfileRow(
    val id: String,
    @SerialName("full_name") val fullName: String = "",
    val role: AppRole,
    val active: Boolean = true,
)

// ------------------------------------------------------------------ Ecuador / SRI

@Serializable
data class CompanyRow(
    val ruc: String = "",
    @SerialName("razon_social") val razonSocial: String = "",
    @SerialName("nombre_comercial") val nombreComercial: String = "",
    @SerialName("dir_matriz") val dirMatriz: String = "",
    @SerialName("dir_establecimiento") val dirEstablecimiento: String = "",
    val estab: String = "001",
    @SerialName("pto_emi") val ptoEmi: String = "001",
    @SerialName("next_secuencial") val nextSecuencial: Long = 1,
    val ambiente: Int = 1,
    @SerialName("obligado_contabilidad") val obligadoContabilidad: Boolean = false,
    @SerialName("contribuyente_especial") val contribuyenteEspecial: String = "",
    @SerialName("agente_retencion") val agenteRetencion: String = "",
    val regimen: String = "GENERAL",
    val email: String = "",
    val phone: String = "",
    @SerialName("late_interest_rate") val lateInterestRate: Double = 0.0,
)

@Serializable
data class SriMessageRow(
    val identificador: String = "",
    val mensaje: String = "",
    val informacionAdicional: String = "",
    val tipo: String = "",
)

@Serializable
data class InvoiceLineRow(
    val id: Long,
    @SerialName("item_id") val itemId: Long? = null,
    val code: String = "",
    val description: String,
    val quantity: Double,
    @SerialName("unit_price") val unitPrice: Long,
    val discount: Long = 0,
    @SerialName("iva_code") val ivaCode: String,
    val subtotal: Long,
    val tax: Long,
)

@Serializable
data class InstallmentRow(
    val number: Int,
    @SerialName("due_date") val dueDate: String,
    val capital: Long,
    val interest: Long,
    val total: Long,
)

@Serializable
data class InvoiceRow(
    val id: Long,
    @SerialName("contact_id") val contactId: Long,
    val estab: String,
    @SerialName("pto_emi") val ptoEmi: String,
    val secuencial: Long,
    @SerialName("fecha_emision") val fechaEmision: String,
    val ambiente: Int,
    @SerialName("clave_acceso") val claveAcceso: String,
    val status: String,
    @SerialName("buyer_name") val buyerName: String,
    @SerialName("buyer_id") val buyerId: String,
    @SerialName("buyer_email") val buyerEmail: String = "",
    val subtotal: Long,
    val discount: Long = 0,
    val tax: Long,
    val total: Long,
    @SerialName("payment_method") val paymentMethod: String = "01",
    @SerialName("term_days") val termDays: Int = 0,
    val installments: Int = 1,
    @SerialName("finance_rate") val financeRate: Double = 0.0,
    val note: String = "",
    @SerialName("authorization_number") val authorizationNumber: String? = null,
    @SerialName("authorized_at") val authorizedAt: String? = null,
    @SerialName("sri_messages") val sriMessages: List<SriMessageRow> = emptyList(),
    val lines: List<InvoiceLineRow> = emptyList(),
    @SerialName("cuotas") val installmentRows: List<InstallmentRow> = emptyList(),
)

@Serializable
data class SriResultDto(
    val status: String? = null,
    val messages: List<SriMessageRow> = emptyList(),
    val error: String? = null,
    val pending: Boolean = false,
)

@Serializable
data class LateReceivableRow(
    @SerialName("entry_id") val entryId: Long,
    @SerialName("contact_id") val contactId: Long? = null,
    val description: String = "",
    val amount: Long,
    @SerialName("due_date") val dueDate: String,
    val days: Int,
    @SerialName("days_overdue") val daysOverdue: Int,
    val interest: Long,
)

@Serializable
data class IvaMonthRow(
    val month: String,
    @SerialName("sales_base") val salesBase: Long,
    @SerialName("sales_tax") val salesTax: Long,
    @SerialName("purchases_base") val purchasesBase: Long,
    @SerialName("purchases_tax") val purchasesTax: Long,
    @SerialName("tax_due") val taxDue: Long,
    @SerialName("due_date") val dueDate: String,
)

@Serializable
data class SriChargesRow(
    val months: Int,
    val interest: Long,
    val fine: Long,
    val total: Long,
    @SerialName("missing_rates") val missingRates: Boolean,
)

@Serializable
data class SriRateRow(
    @SerialName("quarter_start") val quarterStart: String,
    @SerialName("monthly_rate") val monthlyRate: Double,
)
