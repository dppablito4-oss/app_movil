package com.example.posapp.data.dao

import androidx.room.*
import com.example.posapp.data.entities.Cliente
import kotlinx.coroutines.flow.Flow

@Dao
interface ClienteDao {
    @Query("SELECT * FROM cliente WHERE business_id = :businessId AND id = :id")
    suspend fun getById(businessId: String, id: Long): Cliente?

    @Query("SELECT * FROM cliente WHERE business_id = :businessId AND sync_id = :syncId LIMIT 1")
    suspend fun getBySyncId(businessId: String, syncId: String): Cliente?

    @Query("SELECT * FROM cliente WHERE business_id = :businessId AND deleted_at IS NULL ORDER BY nombre")
    fun getAll(businessId: String): Flow<List<Cliente>>

    @Query("SELECT * FROM cliente WHERE business_id = :businessId ORDER BY id")
    suspend fun getAllForSync(businessId: String): List<Cliente>

    @Query("""
        UPDATE cliente
        SET sync_status = 'SYNCED', remote_updated_at = :remoteUpdatedAt
        WHERE business_id = :businessId AND sync_id = :syncId AND updated_at <= :localVersion
    """)
    suspend fun markSyncedBySyncId(businessId: String, syncId: String, localVersion: Long, remoteUpdatedAt: Long)

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
        WHERE v.business_id = :businessId AND v.clienteId = :clienteId AND v.estado = 'PENDIENTE'
    """)
    suspend fun calcularDeuda(businessId: String, clienteId: Long): Long

    // Get only clients with positive debt
    @Query("SELECT * FROM cliente WHERE business_id = :businessId AND deleted_at IS NULL AND deuda_total_centavos > 0 ORDER BY deuda_total_centavos DESC")
    fun obtenerDeudores(businessId: String): kotlinx.coroutines.flow.Flow<List<Cliente>>
}
