package com.example.posapp.auth

import android.content.Context
import androidx.core.content.edit
import androidx.room.withTransaction
import com.example.posapp.data.AppDatabase
import com.example.posapp.data.ActiveBusinessStore
import com.example.posapp.data.entities.BusinessSettings
import com.example.posapp.data.UserPreferencesRepository
import com.example.posapp.data.remote.SupabaseProvider
import com.example.posapp.data.sync.CloudSyncScheduler
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.StateFlow
import io.github.jan.supabase.auth.status.SessionStatus
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthRepository(context: Context) {
    private val appContext = context.applicationContext
    private val client get() = SupabaseProvider.client
    private val auth get() = client.auth
    private val cache = AuthBusinessCache(appContext)
    private val localDataOwner = LocalDataOwner(appContext)
    private val activeBusiness = ActiveBusinessStore(appContext)
    private val database by lazy { AppDatabase.getInstance(appContext) }

    val sessionStatus: StateFlow<SessionStatus> get() = auth.sessionStatus

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

    suspend fun bindLocalDataTo(userId: String, businessId: String) {
        localDataOwner.bindIfEmpty(userId)
        activeBusiness.set(userId, businessId)
        val now = System.currentTimeMillis()
        database.withTransaction {
            database.productoDao().bindUnownedRows(businessId, now)
            database.clienteDao().bindUnownedRows(businessId, now)
            database.ventaDao().bindUnownedSales(businessId, now)
            database.ventaDao().bindUnownedSaleItems(businessId)
            database.ventaDao().bindUnownedPayments(businessId)
            database.syncDao().insertBusinessSettingsIfMissing(
                BusinessSettings(business_id = businessId, updated_at = now)
            )
        }
    }

    suspend fun pendingSyncChanges(): Int {
        val businessId = activeBusiness.businessId()
        return if (businessId.isBlank()) 0 else database.syncDao().pendingCount(businessId)
    }

    /** Sale de una cuenta rechazada sin tocar los datos que pertenecen al usuario anterior. */
    suspend fun signOutPreservingLocalData() {
        auth.signOut()
    }

    suspend fun signOutAndClearLocalData() {
        val userId = currentUser()?.id
        CloudSyncScheduler.cancelAll(appContext)
        auth.signOut()
        withContext(Dispatchers.IO) {
            database.clearAllTables()
            listOf("images", "backups", "exports").forEach { directoryName ->
                File(appContext.filesDir, directoryName).deleteRecursively()
            }
            appContext.cacheDir.resolve("spacesale").deleteRecursively()
        }
        UserPreferencesRepository(appContext).clear()
        if (userId != null) cache.clear(userId)
        activeBusiness.clear()
        localDataOwner.clear()
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

    fun clear() = preferences.edit { clear() }
}
