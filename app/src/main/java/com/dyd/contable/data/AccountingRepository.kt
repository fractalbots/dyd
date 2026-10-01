package com.dyd.contable.data

import com.dyd.contable.data.remote.AccountRow
import com.dyd.contable.data.remote.BomRow
import com.dyd.contable.data.remote.CompanyRow
import com.dyd.contable.data.remote.InvoiceRow
import com.dyd.contable.data.remote.IvaMonthRow
import com.dyd.contable.data.remote.LateReceivableRow
import com.dyd.contable.data.remote.SriChargesRow
import com.dyd.contable.data.remote.SriRateRow
import com.dyd.contable.data.remote.SriResultDto
import com.dyd.contable.data.remote.CategoryRow
import com.dyd.contable.data.remote.ContactRow
import com.dyd.contable.data.remote.ContactStatsRow
import com.dyd.contable.data.remote.EntryRow
import com.dyd.contable.data.remote.ExplosionDto
import com.dyd.contable.data.remote.InteractionRow
import com.dyd.contable.data.remote.ItemRow
import com.dyd.contable.data.remote.ProductionRow
import com.dyd.contable.data.remote.ProfileRow
import com.dyd.contable.data.remote.StockMoveRow
import com.dyd.contable.util.Dates
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate

/**
 * Punto único de acceso a los datos (Supabase) para los ViewModels.
 * Las lecturas son Flows que se vuelven a consultar cada vez que algo cambia ([refresh]).
 * Los errores no rompen la app: se publican en [errors] y la UI los muestra.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountingRepository(private val client: SupabaseClient) {
    private val tick = MutableStateFlow(0L)
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    private val _profile = MutableStateFlow<Profile?>(null)
    /** Perfil (rol) del usuario con sesión iniciada. */
    val profile: StateFlow<Profile?> = _profile.asStateFlow()

    fun refresh() = tick.update { it + 1 }

    private fun report(e: Throwable) {
        val msg = e.message.orEmpty()
        _errors.tryEmit(
            when {
                "row-level security" in msg || "permission" in msg -> "No tienes permiso para esta acción."
                "Unable to resolve host" in msg || "timeout" in msg.lowercase() -> "Sin conexión a internet. Intenta de nuevo."
                "Invalid login" in msg -> "Correo o contraseña incorrectos."
                else -> msg.lineSequence().firstOrNull { it.isNotBlank() }?.take(160) ?: "Ocurrió un error inesperado."
            }
        )
    }

    private fun <T> live(fallback: T, load: suspend () -> T): Flow<T> = tick.flatMapLatest {
        flow {
            val value = try {
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                report(e); fallback
            }
            emit(value)
        }
    }

    /** Ejecuta una escritura; devuelve true si salió bien y refresca las pantallas. */
    private suspend fun write(block: suspend () -> Unit): Boolean = try {
        block(); refresh(); true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        report(e); false
    }

    private suspend fun <T> read(fallback: T, block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        report(e); fallback
    }

    // ------------------------------------------------------------------ Sesión
    val sessionStatus: StateFlow<SessionStatus> get() = client.auth.sessionStatus

    suspend fun signIn(email: String, password: String): Boolean = try {
        client.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
        loadProfile()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        report(e); false
    }

    suspend fun signOut() {
        runCatching { client.auth.signOut() }
        _profile.value = null
    }

    suspend fun sendPasswordReset(email: String): Boolean = write { client.auth.resetPasswordForEmail(email.trim()) }

    suspend fun changePassword(newPassword: String): Boolean = write { client.auth.updateUser { password = newPassword } }

    suspend fun loadProfile() {
        val uid = client.auth.currentUserOrNull()?.id ?: return
        _profile.value = read(null) {
            client.from("profiles").select { filter { eq("id", uid) } }.decodeSingleOrNull<ProfileRow>()?.toModel()
        }
    }

    fun profiles(): Flow<List<Profile>> = live(emptyList()) {
        client.from("profiles").select { order("full_name", Order.ASCENDING) }.decodeList<ProfileRow>().map { it.toModel() }
    }

    suspend fun updateProfile(profile: Profile): Boolean = write {
        client.from("profiles").update(buildJsonObject {
            put("full_name", profile.fullName)
            put("role", profile.role.name)
            put("active", profile.active)
        }) { filter { eq("id", profile.id) } }
    }

    /** Crea un usuario con la función segura "create-user" (solo administradores). */
    suspend fun createUser(email: String, password: String, fullName: String, role: AppRole): Boolean = write {
        client.functions.invoke("create-user", body = buildJsonObject {
            put("email", email.trim())
            put("password", password)
            put("full_name", fullName.trim())
            put("role", role.name)
        })
    }

    // ------------------------------------------------------------------ Cuentas
    fun accountsWithBalance(): Flow<List<AccountWithBalance>> = live(emptyList()) { fetchAccountBalances() }

    private suspend fun fetchAccountBalances(): List<AccountWithBalance> =
        client.from("account_balances").select {
            filter { eq("archived", false) }
            order("name", Order.ASCENDING)
        }.decodeList<AccountRow>().map { AccountWithBalance(it.toModel(), it.balance) }

    fun accounts(): Flow<List<Account>> = live(emptyList()) { fetchAccountBalances().map { it.account } }

    suspend fun saveAccount(account: Account): Boolean = write {
        val json = buildJsonObject {
            put("name", account.name)
            put("type", account.type.name)
            put("initial_balance", account.initialBalance)
            put("color", account.color)
            put("archived", account.archived)
        }
        if (account.id == 0L) client.from("accounts").insert(json)
        else client.from("accounts").update(json) { filter { eq("id", account.id) } }
    }

    /** Borra la cuenta si no tiene movimientos; si los tiene, la archiva. Devuelve true si se borró. */
    suspend fun removeAccount(account: Account): Boolean {
        val deleted = try {
            client.from("accounts").delete { filter { eq("id", account.id) } }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
        if (!deleted) saveAccount(account.copy(archived = true)) else refresh()
        return deleted
    }

    // ------------------------------------------------------------------ Categorías
    fun categories(): Flow<List<Category>> = live(emptyList()) {
        client.from("categories").select { order("name", Order.ASCENDING) }.decodeList<CategoryRow>()
            .map { Category(it.id, it.name, it.type, it.icon, it.color) }
            .sortedWith(compareBy({ it.type }, { it.name.lowercase() }))
    }

    suspend fun saveCategory(category: Category): Boolean = write {
        val json = buildJsonObject {
            put("name", category.name)
            put("type", category.type.name)
            put("icon", category.icon)
            put("color", category.color)
        }
        if (category.id == 0L) client.from("categories").insert(json)
        else client.from("categories").update(json) { filter { eq("id", category.id) } }
    }

    suspend fun deleteCategory(category: Category): Boolean = write {
        client.from("categories").delete { filter { eq("id", category.id) } }
    }

    // ------------------------------------------------------------------ Contactos (CRM)
    fun contacts(): Flow<List<Contact>> = live(emptyList()) { fetchContacts() }

    private suspend fun fetchContacts(): List<Contact> =
        client.from("contacts").select { order("name", Order.ASCENDING) }.decodeList<ContactRow>().map { it.toModel() }

    fun contactsWithBalance(): Flow<List<ContactWithBalance>> = live(emptyList()) {
        val stats = client.from("contact_stats").select().decodeList<ContactStatsRow>().associateBy { it.contactId }
        fetchContacts().map { c ->
            val s = stats[c.id]
            ContactWithBalance(c, s?.receivable ?: 0, s?.payable ?: 0)
        }
    }

    fun contact(id: Long): Flow<Contact?> = live(null) {
        client.from("contacts").select { filter { eq("id", id) } }.decodeSingleOrNull<ContactRow>()?.toModel()
    }

    fun contactStats(id: Long): Flow<ContactStats> = live(ContactStats(0, 0, 0, 0, 0)) {
        client.from("contact_stats").select { filter { eq("contact_id", id) } }.decodeSingleOrNull<ContactStatsRow>()
            ?.let { ContactStats(it.sales, it.expenses, it.cogs, it.receivable, it.payable) }
            ?: ContactStats(0, 0, 0, 0, 0)
    }

    suspend fun saveContact(contact: Contact): Boolean = write {
        val json = buildJsonObject {
            put("name", contact.name)
            put("type", contact.type.name)
            put("tax_id", contact.taxId)
            put("phone", contact.phone)
            put("email", contact.email)
            put("notes", contact.notes)
            put("id_type", contact.idType)
            put("address", contact.address)
            put("actividad_economica", contact.actividadEconomica)
            put("sri_estado", contact.sriEstado)
            put("sri_tipo", contact.sriTipo)
            put("sri_regimen", contact.sriRegimen)
            put("sri_obligado_contabilidad", contact.sriObligado)
            contact.sriData?.let { put("sri_data", Json.parseToJsonElement(it)) }
            contact.sriCheckedAt?.let { put("sri_checked_at", it) }
        }
        if (contact.id == 0L) client.from("contacts").insert(json)
        else client.from("contacts").update(json) { filter { eq("id", contact.id) } }
    }

    suspend fun deleteContact(contact: Contact): Boolean = write {
        client.from("contacts").delete { filter { eq("id", contact.id) } }
    }

    fun interactions(contactId: Long): Flow<List<InteractionDetail>> = live(emptyList()) {
        client.from("interactions").select(Columns.raw("*, contact:contacts(name)")) {
            filter { eq("contact_id", contactId) }
            order("date", Order.DESCENDING)
        }.decodeList<InteractionRow>().map { it.toModel() }
    }

    fun pendingFollowUps(): Flow<List<InteractionDetail>> = live(emptyList()) {
        client.from("interactions").select(Columns.raw("*, contact:contacts(name)")) {
            filter { eq("done", false) }
            order("follow_up", Order.ASCENDING)
        }.decodeList<InteractionRow>().filter { it.followUp != null }.map { it.toModel() }
    }

    suspend fun saveInteraction(i: Interaction): Boolean = write {
        val json = buildJsonObject {
            put("contact_id", i.contactId)
            put("kind", i.kind.name)
            put("date", Dates.isoDate(i.date))
            put("note", i.note)
            put("follow_up", i.followUp?.let { JsonPrimitive(Dates.isoDate(it)) } ?: JsonNull)
            put("done", i.done)
        }
        if (i.id == 0L) client.from("interactions").insert(json)
        else client.from("interactions").update(json) { filter { eq("id", i.id) } }
    }

    suspend fun deleteInteraction(id: Long): Boolean = write {
        client.from("interactions").delete { filter { eq("id", id) } }
    }

    // ------------------------------------------------------------------ Movimientos
    private val entryColumns = Columns.raw(
        "*, category:categories(name,icon,color), account:accounts!entries_account_id_fkey(name), " +
            "to_account:accounts!entries_to_account_id_fkey(name), contact:contacts(name)"
    )

    private suspend fun fetchEntries(from: Long?, to: Long?, pendingOnly: Boolean = false, contactId: Long? = null, limit: Long? = null): List<EntryDetail> =
        client.from("entries").select(entryColumns) {
            filter {
                if (from != null) gte("date", Dates.isoDate(from))
                if (to != null) lte("date", Dates.isoDate(to))
                if (pendingOnly) {
                    eq("is_paid", false)
                    neq("type", EntryType.TRANSFER.name)
                }
                if (contactId != null) eq("contact_id", contactId)
            }
            order("date", Order.DESCENDING)
            order("id", Order.DESCENDING)
            if (limit != null) limit(limit)
        }.decodeList<EntryRow>().map { it.toDetail() }

    fun entries(from: Long, to: Long): Flow<List<EntryDetail>> = live(emptyList()) { fetchEntries(from, to) }
    fun recentEntries(limit: Int = 6): Flow<List<EntryDetail>> = live(emptyList()) { fetchEntries(null, null, limit = limit.toLong()) }
    fun pendingEntries(): Flow<List<EntryDetail>> = live(emptyList()) {
        fetchEntries(null, null, pendingOnly = true).sortedBy { it.entry.dueDate ?: it.entry.date }
    }
    fun contactEntries(contactId: Long): Flow<List<EntryDetail>> = live(emptyList()) { fetchEntries(null, null, contactId = contactId, limit = 30) }

    fun totals(from: Long, to: Long): Flow<PeriodTotals> = live(PeriodTotals(0, 0)) {
        val list = fetchEntries(from, to)
        PeriodTotals(
            // Sin IVA: el impuesto no es ingreso ni gasto del negocio.
            income = list.filter { it.entry.type == EntryType.INCOME }.sumOf { it.entry.net },
            expense = list.filter { it.entry.type == EntryType.EXPENSE }.sumOf { it.entry.net },
        )
    }

    fun totalsByCategory(type: EntryType, from: Long, to: Long): Flow<List<CategoryTotal>> = live(emptyList()) {
        fetchEntries(from, to)
            .filter { it.entry.type == type && it.entry.categoryId != null }
            .groupBy { it.entry.categoryId!! }
            .map { (id, list) ->
                val f = list.first()
                CategoryTotal(id, f.categoryName.orEmpty(), f.categoryIcon ?: "label", f.categoryColor ?: 0xFF607D8B, list.sumOf { it.entry.net })
            }
            .sortedByDescending { it.total }
    }

    suspend fun entry(id: Long): Entry? = read(null) {
        client.from("entries").select { filter { eq("id", id) } }.decodeSingleOrNull<EntryRow>()?.toDetail()?.entry
    }

    /** Movimiento de inventario ligado a un movimiento contable (venta o compra de un artículo). */
    suspend fun stockMoveForEntry(entryId: Long): StockMove? = read(null) {
        client.from("stock_moves").select {
            filter { eq("entry_id", entryId) }
            limit(1)
        }.decodeList<StockMoveRow>().firstOrNull()?.toModel()
    }

    /** Guarda el movimiento y, si se indica, la venta/compra del artículo en una sola transacción. */
    suspend fun saveEntry(entry: Entry, itemId: Long? = null, quantity: Double? = null): Boolean = write {
        client.postgrest.rpc("save_entry", buildJsonObject {
            put("p_entry", buildJsonObject {
                if (entry.id != 0L) put("id", entry.id)
                put("type", entry.type.name)
                put("amount", entry.amount)
                put("tax_amount", entry.taxAmount)
                put("date", Dates.isoDate(entry.date))
                put("account_id", entry.accountId)
                entry.toAccountId?.let { put("to_account_id", it) }
                entry.categoryId?.let { put("category_id", it) }
                entry.contactId?.let { put("contact_id", it) }
                put("description", entry.description)
                put("reference", entry.reference)
                put("is_paid", entry.isPaid)
                entry.dueDate?.let { put("due_date", Dates.isoDate(it)) }
            })
            if (itemId != null && quantity != null && quantity > 0) {
                put("p_item_id", itemId)
                put("p_quantity", quantity)
            }
        })
    }

    suspend fun markPaid(id: Long): Boolean = write {
        client.from("entries").update(buildJsonObject { put("is_paid", true) }) { filter { eq("id", id) } }
    }

    suspend fun deleteEntry(id: Long): Boolean = write {
        client.from("entries").delete { filter { eq("id", id) } }
    }

    // ------------------------------------------------------------------ Inventario
    private suspend fun fetchItems(): List<ItemWithStock> =
        client.from("item_stock").select {
            filter { eq("archived", false) }
            order("name", Order.ASCENDING)
        }.decodeList<ItemRow>().map { ItemWithStock(it.toModel(), it.stock) }

    fun items(): Flow<List<ItemWithStock>> = live(emptyList()) { fetchItems() }

    fun item(id: Long): Flow<ItemWithStock?> = live(null) {
        client.from("item_stock").select { filter { eq("id", id) } }.decodeSingleOrNull<ItemRow>()
            ?.let { ItemWithStock(it.toModel(), it.stock) }
    }

    suspend fun saveItem(item: Item, initialStock: Double = 0.0): Boolean = write {
        val json = buildJsonObject {
            put("name", item.name)
            put("kind", item.kind.name)
            put("unit", item.unit)
            put("sku", item.sku)
            put("unit_cost", item.unitCost)
            put("sale_price", item.salePrice)
            put("min_stock", item.minStock)
            put("labor_cost", item.laborCost)
            put("overhead_cost", item.overheadCost)
            put("archived", item.archived)
            put("is_public", item.isPublic)
            put("description", item.description)
            put("dimensions", item.dimensions)
            put("image_url", item.imageUrl)
            put("iva_code", item.ivaCode)
        }
        if (item.id == 0L) {
            val created = client.from("items").insert(json) { select() }.decodeSingle<ItemRow>()
            if (initialStock != 0.0) insertMove(created.id, StockMoveType.ADJUSTMENT, initialStock, item.unitCost, "Inventario inicial")
        } else {
            client.from("items").update(json) { filter { eq("id", item.id) } }
        }
    }

    /** Borra el artículo si no tiene movimientos; si los tiene, lo archiva. */
    suspend fun removeItem(item: Item): Boolean {
        val deleted = try {
            client.from("items").delete { filter { eq("id", item.id) } }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
        if (!deleted) saveItem(item.copy(archived = true)) else refresh()
        return deleted
    }

    private suspend fun insertMove(itemId: Long, type: StockMoveType, quantity: Double, unitCost: Long, note: String) {
        client.from("stock_moves").insert(buildJsonObject {
            put("item_id", itemId)
            put("type", type.name)
            put("quantity", quantity)
            put("unit_cost", unitCost)
            put("date", Dates.isoDate(Dates.toMillis(Dates.today())))
            put("note", note)
        })
    }

    suspend fun adjustStock(item: Item, delta: Double, note: String): Boolean = write {
        insertMove(item.id, StockMoveType.ADJUSTMENT, delta, item.unitCost, note.ifBlank { "Ajuste de inventario" })
    }

    fun moves(itemId: Long): Flow<List<StockMove>> = live(emptyList()) {
        client.from("stock_moves").select {
            filter { eq("item_id", itemId) }
            order("date", Order.ASCENDING)
            order("id", Order.ASCENDING)
        }.decodeList<StockMoveRow>().map { it.toModel() }
    }

    fun bom(productId: Long): Flow<List<BomLineDetail>> = live(emptyList()) {
        val lines = client.from("bom_lines").select { filter { eq("product_id", productId) } }.decodeList<BomRow>()
        val materials = fetchItems().associateBy { it.item.id }
        lines.mapNotNull { l ->
            val m = materials[l.materialId] ?: return@mapNotNull null
            BomLineDetail(BomLine(l.id, l.productId, l.materialId, l.quantity, l.wastePercent), m.item.name, m.item.unit, m.item.unitCost, m.stock)
        }.sortedBy { it.materialName.lowercase() }
    }

    fun productsUsing(materialId: Long): Flow<List<ItemWithStock>> = live(emptyList()) {
        val productIds = client.from("bom_lines").select { filter { eq("material_id", materialId) } }
            .decodeList<BomRow>().map { it.productId }.toSet()
        fetchItems().filter { it.item.id in productIds }
    }

    suspend fun saveBomLine(line: BomLine): Boolean = write {
        val json = buildJsonObject {
            put("product_id", line.productId)
            put("material_id", line.materialId)
            put("quantity", line.quantity)
            put("waste_percent", line.wastePercent)
        }
        if (line.id == 0L) client.from("bom_lines").insert(json)
        else client.from("bom_lines").update(json) { filter { eq("id", line.id) } }
    }

    suspend fun deleteBomLine(id: Long): Boolean = write {
        client.from("bom_lines").delete { filter { eq("id", id) } }
    }

    /** Explosión de materiales calculada por la base de datos. */
    suspend fun explode(productId: Long, quantity: Double): List<ExplosionRow> = read(emptyList()) {
        client.postgrest.rpc("explode_bom", buildJsonObject {
            put("p_product_id", productId)
            put("p_quantity", quantity)
        }).decodeList<ExplosionDto>().map {
            ExplosionRow(it.materialId, it.materialName, it.unit, it.required, it.available, it.shortage, it.unitCost, it.cost)
        }
    }

    /** Registra una orden de producción: consume materiales y suma producto terminado. */
    suspend fun produce(productId: Long, quantity: Double, date: Long, note: String): Boolean = write {
        client.postgrest.rpc("produce", buildJsonObject {
            put("p_product_id", productId)
            put("p_quantity", quantity)
            put("p_date", Dates.isoDate(date))
            put("p_note", note)
        })
    }

    fun production(): Flow<List<ProductionDetail>> = live(emptyList()) {
        client.from("production_orders").select(Columns.raw("*, product:items(name,unit)")) {
            order("date", Order.DESCENDING)
            order("id", Order.DESCENDING)
            limit(100)
        }.decodeList<ProductionRow>().map {
            ProductionDetail(
                ProductionOrder(
                    it.id, it.productId, it.quantity, it.date.toMillis(), it.materialCost, it.laborCost, it.overheadCost, it.note,
                    QualityStatus.entries.firstOrNull { q -> q.name == it.qualityStatus } ?: QualityStatus.PENDIENTE,
                    it.qualityNote,
                ),
                it.product?.name.orEmpty(),
                it.product?.unit ?: "und",
            )
        }
    }

    /** Control de calidad: aprobar o rechazar una orden de producción. */
    suspend fun inspectProduction(id: Long, status: QualityStatus, note: String): Boolean = write {
        client.postgrest.rpc("inspect_production", buildJsonObject {
            put("p_order_id", id)
            put("p_status", status.name)
            put("p_note", note)
        })
    }

    suspend fun deleteProduction(id: Long): Boolean = write {
        client.from("production_orders").delete { filter { eq("id", id) } }
    }

    fun productSales(from: Long, to: Long): Flow<List<ProductSales>> = live(emptyList()) {
        client.from("stock_moves").select(Columns.raw("*, item:items(name,unit)")) {
            filter {
                eq("type", StockMoveType.SALE.name)
                gte("date", Dates.isoDate(from))
                lte("date", Dates.isoDate(to))
            }
        }.decodeList<StockMoveRow>()
            .groupBy { it.itemId }
            .map { (id, list) ->
                ProductSales(
                    itemId = id,
                    name = list.first().item?.name.orEmpty(),
                    unit = list.first().item?.unit ?: "und",
                    quantity = list.sumOf { -it.quantity },
                    revenue = list.sumOf { Math.round(-it.quantity * it.unitPrice) },
                    cost = list.sumOf { Math.round(-it.quantity * it.unitCost) },
                )
            }
            .sortedByDescending { it.revenue }
    }

    /** Sube la foto de un producto al catálogo y devuelve su URL pública. */
    suspend fun uploadCatalogImage(itemId: Long, bytes: ByteArray, extension: String): String? = read(null) {
        val path = "productos/$itemId-${System.currentTimeMillis()}.$extension"
        val bucket = client.storage.from("catalogo")
        bucket.upload(path, bytes) { upsert = true }
        bucket.publicUrl(path)
    }

    // ------------------------------------------------------------------ Ecuador: empresa y SRI
    fun company(): Flow<Company?> = live(null) {
        client.from("company").select { filter { eq("id", 1) } }.decodeSingleOrNull<CompanyRow>()?.toModel()
    }

    suspend fun saveCompany(c: Company): Boolean = write {
        client.from("company").update(buildJsonObject {
            put("ruc", c.ruc.trim())
            put("razon_social", c.razonSocial.trim())
            put("nombre_comercial", c.nombreComercial.trim())
            put("dir_matriz", c.dirMatriz.trim())
            put("dir_establecimiento", c.dirEstablecimiento.trim())
            put("estab", c.estab)
            put("pto_emi", c.ptoEmi)
            put("next_secuencial", c.nextSecuencial)
            put("ambiente", c.ambiente)
            put("obligado_contabilidad", c.obligadoContabilidad)
            put("contribuyente_especial", c.contribuyenteEspecial.trim())
            put("agente_retencion", c.agenteRetencion.trim())
            put("regimen", c.regimen)
            put("email", c.email.trim())
            put("phone", c.phone.trim())
            put("late_interest_rate", c.lateInterestRate)
        }) { filter { eq("id", 1) } }
    }

    // ------------------------------------------------------------------ Facturación electrónica
    private val invoiceColumns = Columns.raw("*, lines:invoice_lines(*), cuotas:installments(*)")

    fun invoices(): Flow<List<Invoice>> = live(emptyList()) {
        client.from("invoices").select {
            order("id", Order.DESCENDING)
            limit(200)
        }.decodeList<InvoiceRow>().map { it.toModel() }
    }

    fun invoice(id: Long): Flow<InvoiceFull?> = live(null) {
        client.from("invoices").select(invoiceColumns) { filter { eq("id", id) } }.decodeSingleOrNull<InvoiceRow>()?.let { r ->
            InvoiceFull(
                r.toModel(),
                r.lines.sortedBy { it.id }.map {
                    InvoiceLine(it.id, it.itemId, it.code, it.description, it.quantity, it.unitPrice, it.discount, it.ivaCode, it.subtotal, it.tax)
                },
                r.installmentRows.sortedBy { it.number }.map { Installment(it.number, it.dueDate.toMillis(), it.capital, it.interest, it.total) },
            )
        }
    }

    /** Crea la factura (numeración, clave de acceso, contabilidad e inventario). Devuelve su id. */
    suspend fun createInvoice(d: InvoiceDraft): Long? = read(null) {
        val id = client.postgrest.rpc("create_invoice", buildJsonObject {
            put("p", buildJsonObject {
                put("contact_id", d.contactId)
                put("date", Dates.isoDate(d.date))
                put("account_id", d.accountId)
                put("payment_method", d.paymentMethod)
                put("term_days", d.termDays)
                put("installments", d.installments)
                put("finance_rate", d.financeRate)
                put("note", d.note)
                put("lines", buildJsonArray {
                    d.lines.forEach { l ->
                        add(buildJsonObject {
                            l.itemId?.let { put("item_id", it) }
                            put("description", l.description)
                            put("quantity", l.quantity)
                            put("unit_price", l.unitPrice)
                            put("discount", l.discount)
                            put("iva_code", l.ivaCode)
                        })
                    }
                })
            })
        }).decodeAs<Long>()
        refresh()
        id
    }

    /** Firma y envía la factura al SRI (función segura "sri-factura") y consulta la autorización. */
    suspend fun sendToSri(invoiceId: Long): SriResult {
        val result = try {
            val res = client.functions.invoke("sri-factura", body = buildJsonObject { put("invoice_id", invoiceId) })
            sriJson.decodeFromString<SriResultDto>(res.bodyAsText())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // La función responde {"error": "..."} con el motivo; se muestra tal cual.
            val body = e.message.orEmpty()
            val msg = Regex("\"error\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
            SriResultDto(error = msg ?: "No se pudo contactar al servidor. Revisa tu conexión.")
        }
        refresh()
        return SriResult(
            status = InvoiceStatus.entries.firstOrNull { it.name == result.status },
            messages = result.messages.map { SriMessage(it.identificador, it.mensaje, it.informacionAdicional, it.tipo) },
            error = result.error,
            pending = result.pending,
        )
    }

    suspend fun voidInvoice(id: Long): Boolean = write {
        client.postgrest.rpc("void_invoice", buildJsonObject { put("p_id", id) })
    }

    // ------------------------------------------------------------------ Intereses
    fun lateReceivables(): Flow<List<LateReceivable>> = live(emptyList()) {
        client.from("late_receivables").select { order("due_date", Order.ASCENDING) }.decodeList<LateReceivableRow>().map {
            LateReceivable(it.entryId, it.contactId, it.description, it.amount, it.dueDate.toMillis(), it.days, it.daysOverdue, it.interest)
        }
    }

    suspend fun chargeLateInterest(entryId: Long): Boolean = write {
        client.postgrest.rpc("charge_late_interest", buildJsonObject { put("p_entry_id", entryId) })
    }

    fun ivaMonthly(): Flow<List<IvaMonth>> = live(emptyList()) {
        client.from("iva_monthly").select { order("month", Order.DESCENDING) }.decodeList<IvaMonthRow>().map {
            IvaMonth(it.month.toMillis(), it.salesBase, it.salesTax, it.purchasesBase, it.purchasesTax, it.taxDue, it.dueDate.toMillis())
        }
    }

    /** Intereses y multa por pagar o declarar tarde un impuesto. */
    suspend fun sriLateCharges(tax: Long, dueDate: Long, payDate: Long): SriCharges? = read(null) {
        client.postgrest.rpc("sri_late_charges", buildJsonObject {
            put("p_tax", tax)
            put("p_due", Dates.isoDate(dueDate))
            put("p_pay", Dates.isoDate(payDate))
        }).decodeList<SriChargesRow>().firstOrNull()?.let { SriCharges(it.months, it.interest, it.fine, it.total, it.missingRates) }
    }

    fun sriRates(): Flow<List<SriRate>> = live(emptyList()) {
        client.from("sri_interest_rates").select { order("quarter_start", Order.DESCENDING) }.decodeList<SriRateRow>()
            .map { SriRate(it.quarterStart.toMillis(), it.monthlyRate) }
    }

    suspend fun saveSriRate(r: SriRate): Boolean = write {
        client.from("sri_interest_rates").upsert(buildJsonObject {
            put("quarter_start", Dates.isoDate(r.quarterStart))
            put("monthly_rate", r.monthlyRate)
        })
    }

    suspend fun deleteSriRate(r: SriRate): Boolean = write {
        client.from("sri_interest_rates").delete { filter { eq("quarter_start", Dates.isoDate(r.quarterStart)) } }
    }
}

// ------------------------------------------------------------------ Conversión filas → modelos

private fun String.toMillis(): Long = Dates.toMillis(LocalDate.parse(this.take(10)))

private fun ProfileRow.toModel() = Profile(id, fullName, role, active)

private fun AccountRow.toModel() = Account(id, name, type, initialBalance, color, archived)

private val sriJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

private fun CompanyRow.toModel() = Company(
    ruc, razonSocial, nombreComercial, dirMatriz, dirEstablecimiento, estab, ptoEmi, nextSecuencial, ambiente,
    obligadoContabilidad, contribuyenteEspecial, agenteRetencion, regimen, email, phone, lateInterestRate,
)

private fun InvoiceRow.toModel() = Invoice(
    id = id, contactId = contactId, number = "$estab-$ptoEmi-${secuencial.toString().padStart(9, '0')}",
    date = fechaEmision.toMillis(), status = InvoiceStatus.entries.firstOrNull { it.name == status } ?: InvoiceStatus.PENDIENTE,
    ambiente = ambiente, claveAcceso = claveAcceso, buyerName = buyerName, buyerId = buyerId, buyerEmail = buyerEmail,
    subtotal = subtotal, discount = discount, tax = tax, total = total, paymentMethod = paymentMethod, termDays = termDays,
    installments = installments, financeRate = financeRate, note = note, authorizationNumber = authorizationNumber,
    authorizedAt = authorizedAt, messages = sriMessages.map { SriMessage(it.identificador, it.mensaje, it.informacionAdicional, it.tipo) },
)

private fun ContactRow.toModel() = Contact(
    id, name, type, taxId, phone, email, notes, idType, address,
    actividadEconomica, sriEstado, sriTipo, sriRegimen, sriObligado,
    sriData?.takeUnless { it is kotlinx.serialization.json.JsonNull }?.toString(), sriCheckedAt,
)

private fun EntryRow.toDetail() = EntryDetail(
    entry = Entry(
        id = id, type = type, amount = amount, date = date.toMillis(), accountId = accountId,
        toAccountId = toAccountId, categoryId = categoryId, contactId = contactId,
        description = description, reference = reference, isPaid = isPaid, dueDate = dueDate?.toMillis(),
        taxAmount = taxAmount, invoiceId = invoiceId, isInterest = isInterest,
    ),
    categoryName = category?.name,
    categoryIcon = category?.icon,
    categoryColor = category?.color,
    accountName = account?.name,
    toAccountName = toAccount?.name,
    contactName = contact?.name,
)

private fun ItemRow.toModel() = Item(
    id, name, kind, unit, sku, unitCost, salePrice, minStock, laborCost, overheadCost, archived,
    isPublic, description, dimensions, imageUrl, ivaCode,
)

private fun StockMoveRow.toModel() = StockMove(
    id, itemId, type, quantity, unitCost, unitPrice, date.toMillis(), entryId, productionId, contactId, note,
)

private fun InteractionRow.toModel() = InteractionDetail(
    Interaction(id, contactId, kind, date.toMillis(), note, followUp?.toMillis(), done),
    contact?.name.orEmpty(),
)
