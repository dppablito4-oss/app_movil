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
import com.example.posapp.data.sync.CloudSyncCoordinator
import com.example.posapp.data.sync.PendingLocalData
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import com.example.posapp.data.UserProfile
import kotlinx.coroutines.flow.StateFlow
import io.github.jan.supabase.auth.status.SessionStatus
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

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

    suspend fun signInWithPassword(email: String, password: String): AuthenticatedUser {
        auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        return currentUser() ?: error("Supabase no devolvió una sesión.")
    }

    suspend fun signInWithGoogle(idToken: String, nonce: String): AuthenticatedUser {
        auth.signInWith(IDToken) {
            this.idToken = idToken
            provider = Google
            this.nonce = nonce
        }
        return currentUser() ?: error("Supabase no devolvio una sesion de Google.")
    }

    suspend fun setPasswordAndProfile(userId: String, password: String, displayName: String) {
        auth.updateUser {
            this.password = password
            data { put("name", JsonPrimitive(displayName.trim())) }
        }
        client.from("profiles").update(
            {
                set("display_name", displayName.trim())
            }
        ) {
            filter {
                eq("id", userId)
            }
        }
    }

    suspend fun verifyOtp(email: String, token: String, isRegistration: Boolean): AuthenticatedUser {
        auth.verifyEmailOtp(
            type = otpTypeFor(isRegistration),
            email = email,
            token = token
        )
        return currentUser() ?: error("Supabase no devolvió una sesión después de verificar el código.")
    }

    suspend fun listBusinesses(): List<RemoteBusiness> = client
        .from("businesses")
        .select()
        .decodeList<RemoteBusiness>()

    suspend fun findBusiness(): RemoteBusiness? {
        val businesses = listBusinesses()
        val preferredId = activeBusiness.businessId()
        return businesses.firstOrNull { it.id == preferredId } ?: businesses.firstOrNull()
    }

    suspend fun createBusiness(
        userId: String,
        name: String,
        address: String = "",
        phone: String = "",
        logoPath: String? = null
    ): RemoteBusiness {
        findBusiness()?.let { return it }

        // Evita INSERT ... RETURNING: la membresía se crea en un trigger
        // AFTER INSERT y la política SELECT podría evaluarse antes de verla.
        client.from("businesses").insert(
            CreateBusinessRequest(
                ownerId = userId,
                name = name.trim(),
                address = address.trim().ifBlank { null },
                phone = phone.trim().ifBlank { null },
                logoPath = logoPath
            )
        )
        val business = findBusiness()
            ?: error("El negocio se creó, pero la membresía del propietario no está disponible.")
        cache.save(userId, business)
        return business
    }

    suspend fun uploadBusinessLogo(businessId: String, localPath: String): String {
        val file = File(localPath)
        require(file.exists()) { "No se encontró el logo seleccionado." }
        val remotePath = "$businessId/logo-${System.currentTimeMillis()}.jpg"
        client.storage.from("business-assets").upload(remotePath, file.readBytes()) { upsert = false }
        client.postgrest.rpc(
            "set_business_logo",
            Json.encodeToJsonElement(BusinessLogoRpc.serializer(), BusinessLogoRpc(businessId, remotePath)).jsonObject
        )
        return remotePath
    }

    suspend fun saveLocalProfile(displayName: String, business: RemoteBusiness, localLogoPath: String?) {
        UserPreferencesRepository(appContext).saveProfile(
            UserProfile(displayName.trim(), business.name, business.address.orEmpty(), localLogoPath)
        )
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
                BusinessSettings(business_id = businessId, updated_at = now, sync_status = com.example.posapp.data.entities.SyncStatus.SYNCED)
            )
        }
    }

    suspend fun preparePendingLocalData(): PendingLocalData {
        val businessId = activeBusiness.businessId()
        if (businessId.isBlank()) return PendingLocalData()
        CloudSyncCoordinator.prepareQueue(appContext, businessId)
        return pendingLocalData(businessId)
    }

    suspend fun tryImmediateSync(timeoutMillis: Long = 20_000): PendingLocalData {
        val businessId = activeBusiness.businessId()
        if (businessId.isBlank()) return PendingLocalData()
        withTimeoutOrNull(timeoutMillis) {
            try {
                CloudSyncCoordinator.synchronizeNow(appContext, businessId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // El resumen posterior decide si es seguro cerrar; red y 5xx no eliminan datos.
            }
        }
        CloudSyncCoordinator.prepareQueue(appContext, businessId)
        return pendingLocalData(businessId)
    }

    private suspend fun pendingLocalData(businessId: String): PendingLocalData {
        val sync = database.syncDao()
        return PendingLocalData(
            queuedOperations = sync.pendingCount(businessId),
            products = sync.pendingProducts(businessId),
            customers = sync.pendingCustomers(businessId),
            sales = sync.pendingSales(businessId),
            saleItems = sync.pendingSaleItems(businessId),
            payments = sync.pendingPayments(businessId),
            stockMovements = sync.pendingStockMovements(businessId),
            images = sync.pendingImages(businessId),
            businessSettings = sync.pendingBusinessSettings(businessId)
        )
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

internal fun otpTypeFor(isRegistration: Boolean): OtpType.Email =
    if (isRegistration) OtpType.Email.SIGNUP else OtpType.Email.EMAIL

private class AuthBusinessCache(context: Context) {
    private val preferences = context.getSharedPreferences("auth_business_cache", Context.MODE_PRIVATE)

    fun save(userId: String, business: RemoteBusiness) {
        preferences.edit {
            putString("user_id", userId)
            putString("business_id", business.id)
            putString("owner_id", business.ownerId)
            putString("business_name", business.name)
            putString("business_address", business.address)
            putString("business_phone", business.phone)
            putString("business_logo_path", business.logoPath)
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
            address = preferences.getString("business_address", null),
            phone = preferences.getString("business_phone", null),
            logoPath = preferences.getString("business_logo_path", null)
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
