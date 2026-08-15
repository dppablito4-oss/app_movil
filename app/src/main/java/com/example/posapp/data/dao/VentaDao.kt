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

    @Query("SELECT * FROM venta WHERE business_id = :businessId AND id = :id")
    suspend fun getById(businessId: String, id: Long): Venta?

    @Query("SELECT * FROM venta WHERE business_id = :businessId AND sync_id = :syncId LIMIT 1")
    suspend fun getBySyncId(businessId: String, syncId: String): Venta?

    @Query("SELECT * FROM detalle_venta WHERE business_id = :businessId AND sync_id = :syncId LIMIT 1")
    suspend fun getDetailBySyncId(businessId: String, syncId: String): DetalleVenta?

    @Query("SELECT * FROM pago_fiado WHERE business_id = :businessId AND sync_id = :syncId LIMIT 1")
    suspend fun getPaymentBySyncId(businessId: String, syncId: String): PagoFiado?

    @Query("SELECT * FROM venta WHERE business_id = :businessId AND estado = :estado ORDER BY fecha_hora DESC")
    fun getVentasPorEstado(businessId: String, estado: String): Flow<List<Venta>>

    @Query("SELECT * FROM venta WHERE business_id = :businessId ORDER BY fecha_hora DESC")
    suspend fun getAllVentas(businessId: String): List<Venta>

    @Query("""
        UPDATE venta
        SET nube_sincronizada = 1, sync_status = 'SYNCED', remote_updated_at = :now
        WHERE business_id = :businessId AND sync_id = :syncId AND updated_at <= :localVersion
    """)
    suspend fun markVentaSyncedBySyncId(businessId: String, syncId: String, localVersion: Long, now: Long)

    @Query("SELECT * FROM venta WHERE business_id = :businessId ORDER BY fecha_hora DESC")
    fun observeAllVentas(businessId: String): Flow<List<Venta>>

    @Query("SELECT * FROM detalle_venta WHERE business_id = :businessId")
    suspend fun getAllDetalles(businessId: String): List<DetalleVenta>

    @Query("UPDATE detalle_venta SET nube_sincronizada = 1, sync_status = 'SYNCED', remote_updated_at = :now WHERE business_id = :businessId AND sync_id IN(:syncIds)")
    suspend fun markDetallesSyncedBySyncIds(businessId: String, syncIds: List<String>, now: Long)

    @Query("SELECT * FROM detalle_venta WHERE business_id = :businessId")
    fun observeAllDetalles(businessId: String): Flow<List<DetalleVenta>>

    @Query("SELECT * FROM detalle_venta WHERE business_id = :businessId AND ventaId = :ventaId")
    suspend fun getDetallesForVenta(businessId: String, ventaId: Long): List<DetalleVenta>

    @Query("SELECT * FROM detalle_venta WHERE business_id = :businessId AND id IN(:ids)")
    suspend fun getDetallesByIds(businessId: String, ids: List<Long>): List<DetalleVenta>

    @Query("""
        SELECT d.* FROM detalle_venta d
        INNER JOIN venta v ON v.id = d.ventaId
        WHERE d.business_id = :businessId AND v.business_id = :businessId AND v.clienteId = :clienteId
          AND v.estado = 'PENDIENTE'
          AND IFNULL((SELECT SUM(p.monto_centavos) FROM pago_fiado p WHERE p.detalleId = d.id), 0)
              < d.cantidad * d.precio_unitario_centavos
    """)
    suspend fun getDetallesPendientesCliente(businessId: String, clienteId: Long): List<DetalleVenta>

    @Insert
    suspend fun insertPago(pago: PagoFiado)

    @Update
    suspend fun updatePago(pago: PagoFiado)

    @Query("SELECT IFNULL(SUM(monto_centavos), 0) FROM pago_fiado WHERE business_id = :businessId AND ventaId = :ventaId")
    suspend fun getTotalPagado(businessId: String, ventaId: Long): Long

    @Query("SELECT IFNULL(SUM(monto_centavos), 0) FROM pago_fiado WHERE business_id = :businessId AND detalleId = :detalleId")
    suspend fun getTotalPagadoDetalle(businessId: String, detalleId: Long): Long

    @Query("SELECT * FROM pago_fiado WHERE business_id = :businessId ORDER BY fecha_hora")
    suspend fun getAllPagos(businessId: String): List<PagoFiado>

    @Query("SELECT * FROM pago_fiado WHERE business_id = :businessId ORDER BY fecha_hora DESC")
    fun observeAllPagos(businessId: String): Flow<List<PagoFiado>>

    @Query("""
        SELECT p.* FROM pago_fiado p
        INNER JOIN venta v ON v.id = p.ventaId
        WHERE p.business_id = :businessId AND v.business_id = :businessId AND v.clienteId = :clientId
        ORDER BY p.fecha_hora DESC
    """)
    suspend fun getPaymentsForClient(businessId: String, clientId: Long): List<PagoFiado>

    @Query("UPDATE cliente SET deuda_total = 0.0, deuda_total_centavos = :debtCents WHERE business_id = :businessId AND id = :clientId")
    suspend fun updateClientDebtFromRemote(businessId: String, clientId: Long, debtCents: Long)

    @Query("UPDATE pago_fiado SET nube_sincronizada = 1, sync_status = 'SYNCED', remote_updated_at = :now WHERE business_id = :businessId AND sync_id = :syncId")
    suspend fun markPagoSyncedBySyncId(businessId: String, syncId: String, now: Long)

    @Update
    suspend fun updateVenta(venta: Venta)
}
