package com.example.posapp.data.dao

import androidx.room.*
import com.example.posapp.data.entities.Cliente
import kotlinx.coroutines.flow.Flow

@Dao
interface ClienteDao {
    @Query("SELECT * FROM cliente WHERE id = :id")
    suspend fun getById(id: Long): Cliente?

    @Query("SELECT * FROM cliente WHERE sync_id = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): Cliente?

    @Query("SELECT * FROM cliente WHERE deleted_at IS NULL ORDER BY nombre")
    fun getAll(): Flow<List<Cliente>>

    @Query("SELECT * FROM cliente ORDER BY id")
    suspend fun getAllForSync(): List<Cliente>

    @Query("UPDATE cliente SET sync_status = 'SYNCED', remote_updated_at = :remoteUpdatedAt WHERE id IN(:ids)")
    suspend fun markSynced(ids: List<Long>, remoteUpdatedAt: Long)

    @Query("""
        UPDATE cliente
        SET sync_status = 'SYNCED', remote_updated_at = :remoteUpdatedAt
        WHERE sync_id = :syncId AND updated_at <= :localVersion
    """)
    suspend fun markSyncedBySyncId(syncId: String, localVersion: Long, remoteUpdatedAt: Long)

    @Query("UPDATE cliente SET business_id = :businessId, updated_at = :now, sync_status = 'PENDING' WHERE business_id = ''")
    suspend fun bindUnownedRows(businessId: String, now: Long)

    @Insert
    suspend fun insert(cliente: Cliente): Long

    @Update
    suspend fun update(cliente: Cliente)

    @Delete
    suspend fun delete(cliente: Cliente)

    @Query("""
        SELECT IFNULL(SUM(v.total_centavos - IFNULL(
            (SELECT SUM(p.monto_centavos) FROM pago_fiado p WHERE p.ventaId = v.id), 0
        )), 0)
        FROM venta v
        WHERE v.clienteId = :clienteId AND v.estado = 'PENDIENTE'
    """)
    suspend fun calcularDeuda(clienteId: Long): Long

    // Get only clients with positive debt
    @Query("SELECT * FROM cliente WHERE deleted_at IS NULL AND deuda_total_centavos > 0 ORDER BY deuda_total_centavos DESC")
    fun obtenerDeudores(): kotlinx.coroutines.flow.Flow<List<Cliente>>
}
