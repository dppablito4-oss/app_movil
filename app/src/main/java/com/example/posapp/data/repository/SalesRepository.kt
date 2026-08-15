package com.example.posapp.data.repository

import androidx.room.withTransaction
import com.example.posapp.data.AppDatabase
import com.example.posapp.data.entities.Cliente
import com.example.posapp.data.entities.DetalleVenta
import com.example.posapp.data.entities.PagoFiado
import com.example.posapp.data.entities.Producto
import com.example.posapp.data.entities.SyncStatus
import com.example.posapp.data.entities.Venta
import java.util.UUID

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
            require(current.precio_venta_centavos >= 0) { "El producto ${current.nombre} tiene un precio inválido" }
            current.id to current
        }

        val totalCents = normalizedLines.sumOf { line ->
            Math.multiplyExact(
                currentProducts.getValue(line.producto.id).precio_venta_centavos,
                line.cantidad.toLong()
            )
        }
        require(totalCents > 0) { "El total de la venta debe ser mayor que cero" }

        val now = System.currentTimeMillis()
        val businessId = currentProducts.values.firstOrNull()?.business_id.orEmpty()
        require(businessId.isNotBlank()) { "No hay un negocio activo para registrar la venta" }
        val ventaId = ventaDao.insertVenta(
            Venta(
                fecha_hora = now,
                total = 0.0,
                total_centavos = totalCents,
                tipo_pago = tipoPago,
                clienteId = clienteId,
                estado = if (tipoPago == "FIADO") "PENDIENTE" else "CERRADO",
                sync_id = UUID.randomUUID().toString(),
                business_id = businessId,
                created_at = now,
                updated_at = now,
                sync_status = SyncStatus.PENDING
            )
        )

        normalizedLines.forEach { line ->
            val product = currentProducts.getValue(line.producto.id)
            val affected = productoDao.decreaseStockIfEnough(product.id, line.cantidad, now)
            if (affected == 0) throw IllegalStateException("Stock insuficiente para ${product.nombre}")
            ventaDao.insertDetalle(
                DetalleVenta(
                    ventaId = ventaId,
                    productoId = product.id,
                    cantidad = line.cantidad,
                    precio_unitario_historico = 0.0,
                    precio_unitario_centavos = product.precio_venta_centavos,
                    costo_unitario_centavos = product.precio_costo_centavos,
                    sync_id = UUID.randomUUID().toString(),
                    business_id = businessId,
                    created_at = now,
                    sync_status = SyncStatus.PENDING
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
            val lineTotal = Math.multiplyExact(detail.precio_unitario_centavos, detail.cantidad.toLong())
            val remaining = (lineTotal - ventaDao.getTotalPagadoDetalle(detail.id)).coerceAtLeast(0)
            if (remaining > 0) {
                ventaDao.insertPago(
                    PagoFiado(
                        ventaId = detail.ventaId,
                        detalleId = detail.id,
                        monto = 0.0,
                        monto_centavos = remaining,
                        sync_id = UUID.randomUUID().toString(),
                        business_id = detail.business_id,
                        fecha_hora = now,
                        created_at = now,
                        sync_status = SyncStatus.PENDING
                    )
                )
            }
        }

        selected.map { it.ventaId }.distinct().forEach { ventaId ->
            val venta = ventaDao.getById(ventaId) ?: return@forEach
            if (ventaDao.getTotalPagado(ventaId) >= venta.total_centavos) {
                ventaDao.updateVenta(
                    venta.copy(
                        estado = "CERRADO",
                        fecha_pago = now,
                        updated_at = now,
                        sync_status = SyncStatus.PENDING
                    )
                )
            }
        }
        refreshClientDebt(clientId)
    }

    suspend fun payAmount(
        clientId: Long,
        detailIds: Set<Long>,
        amountCents: Long,
        method: String,
        note: String
    ) = db.withTransaction {
        require(detailIds.isNotEmpty()) { "Selecciona al menos un producto" }
        require(amountCents > 0) { "El abono debe ser mayor que cero" }
        require(method in setOf("EFECTIVO", "YAPE", "PLIN", "TARJETA", "TRANSFERENCIA")) {
            "Metodo de pago invalido"
        }

        val pending = ventaDao.getDetallesPendientesCliente(clientId).associateBy { it.id }
        val selected = detailIds.map { id ->
            pending[id] ?: throw IllegalStateException("Una linea ya fue pagada o no pertenece al cliente")
        }
        val remainingByDetail = selected.associateWith { detail ->
            val lineTotal = Math.multiplyExact(detail.precio_unitario_centavos, detail.cantidad.toLong())
            (lineTotal - ventaDao.getTotalPagadoDetalle(detail.id)).coerceAtLeast(0)
        }
        val totalRemaining = remainingByDetail.values.sum()
        require(amountCents <= totalRemaining) { "El abono supera el saldo seleccionado" }

        val now = System.currentTimeMillis()
        var amountToAllocate = amountCents
        selected.sortedBy { it.id }.forEach { detail ->
            if (amountToAllocate <= 0) return@forEach
            val allocated = minOf(remainingByDetail.getValue(detail), amountToAllocate)
            if (allocated > 0) {
                ventaDao.insertPago(
                    PagoFiado(
                        ventaId = detail.ventaId,
                        detalleId = detail.id,
                        monto = 0.0,
                        monto_centavos = allocated,
                        metodo_pago = method,
                        nota = note.trim(),
                        sync_id = UUID.randomUUID().toString(),
                        business_id = detail.business_id,
                        fecha_hora = now,
                        created_at = now,
                        sync_status = SyncStatus.PENDING
                    )
                )
                amountToAllocate -= allocated
            }
        }

        selected.map { it.ventaId }.distinct().forEach { saleId ->
            val sale = ventaDao.getById(saleId) ?: return@forEach
            if (ventaDao.getTotalPagado(saleId) >= sale.total_centavos) {
                ventaDao.updateVenta(
                    sale.copy(
                        estado = "CERRADO",
                        fecha_pago = now,
                        updated_at = now,
                        sync_status = SyncStatus.PENDING
                    )
                )
            }
        }
        refreshClientDebt(clientId)
    }

    suspend fun deleteClient(cliente: Cliente) = db.withTransaction {
        require(clienteDao.calcularDeuda(cliente.id) == 0L) {
            "No se puede eliminar un cliente con deuda pendiente"
        }
        clienteDao.update(
            cliente.copy(
                deleted_at = System.currentTimeMillis(),
                updated_at = System.currentTimeMillis(),
                sync_status = SyncStatus.PENDING
            )
        )
    }

    suspend fun cancelSale(ventaId: Long) = db.withTransaction {
        val venta = ventaDao.getById(ventaId) ?: return@withTransaction
        if (venta.estado == "ANULADO") return@withTransaction
        ventaDao.getDetallesForVenta(ventaId).forEach { detail ->
            productoDao.increaseStock(detail.productoId, detail.cantidad, System.currentTimeMillis())
        }
        ventaDao.updateVenta(
            venta.copy(
                estado = "ANULADO",
                updated_at = System.currentTimeMillis(),
                sync_status = SyncStatus.PENDING
            )
        )
        venta.clienteId?.let { refreshClientDebt(it) }
    }

    private suspend fun refreshClientDebt(clientId: Long) {
        val client = clienteDao.getById(clientId) ?: throw IllegalStateException("El cliente ya no existe")
        clienteDao.update(
            client.copy(
                deuda_total = 0.0,
                deuda_total_centavos = clienteDao.calcularDeuda(clientId),
                updated_at = System.currentTimeMillis(),
                sync_status = SyncStatus.PENDING
            )
        )
    }
}
