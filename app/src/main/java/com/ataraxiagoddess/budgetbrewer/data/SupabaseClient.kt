package com.ataraxiagoddess.budgetbrewer.data

import android.content.Context
import com.russhwolf.settings.SharedPreferencesSettings
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.SettingsSessionManager
import io.github.jan.supabase.postgrest.Postgrest

object SupabaseClient {
    const val SUPABASE_URL = "https://jqncitfsbqiyklojfvsq.supabase.co"
    const val SUPABASE_PUBLISHABLE_KEY = "sb_publishable_6DcNK-3AnsDadX2F2HoMDg_VwpbCHI0"

    lateinit var client: io.github.jan.supabase.SupabaseClient
        private set

    fun initialize(context: Context) {
        val settings = SharedPreferencesSettings(delegate = context.getSharedPreferences("supabase_auth", Context.MODE_PRIVATE))

        client = createSupabaseClient(
            supabaseUrl = SUPABASE_URL,
            supabaseKey = SUPABASE_PUBLISHABLE_KEY
        ) {
            install(Postgrest)
            install(Auth) {
                sessionManager = SettingsSessionManager(settings)
            }
        }
    }
}