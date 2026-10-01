package com.dyd.contable.data.remote

import com.dyd.contable.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.github.jan.supabase.storage.Storage
import kotlinx.serialization.json.Json

object Supabase {
    /** true si local.properties trae la URL y la clave pública del proyecto. */
    val isConfigured: Boolean
        get() = BuildConfig.SUPABASE_URL.startsWith("https://") && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    fun create(): SupabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL.ifBlank { "https://configura-supabase.supabase.co" },
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY.ifBlank { "sin-clave" },
    ) {
        defaultSerializer = KotlinXSerializer(Json { ignoreUnknownKeys = true; encodeDefaults = true })
        install(Auth)
        install(Postgrest)
        install(Functions)
        install(Storage)
    }
}
