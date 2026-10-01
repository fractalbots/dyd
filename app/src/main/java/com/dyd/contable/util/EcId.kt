package com.dyd.contable.util

/** Identificación tributaria de Ecuador (mismas reglas que ec_valid_id en supabase/schema.sql). */
object EcId {
    /** Tipos de identificación del SRI. */
    val TYPES = linkedMapOf(
        "04" to "RUC",
        "05" to "Cédula",
        "06" to "Pasaporte",
        "07" to "Consumidor final",
        "08" to "Identificación del exterior",
    )
    const val CONSUMIDOR_FINAL = "9999999999999"

    fun isValidCedula(id: String): Boolean {
        if (id.length != 10 || !id.all { it.isDigit() }) return false
        val province = id.substring(0, 2).toInt()
        if (province !in 1..24 && province != 30) return false
        if (id[2].digitToInt() >= 6) return false
        val sum = (0 until 9).sumOf { i ->
            val d = id[i].digitToInt() * (if (i % 2 == 0) 2 else 1)
            if (d > 9) d - 9 else d
        }
        return (10 - sum % 10) % 10 == id[9].digitToInt()
    }

    /** Persona natural: cédula + establecimiento. Sociedades y entidades públicas: estructura. */
    fun isValidRuc(id: String): Boolean {
        if (id.length != 13 || !id.all { it.isDigit() }) return false
        val province = id.substring(0, 2).toInt()
        if (province !in 1..24 && province != 30) return false
        return when (id[2].digitToInt()) {
            in 0..5 -> isValidCedula(id.substring(0, 10)) && id.substring(10) != "000"
            6 -> id.substring(9) != "0000"
            9 -> id.substring(10) != "000"
            else -> false
        }
    }

    fun isValid(type: String, id: String): Boolean = when (type) {
        "04" -> isValidRuc(id)
        "05" -> isValidCedula(id)
        "06", "08" -> id.trim().length in 3..20
        "07" -> id == CONSUMIDOR_FINAL
        else -> false
    }

    /** Sugiere el tipo según lo escrito: 13 dígitos RUC, 10 dígitos cédula. */
    fun guessType(id: String): String = when {
        id == CONSUMIDOR_FINAL -> "07"
        id.length == 13 && id.all { it.isDigit() } -> "04"
        id.length == 10 && id.all { it.isDigit() } -> "05"
        else -> "06"
    }

    /** Mensaje para el usuario, o null si es válido. */
    fun error(type: String, id: String): String? = when {
        type.isEmpty() -> "Elige el tipo de identificación"
        id.isBlank() -> "Escribe el número de identificación"
        isValid(type, id) -> null
        type == "04" -> "RUC no válido: son 13 dígitos (cédula + 001 para personas)"
        type == "05" -> "Cédula no válida: revisa los 10 dígitos"
        type == "07" -> "Consumidor final usa 9999999999999"
        else -> "Identificación no válida"
    }
}
