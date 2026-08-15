package com.example.posapp.auth

import android.content.Context
import androidx.core.content.edit
import androidx.room.withTransaction
import com.example.posapp.data.AppDatabase
import com.example.posapp.data.ActiveBusinessStore
import com.example.posapp.data.entities.BusinessSettings
import com.example.posapp.data.UserPreferencesRepository
import com.example.posapp.data.normalizedUuidOrNull
import com.example.posapp.data.requireCloudUuid
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

    fun currentUser(): AuthenticatedUser? {
        val user = auth.currentUserOrNull() ?: return null
        val userId = user.id.normalizedUuidOrNull() ?: return null
        return AuthenticatedUser(id = userId, email = user.email.orEmpty())
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
        val validUserId = userId.requireCloudUuid("user_id")
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
                eq("id", validUserId)
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
        .filter { it.id.normalizedUuidOrNull() != null && it.ownerId.normalizedUuidOrNull() != null }

    suspend fun findBusiness(): RemoteBusiness? {
        val localUserId = currentUser()?.id ?: error("La sesion local no esta disponible")
        val remoteUserId = auth.retrieveUserForCurrentSession(updateSession = true)
            .id
            .requireCloudUuid("user_id")
        check(remoteUserId == localUserId) { "La sesion remota pertenece a otro usuario" }
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
        val validUserId = userId.requireCloudUuid("user_id")
        findBusiness()?.let { return it }

        // Evita INSERT ... RETURNING: la membresía se crea en un trigger
        // AFTER INSERT y la política SELECT podría evaluarse antes de verla.
        client.from("businesses").insert(
            CreateBusinessRequest(
                ownerId = validUserId,
                name = name.trim(),
                address = address.trim().ifBlank { null },
                phone = phone.trim().ifBlank { null },
                logoPath = logoPath
            )
        )
        val business = findBusiness()
            ?: error("El negocio se creó, pero la membresía del propietario no está disponible.")
        cache.save(validUserId, business)
        return business
    }

    suspend fun uploadBusinessLogo(businessId: String, localPath: String): String {
        val validBusinessId = businessId.requireCloudUuid("business_id")
        val file = File(localPath)
        require(file.exists()) { "No se encontró el logo seleccionado." }
        val remotePath = "$validBusinessId/logo-${System.currentTimeMillis()}.jpg"
        client.storage.from("business-assets").upload(remotePath, file.readBytes()) { upsert = false }
        client.postgrest.rpc(
            "set_business_logo",
            Json.encodeToJsonElement(BusinessLogoRpc.serializer(), BusinessLogoRpc(validBusinessId, remotePath)).jsonObject
        )
        return remotePath
    }

    suspend fun saveLocalProfile(displayName: String, business: RemoteBusiness, localLogoPath: String?) {
        UserPreferencesRepository(appContext).saveProfile(
            UserProfile(displayName.trim(), business.name, business.address.orEmpty(), localLogoPath)
        )
    }

    fun cachedBusiness(userId: String): RemoteBusiness? = cache.read(userId.requireCloudUuid("user_id"))

    fun cacheBusiness(userId: String, business: RemoteBusiness) = cache.save(userId.requireCloudUuid("user_id"), business)

    fun canOpenLocalData(userId: String): Boolean = localDataOwner.read()?.let { it == userId } ?: true

    suspend fun prepareForAuthenticatedUser(userId: String) {
        val validUserId = userId.requireCloudUuid("user_id")
        val storedOwnerChanged = shouldClearForAuthenticatedUser(localDataOwner.read(), validUserId)
        val activeOwnerChanged = activeBusiness.userId()?.let { it != validUserId } == true
        if (storedOwnerChanged || activeOwnerChanged) {
            clearLocalAccountData(cancelScheduledSync = true)
        }
    }

    suspend fun bindLocalDataTo(userId: String, businessId: String) {
        val validUserId = userId.requireCloudUuid("user_id")
        val validBusinessId = businessId.requireCloudUuid("business_id")
        prepareForAuthenticatedUser(validUserId)
        localDataOwner.bindIfEmpty(validUserId)
        activeBusiness.set(validUserId, validBusinessId)
        val now = System.currentTimeMillis()
        database.withTransaction {
            database.productoDao().bindUnownedRows(validBusinessId, now)
            database.clienteDao().bindUnownedRows(validBusinessId, now)
            database.ventaDao().bindUnownedSales(validBusinessId, now)
            database.ventaDao().bindUnownedSaleItems(validBusinessId)
            database.ventaDao().bindUnownedPayments(validBusinessId)
            database.syncDao().insertBusinessSettingsIfMissing(
                BusinessSettings(business_id = validBusinessId, updated_at = now, sync_status = com.example.posapp.data.entities.SyncStatus.SYNCED)
            )
        }
    }

    suspend fun preparePendingLocalData(): PendingLocalData {
        val businessId = activeBusiness.businessId().requireCloudUuid("business_id")
        CloudSyncCoordinator.prepareQueue(appContext, businessId)
        return pendingLocalData(businessId)
    }

    suspend fun tryImmediateSync(timeoutMillis: Long = 20_000): PendingLocalData {
        val businessId = activeBusiness.businessId().requireCloudUuid("business_id")
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
        try {
            withTimeoutOrNull(5_000) { auth.signOut() }
        } finally {
            auth.clearSession()
        }
    }

    suspend fun signOutAndClearLocalData() {
        signOutAndAlwaysClear(
            remoteSignOut = {
                try {
                    withTimeoutOrNull(5_000) { auth.signOut() }
                } finally {
                    auth.clearSession()
                }
            },
            clearLocalData = { clearLocalAccountData(cancelScheduledSync = true) }
        )
    }

    suspend fun clearLocalAccountData(cancelScheduledSync: Boolean = true) {
        if (cancelScheduledSync) CloudSyncScheduler.cancelAll(appContext)
        withContext(Dispatchers.IO) {
            database.clearAllTables()
            listOf("images", "backups", "exports").forEach { directoryName ->
                File(appContext.filesDir, directoryName).deleteRecursively()
            }
            appContext.cacheDir.resolve("spacesale").deleteRecursively()
        }
        UserPreferencesRepository(appContext).clear()
        cache.clearAll()
        activeBusiness.clear()
        localDataOwner.clear()
    }
}

data class AuthenticatedUser(val id: String, val email: String) {
    init { id.requireCloudUuid("user_id") }
}

internal fun otpTypeFor(isRegistration: Boolean): OtpType.Email =
    if (isRegistration) OtpType.Email.SIGNUP else OtpType.Email.EMAIL

private class AuthBusinessCache(context: Context) {
    private val preferences = context.getSharedPreferences("auth_business_cache", Context.MODE_PRIVATE)

    fun save(userId: String, business: RemoteBusiness) {
        val validUserId = userId.requireCloudUuid("user_id")
        val validBusinessId = business.id.requireCloudUuid("business_id")
        val validOwnerId = business.ownerId.requireCloudUuid("owner_id")
        preferences.edit {
            putString("user_id", validUserId)
            putString("business_id", validBusinessId)
            putString("owner_id", validOwnerId)
            putString("business_name", business.name)
            putString("business_address", business.address)
            putString("business_phone", business.phone)
            putString("business_logo_path", business.logoPath)
        }
    }

    fun read(userId: String): RemoteBusiness? {
        val validUserId = userId.normalizedUuidOrNull() ?: return null
        if (preferences.getString("user_id", null).normalizedUuidOrNull() != validUserId) return null
        val id = preferences.getString("business_id", null).normalizedUuidOrNull() ?: return null
        val ownerId = preferences.getString("owner_id", null).normalizedUuidOrNull() ?: return null
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

    fun clearAll() = preferences.edit { clear() }
}

private class LocalDataOwner(context: Context) {
    private val preferences = context.getSharedPreferences("local_data_owner", Context.MODE_PRIVATE)

    fun read(): String? = preferences.getString("user_id", null)?.trim()?.takeIf(String::isNotEmpty)

    fun bindIfEmpty(userId: String) {
        val validUserId = userId.requireCloudUuid("user_id")
        if (read() == null) preferences.edit { putString("user_id", validUserId) }
    }

    fun clear() = preferences.edit { clear() }
}
