package com.example.posapp.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.userPrefsDataStore by preferencesDataStore(name = "user_prefs")

private object UserKeys {
    val userName = stringPreferencesKey("user_name")
    val businessName = stringPreferencesKey("business_name")
    val address = stringPreferencesKey("address")
    val logoPath = stringPreferencesKey("logo_path")
}

data class UserProfile(
    val userName: String,
    val businessName: String,
    val address: String,
    val logoPath: String? = null
)

class UserPreferencesRepository(private val context: Context) {
    val profileFlow: Flow<UserProfile?> = context.userPrefsDataStore.data.map { prefs ->
        val name = prefs[UserKeys.userName]
        val business = prefs[UserKeys.businessName]
        val addr = prefs[UserKeys.address]
        val logo = prefs[UserKeys.logoPath]
        if (name.isNullOrBlank() || business.isNullOrBlank()) {
            null
        } else {
            UserProfile(
                userName = name,
                businessName = business,
                address = addr.orEmpty(),
                logoPath = logo
            )
        }
    }

    suspend fun saveProfile(profile: UserProfile) {
        context.userPrefsDataStore.edit { prefs ->
            prefs[UserKeys.userName] = profile.userName
            prefs[UserKeys.businessName] = profile.businessName
            prefs[UserKeys.address] = profile.address
            if (profile.logoPath.isNullOrBlank()) prefs.remove(UserKeys.logoPath) else prefs[UserKeys.logoPath] = profile.logoPath
        }
    }

    suspend fun clear() {
        context.userPrefsDataStore.edit { it.clear() }
    }
}
