package com.example.posapp.data.dao

import androidx.room.*
import com.example.posapp.data.entities.Producto
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductoDao {
    @Query("SELECT * FROM producto WHERE business_id = :businessId AND id = :id")
    suspend fun getById(businessId: String, id: Long): Producto?

    @Query("SELECT * FROM producto WHERE business_id = :businessId AND sync_id = :syncId LIMIT 1")
    suspend fun getBySyncId(businessId: String, syncId: String): Producto?

    @Query("SELECT * FROM producto WHERE business_id = :businessId AND codigo_barras = :barcode AND deleted_at IS NULL LIMIT 1")
    suspend fun getByBarcode(businessId: String, barcode: String): Producto?

    @Query("SELECT * FROM producto WHERE business_id = :businessId AND deleted_at IS NULL ORDER BY nombre")
    fun getAll(businessId: String): Flow<List<Producto>>

    @Query("SELECT * FROM producto WHERE business_id = :businessId ORDER BY id")
    suspend fun getAllForSync(businessId: String): List<Producto>

    @Query("""
        UPDATE producto
        SET sync_status = 'SYNCED', remote_updated_at = :remoteUpdatedAt
        WHERE business_id = :businessId AND sync_id = :syncId AND updated_at <= :localVersion
    """)
    suspend fun markSyncedBySyncId(businessId: String, syncId: String, localVersion: Long, remoteUpdatedAt: Long)

    @Query("UPDATE producto SET storage_path = :storagePath, image_sync_status = 'SYNCED' WHERE business_id = :businessId AND sync_id = :syncId")
    suspend fun markImageUploaded(businessId: String, syncId: String, storagePath: String)

    @Query("UPDATE producto SET image_sync_status = 'ERROR' WHERE business_id = :businessId AND sync_id = :syncId")
    suspend fun markImageUploadFailed(businessId: String, syncId: String)

    @Query("UPDATE producto SET ruta_imagen = :localPath, image_sync_status = 'SYNCED' WHERE business_id = :businessId AND sync_id = :syncId")
    suspend fun setCachedImage(businessId: String, syncId: String, localPath: String)

    @Query("UPDATE producto SET business_id = :businessId, updated_at = :now, sync_status = 'PENDING' WHERE business_id = ''")
    suspend fun bindUnownedRows(businessId: String, now: Long)

    @Insert
    suspend fun insert(producto: Producto): Long

    @Update
    suspend fun update(producto: Producto)

    @Delete
    suspend fun delete(producto: Producto)

    // Decrement stock atomically; returns number of rows affected (0 => not enough stock)
    @Query("UPDATE producto SET stock = stock - :cantidad, updated_at = :now, sync_status = 'PENDING' WHERE business_id = :businessId AND id = :id AND stock >= :cantidad")
    suspend fun decreaseStockIfEnough(businessId: String, id: Long, cantidad: Int, now: Long): Int

    @Query("UPDATE producto SET stock = stock + :cantidad, updated_at = :now, sync_status = 'PENDING' WHERE business_id = :businessId AND id = :id")
    suspend fun increaseStock(businessId: String, id: Long, cantidad: Int, now: Long)

    @Query("SELECT * FROM producto WHERE business_id = :businessId AND deleted_at IS NULL AND (busqueda_normalizada LIKE :query || '%' OR codigo_barras LIKE :query || '%') ORDER BY nombre")
    fun searchProductos(businessId: String, query: String): Flow<List<Producto>>
}
