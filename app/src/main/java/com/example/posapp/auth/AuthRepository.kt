package com.example.posapp.auth

import android.content.Context
import androidx.core.content.edit
import com.example.posapp.data.remote.SupabaseProvider
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.StateFlow
import io.github.jan.supabase.auth.status.SessionStatus

class AuthRepository(context: Context) {
    private val client = SupabaseProvider.client
    private val auth = client.auth
    private val cache = AuthBusinessCache(context.applicationContext)
    private val localDataOwner = LocalDataOwner(context.applicationContext)

    val sessionStatus: StateFlow<SessionStatus> = auth.sessionStatus

    suspend fun awaitInitialization() = auth.awaitInitialization()

    fun currentUser(): AuthenticatedUser? = auth.currentUserOrNull()?.let {
        AuthenticatedUser(id = it.id, email = it.email.orEmpty())
    }

    suspend fun sendOtp(email: String, createUser: Boolean) {
        auth.signInWith(OTP) {
            this.email = email
            this.createUser = createUser
        }
    }

    suspend fun verifyOtp(email: String, token: String): AuthenticatedUser {
        auth.verifyEmailOtp(
            type = OtpType.Email.EMAIL,
            email = email,
            token = token
        )
        return currentUser() ?: error("Supabase no devolvió una sesión después de verificar el código.")
    }

    suspend fun findBusiness(): RemoteBusiness? = client
        .from("businesses")
        .select { limit(1) }
        .decodeList<RemoteBusiness>()
        .firstOrNull()

    suspend fun createBusiness(userId: String, name: String): RemoteBusiness {
        findBusiness()?.let { return it }

        // Evita INSERT ... RETURNING: la membresía se crea en un trigger
        // AFTER INSERT y la política SELECT podría evaluarse antes de verla.
        client.from("businesses").insert(
            CreateBusinessRequest(ownerId = userId, name = name.trim())
        )
        val business = findBusiness()
            ?: error("El negocio se creó, pero la membresía del propietario no está disponible.")
        cache.save(userId, business)
        return business
    }

    fun cachedBusiness(userId: String): RemoteBusiness? = cache.read(userId)

    fun cacheBusiness(userId: String, business: RemoteBusiness) = cache.save(userId, business)

    fun canOpenLocalData(userId: String): Boolean = localDataOwner.read()?.let { it == userId } ?: true

    fun bindLocalDataTo(userId: String) = localDataOwner.bindIfEmpty(userId)

    suspend fun signOut() {
        currentUser()?.let { cache.clear(it.id) }
        auth.signOut()
    }
}

data class AuthenticatedUser(val id: String, val email: String)

private class AuthBusinessCache(context: Context) {
    private val preferences = context.getSharedPreferences("auth_business_cache", Context.MODE_PRIVATE)

    fun save(userId: String, business: RemoteBusiness) {
        preferences.edit {
            putString("user_id", userId)
            putString("business_id", business.id)
            putString("owner_id", business.ownerId)
            putString("business_name", business.name)
            putString("business_address", business.address)
        }
    }

    fun read(userId: String): RemoteBusiness? {
        if (preferences.getString("user_id", null) != userId) return null
        val id = preferences.getString("business_id", null) ?: return null
        val ownerId = preferences.getString("owner_id", null) ?: return null
        val name = preferences.getString("business_name", null) ?: return null
        return RemoteBusiness(
            id = id,
            ownerId = ownerId,
            name = name,
            address = preferences.getString("business_address", null)
        )
    }

    fun clear(userId: String) {
        if (preferences.getString("user_id", null) == userId) preferences.edit { clear() }
    }
}

private class LocalDataOwner(context: Context) {
    private val preferences = context.getSharedPreferences("local_data_owner", Context.MODE_PRIVATE)

    fun read(): String? = preferences.getString("user_id", null)

    fun bindIfEmpty(userId: String) {
        if (read() == null) preferences.edit { putString("user_id", userId) }
    }
}
