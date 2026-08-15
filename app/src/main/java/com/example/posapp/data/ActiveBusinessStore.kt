package com.example.posapp.data

import android.content.Context
import androidx.core.content.edit

/** Identidad local del negocio activo. No contiene credenciales. */
class ActiveBusinessStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("active_business", Context.MODE_PRIVATE)

    fun set(userId: String, businessId: String) {
        preferences.edit {
            putString("user_id", userId)
            putString("business_id", businessId)
        }
    }

    fun businessId(): String = preferences.getString("business_id", null).orEmpty()

    fun clear() = preferences.edit { clear() }
}
