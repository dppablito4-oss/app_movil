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

    @Query("SELECT * FROM stock_movement WHERE sync_id = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): StockMovement?

    @Query("SELECT * FROM stock_movement ORDER BY created_at")
    suspend fun getAllForSync(): List<StockMovement>

    @Query("SELECT * FROM stock_movement WHERE productId = :productId ORDER BY created_at DESC")
    fun observeForProduct(productId: Long): Flow<List<StockMovement>>

    @Query("UPDATE stock_movement SET sync_status = 'SYNCED', remote_created_at = :remoteCreatedAt WHERE sync_id = :syncId")
    suspend fun markSynced(syncId: String, remoteCreatedAt: Long)
}
