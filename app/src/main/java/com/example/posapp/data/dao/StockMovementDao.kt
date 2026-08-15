package com.example.posapp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.posapp.data.entities.StockMovement
import kotlinx.coroutines.flow.Flow

@Dao
interface StockMovementDao {
    @Insert
    suspend fun insert(movement: StockMovement): Long

    @Query("SELECT * FROM stock_movement WHERE business_id = :businessId AND sync_id = :syncId LIMIT 1")
    suspend fun getBySyncId(businessId: String, syncId: String): StockMovement?

    @Query("SELECT * FROM stock_movement WHERE business_id = :businessId ORDER BY created_at")
    suspend fun getAllForSync(businessId: String): List<StockMovement>

    @Query("SELECT * FROM stock_movement WHERE business_id = :businessId AND productId = :productId ORDER BY created_at DESC")
    fun observeForProduct(businessId: String, productId: Long): Flow<List<StockMovement>>

    @Query("UPDATE stock_movement SET sync_status = 'SYNCED', remote_created_at = :remoteCreatedAt WHERE business_id = :businessId AND sync_id = :syncId")
    suspend fun markSynced(businessId: String, syncId: String, remoteCreatedAt: Long)
}
