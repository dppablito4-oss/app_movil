package com.example.posapp.data.remote

import com.example.posapp.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

/**
 * Punto único de creación del cliente remoto.
 *
 * Room continúa siendo la fuente de verdad local. Las pantallas y ViewModels no
 * deben consultar este cliente directamente: los futuros data sources remotos y
 * el motor de sincronización serán sus únicos consumidores.
 */
object SupabaseProvider {
    val isConfigured: Boolean
        get() = BuildConfig.SUPABASE_URL.isNotBlank() &&
            BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()

    val client: SupabaseClient by lazy {
        check(isConfigured) {
            "Supabase no está configurado. Define SUPABASE_URL y " +
                "SUPABASE_PUBLISHABLE_KEY en local.properties."
        }

        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
        ) {
            install(Auth)
            install(Postgrest)
            install(Storage)
        }
    }
}
