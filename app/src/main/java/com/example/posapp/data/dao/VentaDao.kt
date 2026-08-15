package com.example.posapp.data.dao

import androidx.room.*
import com.example.posapp.data.entities.DetalleVenta
import com.example.posapp.data.entities.PagoFiado
import com.example.posapp.data.entities.Venta
import kotlinx.coroutines.flow.Flow

@Dao
interface VentaDao {
    @Query("UPDATE venta SET business_id = :businessId, updated_at = :now, sync_status = 'PENDING' WHERE business_id = ''")
    suspend fun bindUnownedSales(businessId: String, now: Long)

    @Query("UPDATE detalle_venta SET business_id = :businessId, sync_status = 'PENDING' WHERE business_id = ''")
    suspend fun bindUnownedSaleItems(businessId: String)

    @Query("UPDATE pago_fiado SET business_id = :businessId, sync_status = 'PENDING' WHERE business_id = ''")
    suspend fun bindUnownedPayments(businessId: String)

    @Insert
    suspend fun insertVenta(venta: Venta): Long

    @Insert
    suspend fun insertDetalle(detalle: DetalleVenta)

    @Update
    suspend fun updateDetalle(detalle: DetalleVenta)

    @Query("SELECT * FROM venta WHERE id = :id")
    suspend fun getById(id: Long): Venta?

    @Query("SELECT * FROM venta WHERE sync_id = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): Venta?

    @Query("SELECT * FROM detalle_venta WHERE sync_id = :syncId LIMIT 1")
    suspend fun getDetailBySyncId(syncId: String): DetalleVenta?

    @Query("SELECT * FROM pago_fiado WHERE sync_id = :syncId LIMIT 1")
    suspend fun getPaymentBySyncId(syncId: String): PagoFiado?

    @Query("SELECT * FROM venta WHERE estado = :estado ORDER BY fecha_hora DESC")
    fun getVentasPorEstado(estado: String): Flow<List<Venta>>

    @Query("SELECT * FROM venta ORDER BY fecha_hora DESC")
    suspend fun getAllVentas(): List<Venta>

    @Query("UPDATE venta SET nube_sincronizada = 1, sync_status = 'SYNCED', remote_updated_at = :now WHERE id IN(:ids)")
    suspend fun markVentasSynced(ids: List<Long>, now: Long)

    @Query("""
        UPDATE venta
        SET nube_sincronizada = 1, sync_status = 'SYNCED', remote_updated_at = :now
        WHERE sync_id = :syncId AND updated_at <= :localVersion
    """)
    suspend fun markVentaSyncedBySyncId(syncId: String, localVersion: Long, now: Long)

    @Query("SELECT * FROM venta ORDER BY fecha_hora DESC")
    fun observeAllVentas(): Flow<List<Venta>>

    @Query("SELECT * FROM detalle_venta")
    suspend fun getAllDetalles(): List<DetalleVenta>

    @Query("UPDATE detalle_venta SET nube_sincronizada = 1, sync_status = 'SYNCED', remote_updated_at = :now WHERE id IN(:ids)")
    suspend fun markDetallesSynced(ids: List<Long>, now: Long)

    @Query("UPDATE detalle_venta SET nube_sincronizada = 1, sync_status = 'SYNCED', remote_updated_at = :now WHERE sync_id IN(:syncIds)")
    suspend fun markDetallesSyncedBySyncIds(syncIds: List<String>, now: Long)

    @Query("SELECT * FROM detalle_venta")
    fun observeAllDetalles(): Flow<List<DetalleVenta>>

    @Query("SELECT * FROM detalle_venta WHERE ventaId = :ventaId")
    suspend fun getDetallesForVenta(ventaId: Long): List<DetalleVenta>

    @Query("SELECT * FROM detalle_venta WHERE id IN(:ids)")
    suspend fun getDetallesByIds(ids: List<Long>): List<DetalleVenta>

    @Query("""
        SELECT d.* FROM detalle_venta d
        INNER JOIN venta v ON v.id = d.ventaId
        WHERE v.clienteId = :clienteId
          AND v.estado = 'PENDIENTE'
          AND IFNULL((SELECT SUM(p.monto_centavos) FROM pago_fiado p WHERE p.detalleId = d.id), 0)
              < d.cantidad * d.precio_unitario_centavos
    """)
    suspend fun getDetallesPendientesCliente(clienteId: Long): List<DetalleVenta>

    @Insert
    suspend fun insertPago(pago: PagoFiado)

    @Update
    suspend fun updatePago(pago: PagoFiado)

    @Query("SELECT IFNULL(SUM(monto_centavos), 0) FROM pago_fiado WHERE ventaId = :ventaId")
    suspend fun getTotalPagado(ventaId: Long): Long

    @Query("SELECT IFNULL(SUM(monto_centavos), 0) FROM pago_fiado WHERE detalleId = :detalleId")
    suspend fun getTotalPagadoDetalle(detalleId: Long): Long

    @Query("SELECT * FROM pago_fiado ORDER BY fecha_hora")
    suspend fun getAllPagos(): List<PagoFiado>

    @Query("""
        SELECT p.* FROM pago_fiado p
        INNER JOIN venta v ON v.id = p.ventaId
        WHERE v.clienteId = :clientId
        ORDER BY p.fecha_hora DESC
    """)
    suspend fun getPaymentsForClient(clientId: Long): List<PagoFiado>

    @Query("UPDATE cliente SET deuda_total = 0.0, deuda_total_centavos = :debtCents WHERE id = :clientId")
    suspend fun updateClientDebtFromRemote(clientId: Long, debtCents: Long)

    @Query("UPDATE pago_fiado SET nube_sincronizada = 1, sync_status = 'SYNCED', remote_updated_at = :now WHERE id IN(:ids)")
    suspend fun markPagosSynced(ids: List<Long>, now: Long)

    @Query("UPDATE pago_fiado SET nube_sincronizada = 1, sync_status = 'SYNCED', remote_updated_at = :now WHERE sync_id = :syncId")
    suspend fun markPagoSyncedBySyncId(syncId: String, now: Long)

    @Update
    suspend fun updateVenta(venta: Venta)
}
