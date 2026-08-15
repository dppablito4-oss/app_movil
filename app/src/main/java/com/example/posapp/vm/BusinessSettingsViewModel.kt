package com.example.posapp.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.posapp.data.ActiveBusinessStore
import com.example.posapp.data.AppDatabase
import com.example.posapp.data.entities.BusinessSettings
import com.example.posapp.data.entities.SyncStatus
import com.example.posapp.data.sync.CloudSyncScheduler
import com.example.posapp.utils.toCents
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BusinessSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getInstance(application).syncDao()
    private val businessId = ActiveBusinessStore(application).businessId()

    val settings: StateFlow<BusinessSettings?> = dao.observeBusinessSettings(businessId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        if (businessId.isNotBlank()) {
            viewModelScope.launch {
                dao.insertBusinessSettingsIfMissing(
                    BusinessSettings(business_id = businessId, sync_status = SyncStatus.SYNCED)
                )
            }
        }
    }

    fun save(
        dailyGoal: Double,
        lowStockEnabled: Boolean,
        receiptMessage: String,
        onComplete: (String?) -> Unit
    ) {
        viewModelScope.launch {
            val error = runCatching {
                require(dailyGoal.isFinite() && dailyGoal >= 0.0) { "Ingresa una meta valida" }
                require(businessId.isNotBlank()) { "No hay un negocio activo" }
                val current = dao.businessSettings(businessId) ?: BusinessSettings(business_id = businessId)
                dao.upsertBusinessSettings(
                    current.copy(
                        daily_goal_cents = dailyGoal.toCents(),
                        low_stock_enabled = lowStockEnabled,
                        receipt_message = receiptMessage.trim().take(240),
                        updated_at = System.currentTimeMillis(),
                        sync_status = SyncStatus.PENDING
                    )
                )
                CloudSyncScheduler.schedule(getApplication<Application>().applicationContext)
            }.exceptionOrNull()?.message
            onComplete(error)
        }
    }
}
