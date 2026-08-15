package com.example.posapp.data.repository

import androidx.room.withTransaction
import com.example.posapp.data.AppDatabase
import com.example.posapp.data.entities.Cliente
import com.example.posapp.data.entities.DetalleVenta
import com.example.posapp.data.entities.PagoFiado
import com.example.posapp.data.entities.Producto
import com.example.posapp.data.entities.SyncStatus
import com.example.posapp.data.entities.StockMovement
import com.example.posapp.data.entities.Venta
import java.util.UUID

data class SaleLine(val producto: Producto, val cantidad: Int)

class SalesRepository(private val db: AppDatabase, private val businessId: String) {
    private val productoDao = db.productoDao()
    private val ventaDao = db.ventaDao()
    private val clienteDao = db.clienteDao()
    private val movementDao = db.stockMovementDao()

    init { require(businessId.isNotBlank()) { "No hay un negocio activo" } }

    suspend fun checkout(lines: List<SaleLine>, tipoPago: String, clienteId: Long?): Long = db.withTransaction {
        require(lines.isNotEmpty()) { "El carrito está vacío" }
        require(tipoPago in setOf("EFECTIVO", "YAPE", "FIADO")) { "Método de pago inválido" }
        require(tipoPago != "FIADO" || clienteId != null) { "Selecciona un cliente para la venta fiada" }

        val normalizedLines = lines.groupBy { it.producto.id }.map { (_, items) ->
            items.first().copy(cantidad = items.sumOf { it.cantidad })
        }
        require(normalizedLines.all { it.cantidad > 0 }) { "La cantidad debe ser mayor que cero" }

        val currentProducts = normalizedLines.associate { line ->
            val current = productoDao.getById(businessId, line.producto.id)
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
        require(currentProducts.values.all { it.business_id == businessId }) { "El carrito contiene productos de otro negocio" }
        if (clienteId != null) require(clienteDao.getById(businessId, clienteId) != null) { "El cliente pertenece a otro negocio" }
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
            val affected = productoDao.decreaseStockIfEnough(businessId, product.id, line.cantidad, now)
            if (affected == 0) throw IllegalStateException("Stock insuficiente para ${product.nombre}")
            ventaDao.insertDetalle(
                DetalleVenta(
                    ventaId = ventaId,
                    productoId = product.id,
                    product_sync_id_snapshot = product.sync_id,
                    product_name_snapshot = product.nombre,
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
            movementDao.insert(
                StockMovement(
                    productId = product.id,
                    saleId = ventaId,
                    type = "SALE",
                    quantity_delta = -line.cantidad,
                    notes = "Venta #$ventaId",
                    sync_id = UUID.randomUUID().toString(),
                    business_id = businessId,
                    created_at = now
                )
            )
        }

        if (tipoPago == "FIADO") refreshClientDebt(requireNotNull(clienteId))
        ventaId
    }

    suspend fun payDetails(clientId: Long, detailIds: Set<Long>) = db.withTransaction {
        require(detailIds.isNotEmpty()) { "Selecciona al menos un producto" }
        val pending = ventaDao.getDetallesPendientesCliente(businessId, clientId).associateBy { it.id }
        val selected = detailIds.map { id ->
            pending[id] ?: throw IllegalStateException("Uno de los productos ya fue pagado o no pertenece al cliente")
        }
        val now = System.currentTimeMillis()
        selected.forEach { detail ->
            val lineTotal = Math.multiplyExact(detail.precio_unitario_centavos, detail.cantidad.toLong())
            val remaining = (lineTotal - ventaDao.getTotalPagadoDetalle(businessId, detail.id)).coerceAtLeast(0)
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
            val venta = ventaDao.getById(businessId, ventaId) ?: return@forEach
            if (ventaDao.getTotalPagado(businessId, ventaId) >= venta.total_centavos) {
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

        val pending = ventaDao.getDetallesPendientesCliente(businessId, clientId).associateBy { it.id }
        val selected = detailIds.map { id ->
            pending[id] ?: throw IllegalStateException("Una linea ya fue pagada o no pertenece al cliente")
        }
        val remainingByDetail = selected.associateWith { detail ->
            val lineTotal = Math.multiplyExact(detail.precio_unitario_centavos, detail.cantidad.toLong())
            (lineTotal - ventaDao.getTotalPagadoDetalle(businessId, detail.id)).coerceAtLeast(0)
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
            val sale = ventaDao.getById(businessId, saleId) ?: return@forEach
            if (ventaDao.getTotalPagado(businessId, saleId) >= sale.total_centavos) {
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
        require(cliente.business_id == businessId && clienteDao.calcularDeuda(businessId, cliente.id) == 0L) {
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
        val venta = ventaDao.getById(businessId, ventaId) ?: return@withTransaction
        if (venta.estado == "ANULADO") return@withTransaction
        ventaDao.getDetallesForVenta(businessId, ventaId).forEach { detail ->
            val now = System.currentTimeMillis()
            val productId = detail.productoId
                ?: throw IllegalStateException("No se puede reponer un producto eliminado del historial")
            productoDao.increaseStock(businessId, productId, detail.cantidad, now)
            movementDao.insert(
                StockMovement(
                    productId = productId,
                    saleId = ventaId,
                    type = "SALE_CANCEL",
                    quantity_delta = detail.cantidad,
                    notes = "Reposicion por anulacion",
                    sync_id = UUID.randomUUID().toString(),
                    business_id = detail.business_id,
                    created_at = now
                )
            )
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
        val client = clienteDao.getById(businessId, clientId) ?: throw IllegalStateException("El cliente ya no existe")
        clienteDao.update(
            client.copy(
                deuda_total = 0.0,
                deuda_total_centavos = clienteDao.calcularDeuda(businessId, clientId),
                updated_at = System.currentTimeMillis(),
                sync_status = SyncStatus.PENDING
            )
        )
    }
}
