package com.example.posapp.data

import android.content.Context
import androidx.core.content.edit

/** Identidad local del negocio activo. No contiene credenciales. */
class ActiveBusinessStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("active_business", Context.MODE_PRIVATE)

    fun set(userId: String, businessId: String) {
        val validUserId = userId.requireCloudUuid("user_id")
        val validBusinessId = businessId.requireCloudUuid("business_id")
        preferences.edit {
            putString("user_id", validUserId)
            putString("business_id", validBusinessId)
        }
    }

    fun businessId(): String = preferences.getString("business_id", null).normalizedUuidOrNull().orEmpty()

    fun userId(): String? = preferences.getString("user_id", null).normalizedUuidOrNull()

    fun clear() = preferences.edit { clear() }
}
