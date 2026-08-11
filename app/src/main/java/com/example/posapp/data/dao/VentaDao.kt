package com.example.posapp.data.dao

import androidx.room.*
import com.example.posapp.data.entities.DetalleVenta
import com.example.posapp.data.entities.Venta
import kotlinx.coroutines.flow.Flow

@Dao
interface VentaDao {
    @Insert
    suspend fun insertVenta(venta: Venta): Long

    @Insert
    suspend fun insertDetalle(detalle: DetalleVenta)

    @Transaction
    suspend fun insertVentaConDetalles(venta: Venta, detalles: List<DetalleVenta>, productoDao: ProductoDao): Long {
        val ventaId = insertVenta(venta)
        for (d in detalles) {
            val detalleConIdVenta = d.copy(ventaId = ventaId)
            insertDetalle(detalleConIdVenta)
            val affected = productoDao.decreaseStockIfEnough(d.productoId, d.cantidad)
            if (affected == 0) {
                throw IllegalStateException("Stock insuficiente para producto ${d.productoId}")
            }
        }
        return ventaId
    }

    @Query("SELECT * FROM venta WHERE id = :id")
    suspend fun getById(id: Long): Venta?

    @Query("SELECT * FROM venta WHERE estado = :estado ORDER BY fecha_hora DESC")
    fun getVentasPorEstado(estado: String): Flow<List<Venta>>

    @Query("SELECT * FROM venta ORDER BY fecha_hora DESC")
    suspend fun getAllVentas(): List<Venta>

    @Query("SELECT * FROM detalle_venta")
    suspend fun getAllDetalles(): List<DetalleVenta>

    @Transaction
    suspend fun cancelVenta(ventaId: Long, productoDao: ProductoDao) {
        // restore stock for all detalles and mark venta as ANULADO
        val detalles = getDetallesForVenta(ventaId)
        for (d in detalles) {
            productoDao.increaseStock(d.productoId, d.cantidad)
        }
        val venta = getById(ventaId)
        if (venta != null) {
            val updated = venta.copy(estado = "ANULADO")
            updateVenta(updated)
        }
    }

    @Query("SELECT * FROM detalle_venta WHERE ventaId = :ventaId")
    suspend fun getDetallesForVenta(ventaId: Long): List<DetalleVenta>

    @Query("DELETE FROM detalle_venta WHERE id IN(:ids)")
    suspend fun deleteDetallesByIds(ids: List<Long>)

    @Update
    suspend fun updateVenta(venta: Venta)
}
