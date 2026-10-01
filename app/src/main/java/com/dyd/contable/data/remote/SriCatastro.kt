package com.dyd.contable.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Datos de un contribuyente según el catastro del SRI. */
data class SriContribuyente(
    val ruc: String,
    val razonSocial: String,
    val estado: String,
    val actividadEconomica: String,
    val tipo: String,
    val regimen: String,
    val obligadoContabilidad: String,
    val direccion: String,
    /** Respuesta del SRI tal cual, para guardarla junto al cliente. */
    val raw: JsonObject,
)

/**
 * Consulta pública de RUC del SRI (la misma que usa «SRI en línea > Consulta de RUC»).
 * Se llama desde el teléfono y no desde Supabase: el SRI rechaza conexiones desde fuera de Ecuador.
 */
object SriCatastro {
    private const val BASE = "https://srienlinea.sri.gob.ec/sri-catastro-sujeto-servicio-internet/rest"
    private val json = Json { ignoreUnknownKeys = true }
    private val http by lazy {
        HttpClient(OkHttp) { install(HttpTimeout) { requestTimeoutMillis = 20_000; connectTimeoutMillis = 10_000 } }
    }

    /** Devuelve los datos del RUC, o lanza una excepción con un mensaje para el usuario. */
    suspend fun consultar(ruc: String): SriContribuyente {
        val body = try {
            val res = http.get("$BASE/ConsolidadoContribuyente/obtenerPorNumerosRuc?&ruc=$ruc") {
                header("Accept", "application/json")
            }
            if (!res.status.isSuccess()) throw IllegalStateException("El SRI respondió ${res.status.value}")
            res.bodyAsText()
        } catch (e: IllegalStateException) {
            throw e
        } catch (e: Exception) {
            throw IllegalStateException("No se pudo conectar con el SRI. Revisa tu internet (la consulta solo funciona desde Ecuador).")
        }
        val direccion = runCatching { direccionMatriz(ruc) }.getOrDefault("")
        return parseContribuyente(body, direccion) ?: throw IllegalStateException("El SRI no tiene registrado el RUC $ruc")
    }

    private suspend fun direccionMatriz(ruc: String): String {
        val res = http.get("$BASE/Establecimiento/consultarPorNumeroRuc?numeroRuc=$ruc") { header("Accept", "application/json") }
        return if (res.status.isSuccess()) parseDireccionMatriz(res.bodyAsText()) else ""
    }

    /** Lee la respuesta de ConsolidadoContribuyente (lista u objeto). Tolera campos faltantes. */
    fun parseContribuyente(body: String, direccion: String = ""): SriContribuyente? {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return null
        val obj = when (root) {
            is JsonArray -> root.firstOrNull() as? JsonObject
            is JsonObject -> root
            else -> null
        } ?: return null
        val ruc = obj.text("numeroRuc")
        val razon = obj.text("razonSocial")
        if (ruc.isEmpty() && razon.isEmpty()) return null
        return SriContribuyente(
            ruc = ruc,
            razonSocial = razon,
            estado = obj.text("estadoContribuyenteRuc", "estado"),
            actividadEconomica = obj.text("actividadEconomicaPrincipal", "actividadEconomica"),
            tipo = obj.text("tipoContribuyente"),
            regimen = obj.text("regimen"),
            obligadoContabilidad = obj.text("obligadoLlevarContabilidad"),
            direccion = direccion,
            raw = obj,
        )
    }

    /** Dirección del establecimiento matriz (o el primero abierto). */
    fun parseDireccionMatriz(body: String): String {
        val list = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonArray ?: return ""
        val items = list.mapNotNull { it as? JsonObject }
        val matriz = items.firstOrNull { it.text("matriz") == "SI" }
            ?: items.firstOrNull { it.text("estado") == "ABIERTO" } ?: items.firstOrNull()
        return matriz?.text("direccionCompleta").orEmpty()
    }

    private fun JsonObject.text(vararg keys: String): String {
        for (k in keys) {
            val v: JsonElement? = this[k]
            if (v is JsonPrimitive && v !is JsonNull) v.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return ""
    }
}
