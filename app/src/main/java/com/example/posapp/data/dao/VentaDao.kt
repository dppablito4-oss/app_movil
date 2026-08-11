package com.example.posapp.data.dao

import androidx.room.*
import com.example.posapp.data.entities.DetalleVenta
import com.example.posapp.data.entities.PagoFiado
import com.example.posapp.data.entities.Venta
import kotlinx.coroutines.flow.Flow

@Dao
interface VentaDao {
    @Insert
    suspend fun insertVenta(venta: Venta): Long

    @Insert
    suspend fun insertDetalle(detalle: DetalleVenta)

    @Query("SELECT * FROM venta WHERE id = :id")
    suspend fun getById(id: Long): Venta?

    @Query("SELECT * FROM venta WHERE estado = :estado ORDER BY fecha_hora DESC")
    fun getVentasPorEstado(estado: String): Flow<List<Venta>>

    @Query("SELECT * FROM venta ORDER BY fecha_hora DESC")
    suspend fun getAllVentas(): List<Venta>

    @Query("SELECT * FROM venta ORDER BY fecha_hora DESC")
    fun observeAllVentas(): Flow<List<Venta>>

    @Query("SELECT * FROM detalle_venta")
    suspend fun getAllDetalles(): List<DetalleVenta>

    @Query("SELECT * FROM detalle_venta")
    fun observeAllDetalles(): Flow<List<DetalleVenta>>

    @Query("SELECT * FROM detalle_venta WHERE ventaId = :ventaId")
    suspend fun getDetallesForVenta(ventaId: Long): List<DetalleVenta>

    @Query("SELECT * FROM detalle_venta WHERE id IN(:ids)")
    suspend fun getDetallesByIds(ids: List<Long>): List<DetalleVenta>

    @Query("""
        SELECT d.* FROM detalle_venta d
        INNER JOIN venta v ON v.id = d.ventaId
        LEFT JOIN pago_fiado p ON p.detalleId = d.id
        WHERE v.clienteId = :clienteId AND v.estado = 'PENDIENTE' AND p.id IS NULL
    """)
    suspend fun getDetallesPendientesCliente(clienteId: Long): List<DetalleVenta>

    @Insert
    suspend fun insertPago(pago: PagoFiado)

    @Query("SELECT IFNULL(SUM(monto), 0) FROM pago_fiado WHERE ventaId = :ventaId")
    suspend fun getTotalPagado(ventaId: Long): Double

    @Query("SELECT * FROM pago_fiado ORDER BY fecha_hora")
    suspend fun getAllPagos(): List<PagoFiado>

    @Update
    suspend fun updateVenta(venta: Venta)
}
