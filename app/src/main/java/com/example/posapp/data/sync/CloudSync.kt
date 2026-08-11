package com.example.posapp.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.posapp.auth.RemoteBusiness
import com.example.posapp.data.AppDatabase
import com.example.posapp.data.entities.Cliente
import com.example.posapp.data.entities.DetalleVenta
import com.example.posapp.data.entities.PagoFiado
import com.example.posapp.data.entities.Producto
import com.example.posapp.data.entities.Venta
import com.example.posapp.data.remote.SupabaseProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.roundToLong

/**
 * Copia los datos locales a Supabase sin bloquear ninguna pantalla.
 * Room sigue siendo la fuente de verdad: si no hay red, WorkManager reintentará.
 *
 * Esta primera versión es deliberadamente "local -> nube". Evita que un cambio
 * remoto inesperado sobrescriba una venta hecha sin conexión. La descarga y la
 * resolución de conflictos se añadirán cuando exista una identidad de dispositivo
 * y una pantalla explícita para tratar conflictos.
 */
class CloudSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        if (!SupabaseProvider.isConfigured) return Result.success()

        return runCatching {
            val client = SupabaseProvider.client
            client.auth.awaitInitialization()
            if (client.auth.currentUserOrNull() == null) return Result.success()

            val business = client.from("businesses")
                .select { limit(1) }
                .decodeList<RemoteBusiness>()
                .firstOrNull()
                ?: return Result.success()

            CloudSyncEngine(applicationContext, business.id).uploadLocalData()
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() }
        )
    }
}

object CloudSyncScheduler {
    private const val PERIODIC_WORK = "spacesale-cloud-sync-periodic"
    private const val IMMEDIATE_WORK = "spacesale-cloud-sync-now"

    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val manager = WorkManager.getInstance(context.applicationContext)
        manager.enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<CloudSyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
        )
        manager.enqueueUniqueWork(
            IMMEDIATE_WORK,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<CloudSyncWorker>()
                .setConstraints(constraints)
                .build()
        )
    }
}

private class CloudSyncEngine(context: Context, private val businessId: String) {
    private val database = AppDatabase.getInstance(context.applicationContext)
    private val products = database.productoDao()
    private val customers = database.clienteDao()
    private val sales = database.ventaDao()

    suspend fun uploadLocalData() {
        // El orden protege las llaves foráneas del esquema remoto.
        val localProducts = mutableListOf<Producto>()
        for (product in products.getAllForSync()) localProducts += product.ensureSyncId(products::update)
        val localCustomers = mutableListOf<Cliente>()
        for (customer in customers.getAllForSync()) localCustomers += customer.ensureSyncId(customers::update)
        val localSales = mutableListOf<Venta>()
        for (sale in sales.getAllVentas()) localSales += sale.ensureSyncId(sales::updateVenta)
        val localDetails = mutableListOf<DetalleVenta>()
        for (detail in sales.getAllDetalles()) localDetails += detail.ensureSyncId(sales::updateDetalle)
        val localPayments = mutableListOf<PagoFiado>()
        for (payment in sales.getAllPagos()) localPayments += payment.ensureSyncId(sales::updatePago)

        val productIds = localProducts.associate { it.id to requireNotNull(it.sync_id) }
        val customerIds = localCustomers.associate { it.id to requireNotNull(it.sync_id) }
        val saleIds = localSales.associate { it.id to requireNotNull(it.sync_id) }

        if (localProducts.isNotEmpty()) SupabaseProvider.client.from("products").upsert(
            localProducts.map { product ->
                SyncProduct(
                    id = requireNotNull(product.sync_id), businessId = businessId,
                    name = product.nombre, costCents = product.precio_costo.toCents(),
                    saleCents = product.precio_venta.toCents(), stock = product.stock,
                    imagePath = product.ruta_imagen, normalizedSearch = product.busqueda_normalizada
                )
            }
        )
        if (localCustomers.isNotEmpty()) SupabaseProvider.client.from("customers").upsert(
            localCustomers.map { customer ->
                SyncCustomer(requireNotNull(customer.sync_id), businessId, customer.nombre, customer.telefono, customer.nota)
            }
        )
        val unsyncedSales = localSales.filterNot { it.nube_sincronizada }
        if (unsyncedSales.isNotEmpty()) {
            SupabaseProvider.client.from("sales").insert(
                unsyncedSales.map { sale ->
                SyncSale(
                    id = requireNotNull(sale.sync_id), businessId = businessId,
                    customerId = sale.clienteId?.let(customerIds::get), totalCents = sale.total.toCents(),
                    paymentMethod = sale.tipo_pago, status = sale.estado,
                    soldAt = sale.fecha_hora.toTimestamp(), evidencePath = sale.ruta_evidencia,
                    paidAt = sale.fecha_pago?.toTimestamp()
                )
                }
            )
            sales.markVentasSynced(unsyncedSales.map { it.id })
        }
        val unsyncedDetails = localDetails.filterNot { it.nube_sincronizada }
        if (unsyncedDetails.isNotEmpty()) {
            SupabaseProvider.client.from("sale_items").insert(
                unsyncedDetails.mapNotNull { detail ->
                val saleId = saleIds[detail.ventaId] ?: return@mapNotNull null
                val productId = productIds[detail.productoId] ?: return@mapNotNull null
                val productName = localProducts.firstOrNull { it.id == detail.productoId }?.nombre ?: "Producto"
                SyncSaleItem(requireNotNull(detail.sync_id), businessId, saleId, productId, productName, detail.cantidad, detail.precio_unitario_historico.toCents())
                }
            )
            sales.markDetallesSynced(unsyncedDetails.map { it.id })
        }
        val unsyncedPayments = localPayments.filterNot { it.nube_sincronizada }
        if (unsyncedPayments.isNotEmpty()) {
            SupabaseProvider.client.from("credit_payments").insert(
                unsyncedPayments.mapNotNull { payment ->
                val sale = localSales.firstOrNull { it.id == payment.ventaId } ?: return@mapNotNull null
                val customerId = sale.clienteId?.let(customerIds::get) ?: return@mapNotNull null
                val saleId = saleIds[payment.ventaId] ?: return@mapNotNull null
                SyncCreditPayment(requireNotNull(payment.sync_id), businessId, customerId, saleId, payment.monto.toCents(), "EFECTIVO", payment.fecha_hora.toTimestamp())
                }
            )
            sales.markPagosSynced(unsyncedPayments.map { it.id })
        }
    }

    private suspend fun Producto.ensureSyncId(save: suspend (Producto) -> Unit): Producto {
        if (sync_id != null) return this
        val updated = copy(sync_id = UUID.randomUUID().toString())
        save(updated)
        return updated
    }
    private suspend fun Cliente.ensureSyncId(save: suspend (Cliente) -> Unit): Cliente {
        if (sync_id != null) return this
        val updated = copy(sync_id = UUID.randomUUID().toString())
        save(updated)
        return updated
    }
    private suspend fun Venta.ensureSyncId(save: suspend (Venta) -> Unit): Venta {
        if (sync_id != null) return this
        val updated = copy(sync_id = UUID.randomUUID().toString())
        save(updated)
        return updated
    }
    private suspend fun DetalleVenta.ensureSyncId(save: suspend (DetalleVenta) -> Unit): DetalleVenta {
        if (sync_id != null) return this
        val updated = copy(sync_id = UUID.randomUUID().toString())
        save(updated)
        return updated
    }
    private suspend fun PagoFiado.ensureSyncId(save: suspend (PagoFiado) -> Unit): PagoFiado {
        if (sync_id != null) return this
        val updated = copy(sync_id = UUID.randomUUID().toString())
        save(updated)
        return updated
    }
}

private fun Double.toCents(): Long = (this * 100).roundToLong().coerceAtLeast(0)
private fun Long.toTimestamp(): String = Instant.ofEpochMilli(this).toString()

@Serializable private data class SyncProduct(
    val id: String, @SerialName("business_id") val businessId: String, val name: String,
    @SerialName("cost_cents") val costCents: Long, @SerialName("sale_cents") val saleCents: Long,
    val stock: Int, @SerialName("min_stock") val minStock: Int = 5,
    @SerialName("image_path") val imagePath: String?, @SerialName("normalized_search") val normalizedSearch: String
)
@Serializable private data class SyncCustomer(
    val id: String, @SerialName("business_id") val businessId: String, val name: String,
    val phone: String?, val notes: String
)
@Serializable private data class SyncSale(
    val id: String, @SerialName("business_id") val businessId: String,
    @SerialName("customer_id") val customerId: String?, @SerialName("total_cents") val totalCents: Long,
    @SerialName("payment_method") val paymentMethod: String, val status: String,
    @SerialName("sold_at") val soldAt: String, @SerialName("evidence_path") val evidencePath: String?,
    @SerialName("paid_at") val paidAt: String?
)
@Serializable private data class SyncSaleItem(
    val id: String, @SerialName("business_id") val businessId: String, @SerialName("sale_id") val saleId: String,
    @SerialName("product_id") val productId: String, @SerialName("product_name_snapshot") val productNameSnapshot: String,
    val quantity: Int, @SerialName("unit_price_cents") val unitPriceCents: Long
)
@Serializable private data class SyncCreditPayment(
    val id: String, @SerialName("business_id") val businessId: String, @SerialName("customer_id") val customerId: String,
    @SerialName("sale_id") val saleId: String, @SerialName("amount_cents") val amountCents: Long,
    @SerialName("payment_method") val paymentMethod: String, @SerialName("paid_at") val paidAt: String
)
