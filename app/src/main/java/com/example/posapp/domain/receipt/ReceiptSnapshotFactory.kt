package com.example.posapp.domain.receipt

import android.content.Context
import com.example.posapp.data.ActiveBusinessStore
import com.example.posapp.data.AppDatabase
import com.example.posapp.data.UserPreferencesRepository
import kotlinx.coroutines.flow.first

interface ReceiptSnapshotFactory {
    suspend fun fromSale(saleId: Long): ReceiptDocument
}

class RoomReceiptSnapshotFactory(context: Context) : ReceiptSnapshotFactory {
    private val appContext = context.applicationContext
    private val database = AppDatabase.getInstance(appContext)
    private val activeBusiness = ActiveBusinessStore(appContext)
    private val preferences = UserPreferencesRepository(appContext)

    override suspend fun fromSale(saleId: Long): ReceiptDocument {
        val businessId = activeBusiness.businessId().also {
            require(it.isNotBlank()) { "No hay un negocio activo" }
        }
        val sale = database.ventaDao().getById(businessId, saleId)
            ?: throw IllegalArgumentException("La venta ya no existe en este negocio")
        require(sale.business_id == businessId) { "La venta pertenece a otro negocio" }
        val syncId = requireNotNull(sale.sync_id) { "La venta no tiene identificador estable" }
        val details = database.ventaDao().getDetallesForVenta(businessId, saleId)
        require(details.isNotEmpty() && details.all { it.business_id == businessId && it.ventaId == sale.id }) {
            "La venta no tiene un detalle historico valido"
        }

        val lines = details.map { detail ->
            val lineTotal = Math.multiplyExact(detail.precio_unitario_centavos, detail.cantidad.toLong())
            ReceiptLine(
                nameSnapshot = detail.product_name_snapshot.ifBlank { "Producto" },
                quantity = detail.cantidad,
                unitPriceCents = detail.precio_unitario_centavos,
                lineTotalCents = lineTotal
            )
        }
        val subtotal = lines.sumOf(ReceiptLine::lineTotalCents)
        require(subtotal == sale.total_centavos) {
            "El total historico no coincide con la venta confirmada"
        }

        val profile = preferences.profileFlow.first()
        val settings = database.syncDao().businessSettings(businessId)
        val customer = sale.clienteId?.let { customerId ->
            database.clienteDao().getById(businessId, customerId)?.let {
                require(it.business_id == businessId) { "El cliente pertenece a otro negocio" }
                ReceiptCustomer(name = it.nombre, document = null, phone = it.telefono)
            }
        }
        val footer = settings?.receipt_message?.trim()?.take(240).orEmpty()
            .ifBlank { "Gracias por su compra." }

        return ReceiptDocument(
            businessId = businessId,
            saleSyncId = syncId,
            displayCode = ReceiptCode.from(sale.fecha_hora, syncId),
            issuedAt = sale.fecha_hora,
            business = ReceiptBusiness(
                name = profile?.businessName?.ifBlank { "SpaceSale" } ?: "SpaceSale",
                address = profile?.address?.takeIf(String::isNotBlank),
                phone = null,
                logoLocalPath = profile?.logoPath
            ),
            customer = customer,
            paymentMethod = sale.tipo_pago,
            lines = lines,
            subtotalCents = subtotal,
            discountCents = 0,
            totalCents = sale.total_centavos,
            amountInWords = sale.total_centavos.toPenWords(),
            internalQrValue = null,
            footerMessage = footer
        )
    }
}
