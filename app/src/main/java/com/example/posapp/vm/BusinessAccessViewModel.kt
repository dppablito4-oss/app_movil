package com.example.posapp.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.posapp.auth.RemoteBusiness
import com.example.posapp.data.ActiveBusinessStore
import com.example.posapp.data.AppDatabase
import com.example.posapp.data.entities.SyncStatus
import com.example.posapp.data.remote.SupabaseProvider
import com.example.posapp.data.sync.CloudSyncScheduler
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class BusinessAccess(
    val business: RemoteBusiness,
    val role: String,
    val isActive: Boolean
)

data class BusinessAccessUiState(
    val isLoading: Boolean = true,
    val businesses: List<BusinessAccess> = emptyList(),
    val message: String? = null
)

class BusinessAccessViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val activeStore = ActiveBusinessStore(application)
    private val _state = MutableStateFlow(BusinessAccessUiState())
    val state: StateFlow<BusinessAccessUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, message = null)
            runCatching {
                val client = SupabaseProvider.client
                val user = client.auth.currentUserOrNull() ?: error("La sesion no esta disponible")
                val businesses = client.from("businesses").select().decodeList<RemoteBusiness>()
                val memberships = client.from("business_members").select {
                    filter { eq("user_id", user.id) }
                }.decodeList<RemoteMembership>().associateBy { it.businessId }
                businesses.map { business ->
                    BusinessAccess(
                        business = business,
                        role = memberships[business.id]?.role ?: if (business.ownerId == user.id) "owner" else "member",
                        isActive = business.id == activeStore.businessId()
                    )
                }
            }.onSuccess { rows -> _state.value = BusinessAccessUiState(false, rows) }
                .onFailure { error -> _state.value = BusinessAccessUiState(false, message = error.message ?: "No se pudieron cargar los negocios") }
        }
    }

    fun switchBusiness(target: BusinessAccess, onComplete: (String?) -> Unit) {
        viewModelScope.launch {
            val error = runCatching {
                if (target.isActive) return@runCatching
                val dirty = database.productoDao().getAllForSync().any { it.sync_status != SyncStatus.SYNCED } ||
                    database.clienteDao().getAllForSync().any { it.sync_status != SyncStatus.SYNCED } ||
                    database.ventaDao().getAllVentas().any { it.sync_status != SyncStatus.SYNCED } ||
                    database.ventaDao().getAllDetalles().any { it.sync_status != SyncStatus.SYNCED } ||
                    database.ventaDao().getAllPagos().any { it.sync_status != SyncStatus.SYNCED } ||
                    database.stockMovementDao().getAllForSync().any { it.sync_status != SyncStatus.SYNCED }
                require(!dirty) { "Sincroniza los cambios pendientes antes de cambiar de negocio" }
                val userId = SupabaseProvider.client.auth.currentUserOrNull()?.id ?: error("La sesion no esta disponible")
                CloudSyncScheduler.cancelAll(getApplication<Application>().applicationContext)
                database.clearAllTables()
                activeStore.set(userId, target.business.id)
                CloudSyncScheduler.schedule(getApplication<Application>().applicationContext)
            }.exceptionOrNull()?.message
            onComplete(error)
        }
    }
}

@Serializable
private data class RemoteMembership(
    @SerialName("business_id") val businessId: String,
    val role: String
)
