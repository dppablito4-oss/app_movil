package com.example.posapp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.example.posapp.data.entities.BusinessSettings
import com.example.posapp.data.entities.SyncMetadata
import com.example.posapp.data.entities.SyncQueueItem
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueue(item: SyncQueueItem): Long

    @Query("""
        SELECT * FROM sync_queue
        WHERE business_id = :businessId AND next_attempt_at <= :now
        ORDER BY CASE entity_type
            WHEN 'IMAGE_UPLOAD' THEN 1
            WHEN 'PRODUCT' THEN 2
            WHEN 'IMAGE_DELETE' THEN 3
            WHEN 'CUSTOMER' THEN 4
            WHEN 'SETTINGS' THEN 5
            WHEN 'SALE_BUNDLE' THEN 6
            WHEN 'SALE_TRANSITION' THEN 7
            WHEN 'SALE_ITEM' THEN 8
            WHEN 'PAYMENT' THEN 9
            WHEN 'STOCK_MOVEMENT' THEN 10
            ELSE 99 END,
            created_at, id
        LIMIT :limit
    """)
    suspend fun pendingOperations(businessId: String, now: Long, limit: Int = 100): List<SyncQueueItem>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE business_id = :businessId")
    suspend fun pendingCount(businessId: String): Int

    @Query("SELECT COUNT(*) FROM producto WHERE business_id = :businessId AND sync_status != 'SYNCED'")
    suspend fun pendingProducts(businessId: String): Int

    @Query("SELECT COUNT(*) FROM producto WHERE business_id = :businessId AND ruta_imagen IS NOT NULL AND ruta_imagen != '' AND image_sync_status != 'SYNCED'")
    suspend fun pendingImages(businessId: String): Int

    @Query("SELECT COUNT(*) FROM cliente WHERE business_id = :businessId AND sync_status != 'SYNCED'")
    suspend fun pendingCustomers(businessId: String): Int

    @Query("SELECT COUNT(*) FROM venta WHERE business_id = :businessId AND sync_status != 'SYNCED'")
    suspend fun pendingSales(businessId: String): Int

    @Query("SELECT COUNT(*) FROM detalle_venta WHERE business_id = :businessId AND sync_status != 'SYNCED'")
    suspend fun pendingSaleItems(businessId: String): Int

    @Query("SELECT COUNT(*) FROM pago_fiado WHERE business_id = :businessId AND sync_status != 'SYNCED'")
    suspend fun pendingPayments(businessId: String): Int

    @Query("SELECT COUNT(*) FROM stock_movement WHERE business_id = :businessId AND sync_status != 'SYNCED'")
    suspend fun pendingStockMovements(businessId: String): Int

    @Query("SELECT COUNT(*) FROM business_settings WHERE business_id = :businessId AND sync_status != 'SYNCED'")
    suspend fun pendingBusinessSettings(businessId: String): Int

    @Query("SELECT MIN(next_attempt_at) FROM sync_queue WHERE business_id = :businessId")
    suspend fun nextAttemptAt(businessId: String): Long?

    @Query("SELECT COUNT(*) > 0 FROM sync_queue WHERE business_id = :businessId AND entity_type = :entityType AND entity_sync_id = :syncId")
    suspend fun hasPendingOperation(businessId: String, entityType: String, syncId: String): Boolean

    @Query("DELETE FROM sync_queue WHERE operation_id = :operationId")
    suspend fun confirm(operationId: String)

    @Query("UPDATE sync_queue SET attempt_count = attempt_count + 1, next_attempt_at = :nextAttemptAt, last_error = :message WHERE operation_id = :operationId")
    suspend fun markFailed(operationId: String, nextAttemptAt: Long, message: String)

    @Query("SELECT COUNT(*) FROM sync_queue WHERE business_id = :businessId")
    fun observePendingCount(businessId: String): Flow<Int>

    @Query("SELECT last_error FROM sync_queue WHERE business_id = :businessId AND last_error IS NOT NULL ORDER BY next_attempt_at DESC, id DESC LIMIT 1")
    fun observeLatestError(businessId: String): Flow<String?>

    @Upsert
    suspend fun upsertMetadata(metadata: SyncMetadata)

    @Query("SELECT * FROM sync_metadata WHERE business_id = :businessId AND entity_type = :entityType")
    suspend fun metadata(businessId: String, entityType: String): SyncMetadata?

    @Upsert
    suspend fun upsertBusinessSettings(settings: BusinessSettings)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBusinessSettingsIfMissing(settings: BusinessSettings)

    @Query("SELECT * FROM business_settings WHERE business_id = :businessId")
    fun observeBusinessSettings(businessId: String): Flow<BusinessSettings?>

    @Query("SELECT * FROM business_settings WHERE business_id = :businessId")
    suspend fun businessSettings(businessId: String): BusinessSettings?

    @Query("UPDATE business_settings SET sync_status = 'SYNCED', updated_at = :updatedAt WHERE business_id = :businessId")
    suspend fun markBusinessSettingsSynced(businessId: String, updatedAt: Long)
}
