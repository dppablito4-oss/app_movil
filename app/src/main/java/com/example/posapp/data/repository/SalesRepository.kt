package com.example.posapp.data.repository

import androidx.room.withTransaction
import com.example.posapp.data.AppDatabase
import com.example.posapp.data.entities.Cliente
import com.example.posapp.data.entities.DetalleVenta
import com.example.posapp.data.entities.PagoFiado
import com.example.posapp.data.entities.Producto
import com.example.posapp.data.entities.Venta

data class SaleLine(val producto: Producto, val cantidad: Int)

class SalesRepository(private val db: AppDatabase) {
    private val productoDao = db.productoDao()
    private val ventaDao = db.ventaDao()
    private val clienteDao = db.clienteDao()

    suspend fun checkout(lines: List<SaleLine>, tipoPago: String, clienteId: Long?): Long = db.withTransaction {
        require(lines.isNotEmpty()) { "El carrito está vacío" }
        require(tipoPago in setOf("EFECTIVO", "YAPE", "FIADO")) { "Método de pago inválido" }
        require(tipoPago != "FIADO" || clienteId != null) { "Selecciona un cliente para la venta fiada" }

        val normalizedLines = lines.groupBy { it.producto.id }.map { (_, items) ->
            items.first().copy(cantidad = items.sumOf { it.cantidad })
        }
        require(normalizedLines.all { it.cantidad > 0 }) { "La cantidad debe ser mayor que cero" }

        val currentProducts = normalizedLines.associate { line ->
            val current = productoDao.getById(line.producto.id)
                ?: throw IllegalStateException("El producto ${line.producto.nombre} ya no existe")
            require(current.precio_venta >= 0.0) { "El producto ${current.nombre} tiene un precio inválido" }
            current.id to current
        }

        val total = normalizedLines.sumOf { line ->
            currentProducts.getValue(line.producto.id).precio_venta * line.cantidad
        }
        require(total > 0.0) { "El total de la venta debe ser mayor que cero" }

        val venta = Venta(
            fecha_hora = System.currentTimeMillis(),
            total = total,
            tipo_pago = tipoPago,
            clienteId = clienteId,
            estado = if (tipoPago == "FIADO") "PENDIENTE" else "CERRADO"
        )
        val ventaId = ventaDao.insertVenta(venta)

        normalizedLines.forEach { line ->
            val product = currentProducts.getValue(line.producto.id)
            val affected = productoDao.decreaseStockIfEnough(product.id, line.cantidad)
            if (affected == 0) {
                throw IllegalStateException("Stock insuficiente para ${product.nombre}")
            }
            ventaDao.insertDetalle(
                DetalleVenta(
                    ventaId = ventaId,
                    productoId = product.id,
                    cantidad = line.cantidad,
                    precio_unitario_historico = product.precio_venta
                )
            )
        }

        if (tipoPago == "FIADO") refreshClientDebt(requireNotNull(clienteId))
        ventaId
    }

    suspend fun payDetails(clientId: Long, detailIds: Set<Long>) = db.withTransaction {
        require(detailIds.isNotEmpty()) { "Selecciona al menos un producto" }
        val pending = ventaDao.getDetallesPendientesCliente(clientId).associateBy { it.id }
        val selected = detailIds.map { id ->
            pending[id] ?: throw IllegalStateException("Uno de los productos ya fue pagado o no pertenece al cliente")
        }
        val now = System.currentTimeMillis()
        selected.forEach { detail ->
            ventaDao.insertPago(
                PagoFiado(
                    ventaId = detail.ventaId,
                    detalleId = detail.id,
                    monto = detail.cantidad * detail.precio_unitario_historico,
                    fecha_hora = now
                )
            )
        }

        selected.map { it.ventaId }.distinct().forEach { ventaId ->
            val venta = ventaDao.getById(ventaId) ?: return@forEach
            val paid = ventaDao.getTotalPagado(ventaId)
            if (paid + 0.0001 >= venta.total) {
                ventaDao.updateVenta(venta.copy(estado = "CERRADO", fecha_pago = now))
            }
        }
        refreshClientDebt(clientId)
    }

    suspend fun deleteClient(cliente: Cliente) = db.withTransaction {
        val debt = clienteDao.calcularDeuda(cliente.id)
        require(debt <= 0.0001) { "No se puede eliminar un cliente con deuda pendiente" }
        clienteDao.delete(cliente)
    }

    suspend fun cancelSale(ventaId: Long) = db.withTransaction {
        val venta = ventaDao.getById(ventaId) ?: return@withTransaction
        if (venta.estado == "ANULADO") return@withTransaction
        ventaDao.getDetallesForVenta(ventaId).forEach { detail ->
            productoDao.increaseStock(detail.productoId, detail.cantidad)
        }
        ventaDao.updateVenta(venta.copy(estado = "ANULADO"))
        venta.clienteId?.let { refreshClientDebt(it) }
    }

    private suspend fun refreshClientDebt(clientId: Long) {
        val client = clienteDao.getById(clientId) ?: throw IllegalStateException("El cliente ya no existe")
        clienteDao.update(client.copy(deuda_total = clienteDao.calcularDeuda(clientId)))
    }
}
