package com.example.posapp.data.sync

import android.content.Context
import androidx.room.withTransaction
import androidx.work.BackoffPolicy
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
import com.example.posapp.data.ActiveBusinessStore
import com.example.posapp.data.AppDatabase
import com.example.posapp.data.entities.Cliente
import com.example.posapp.data.entities.DetalleVenta
import com.example.posapp.data.entities.PagoFiado
import com.example.posapp.data.entities.Producto
import com.example.posapp.data.entities.SyncMetadata
import com.example.posapp.data.entities.SyncQueueItem
import com.example.posapp.data.entities.SyncStatus
import com.example.posapp.data.entities.StockMovement
import com.example.posapp.data.entities.Venta
import com.example.posapp.data.remote.SupabaseProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/** Estado ligero para que la UI informe la sincronizacion sin consultar la nube. */
data class CloudSyncState(
    val phase: CloudSyncPhase = CloudSyncPhase.IDLE,
    val pendingChanges: Int = 0,
    val message: String? = null,
    val lastSuccessAt: Long? = null
)

enum class CloudSyncPhase { IDLE, SYNCING, SYNCED, PENDING, ERROR, DISABLED }

object CloudSyncRuntime {
    private val mutableState = MutableStateFlow(CloudSyncState())
    val state: StateFlow<CloudSyncState> = mutableState.asStateFlow()

    internal fun update(value: CloudSyncState) {
        mutableState.value = value
    }
}

/**
 * Sincronizacion bidireccional offline-first.
 *
 * Room sigue siendo la fuente inmediata de la interfaz. Antes de tocar la red,
 * cada cambio pendiente se registra en sync_queue; por eso sobrevive al cierre
 * del proceso. Las escrituras financieras usan RPC idempotentes en Supabase.
 */
class CloudSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        if (!SupabaseProvider.isConfigured) {
            CloudSyncRuntime.update(
                CloudSyncState(CloudSyncPhase.DISABLED, message = "Supabase no esta configurado")
            )
            return Result.success()
        }

        CloudSyncRuntime.update(CloudSyncState(CloudSyncPhase.SYNCING))
        return runCatching {
            val client = SupabaseProvider.client
            client.auth.awaitInitialization()
            val user = client.auth.currentUserOrNull() ?: return Result.success()
            val activeBusinessStore = ActiveBusinessStore(applicationContext)
            val preferredBusinessId = activeBusinessStore.businessId()
            val accessibleBusinesses = client.from("businesses")
                .select()
                .decodeList<RemoteBusiness>()
            val business = accessibleBusinesses.firstOrNull { it.id == preferredBusinessId }
                ?: accessibleBusinesses.firstOrNull()
                ?: return Result.success()

            if (preferredBusinessId != business.id) activeBusinessStore.set(user.id, business.id)
            CloudSyncEngine(applicationContext, business.id).synchronize()
        }.fold(
            onSuccess = {
                val now = System.currentTimeMillis()
                CloudSyncRuntime.update(CloudSyncState(CloudSyncPhase.SYNCED, lastSuccessAt = now))
                Result.success()
            },
            onFailure = { error ->
                val pending = ActiveBusinessStore(applicationContext).businessId().takeIf(String::isNotBlank)
                    ?.let { AppDatabase.getInstance(applicationContext).syncDao().pendingCount(it) }
                    ?: 0
                CloudSyncRuntime.update(
                    CloudSyncState(
                        phase = CloudSyncPhase.ERROR,
                        pendingChanges = pending,
                        message = error.safeSyncMessage()
                    )
                )
                Result.retry()
            }
        )
    }
}

object CloudSyncScheduler {
    private const val WORK_TAG = "spacesale-cloud-sync"
    private const val PERIODIC_WORK = "spacesale-cloud-sync-periodic"
    private const val IMMEDIATE_WORK = "spacesale-cloud-sync-now"

    fun schedule(context: Context) {
        val appContext = context.applicationContext
        val businessSuffix = ActiveBusinessStore(appContext).businessId().ifBlank { "session" }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val manager = WorkManager.getInstance(appContext)
        manager.enqueueUniquePeriodicWork(
            "$PERIODIC_WORK-$businessSuffix",
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<CloudSyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .addTag(WORK_TAG)
                .build()
        )
        manager.enqueueUniqueWork(
            "$IMMEDIATE_WORK-$businessSuffix",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<CloudSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag(WORK_TAG)
                .build()
        )
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelAllWorkByTag(WORK_TAG)
    }
}

private class CloudSyncEngine(context: Context, private val businessId: String) {
    private val appContext = context.applicationContext
    private val database = AppDatabase.getInstance(appContext)
    private val products = database.productoDao()
    private val customers = database.clienteDao()
    private val sales = database.ventaDao()
    private val queue = database.syncDao()
    private val movements = database.stockMovementDao()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun synchronize() {
        enqueuePendingChanges()
        processQueue()
        pullRemoteChanges()
        refreshLocalDebts()

        val pending = queue.pendingCount(businessId)
        CloudSyncRuntime.update(
            CloudSyncState(
                phase = if (pending == 0) CloudSyncPhase.SYNCED else CloudSyncPhase.PENDING,
                pendingChanges = pending,
                lastSuccessAt = System.currentTimeMillis()
            )
        )
        if (pending > 0) throw PendingSyncOperationsException()
    }

    private suspend fun enqueuePendingChanges() {
        val now = System.currentTimeMillis()
        val localProducts = products.getAllForSync()
            .filter { it.business_id == businessId }
            .map { it.ensureSyncId(products::update) }
        val localCustomers = customers.getAllForSync()
            .filter { it.business_id == businessId }
            .map { it.ensureSyncId(customers::update) }
        val localSales = sales.getAllVentas()
            .filter { it.business_id == businessId }
            .map { it.ensureSyncId(sales::updateVenta) }
        val localDetails = sales.getAllDetalles()
            .filter { it.business_id == businessId }
            .map { it.ensureSyncId(sales::updateDetalle) }
        val localPayments = sales.getAllPagos()
            .filter { it.business_id == businessId }
            .map { it.ensureSyncId(sales::updatePago) }
        val localMovements = movements.getAllForSync()
            .filter { it.business_id == businessId }
        val localSettings = queue.businessSettings(businessId)

        val productIds = localProducts.associate { it.id to requireNotNull(it.sync_id) }
        val customerIds = localCustomers.associate { it.id to requireNotNull(it.sync_id) }
        val saleIds = localSales.associate { it.id to requireNotNull(it.sync_id) }
        val productsById = localProducts.associateBy(Producto::id)
        val detailsBySale = localDetails.groupBy(DetalleVenta::ventaId)

        localProducts.filter { it.sync_status != SyncStatus.SYNCED }.forEach { product ->
            val syncId = requireNotNull(product.sync_id)
            val localImage = product.ruta_imagen?.let(::File)?.takeIf(File::isFile)
            val expectedStoragePath = if (localImage != null) {
                product.storage_path ?: "$businessId/products/$syncId/main.jpg"
            } else {
                product.storage_path
            }
            if (product.deleted_at == null && localImage != null && product.image_sync_status != SyncStatus.SYNCED) {
                val imagePayload = ImageUploadOperation(
                    productSyncId = syncId,
                    localPath = localImage.absolutePath,
                    storagePath = requireNotNull(expectedStoragePath)
                )
                queue.enqueue(imagePayload.toQueueItem("IMAGE_UPLOAD", syncId, product.updated_at, now))
            }
            val payload = ProductOperation(
                localVersion = product.updated_at,
                data = SyncProduct(
                    id = syncId,
                    businessId = businessId,
                    name = product.nombre,
                    costCents = product.precio_costo_centavos,
                    saleCents = product.precio_venta_centavos,
                    stock = product.stock,
                    barcode = product.codigo_barras,
                    minStock = product.stock_minimo,
                    imagePath = expectedStoragePath,
                    normalizedSearch = product.busqueda_normalizada,
                    deletedAt = product.deleted_at?.toTimestamp()
                )
            )
            queue.enqueue(payload.toQueueItem("PRODUCT", syncId, product.updated_at, now))
            if (product.deleted_at != null && !product.storage_path.isNullOrBlank()) {
                val deletePayload = ImageDeleteOperation(syncId, product.storage_path)
                queue.enqueue(deletePayload.toQueueItem("IMAGE_DELETE", syncId, product.updated_at, now))
            }
        }

        localCustomers.filter { it.sync_status != SyncStatus.SYNCED }.forEach { customer ->
            val syncId = requireNotNull(customer.sync_id)
            val payload = CustomerOperation(
                localVersion = customer.updated_at,
                data = SyncCustomer(
                    id = syncId,
                    businessId = businessId,
                    name = customer.nombre,
                    phone = customer.telefono,
                    notes = customer.nota,
                    deletedAt = customer.deleted_at?.toTimestamp()
                )
            )
            queue.enqueue(payload.toQueueItem("CUSTOMER", syncId, customer.updated_at, now))
        }

        if (localSettings != null && localSettings.sync_status != SyncStatus.SYNCED) {
            val data = SyncBusinessSettings(
                businessId = businessId,
                currency = localSettings.currency,
                dailyGoalCents = localSettings.daily_goal_cents,
                lowStockEnabled = localSettings.low_stock_enabled,
                receiptMessage = localSettings.receipt_message
            )
            queue.enqueue(SettingsOperation(localSettings.updated_at, data).toQueueItem("SETTINGS", businessId, localSettings.updated_at, now))
        }

        localSales.filter { it.sync_status != SyncStatus.SYNCED }.forEach { sale ->
            val syncId = requireNotNull(sale.sync_id)
            val salePayload = sale.toSyncSale(customerIds)
            if (!sale.nube_sincronizada || sale.remote_updated_at == null) {
                val itemPayloads = detailsBySale[sale.id].orEmpty().mapNotNull { detail ->
                    detail.toSyncSaleItem(saleIds, productIds, productsById)
                }
                val payload = SaleBundleOperation(sale.updated_at, salePayload, itemPayloads)
                queue.enqueue(payload.toQueueItem("SALE_BUNDLE", syncId, sale.updated_at, now))
            } else {
                val payload = SaleTransitionOperation(sale.updated_at, salePayload)
                queue.enqueue(payload.toQueueItem("SALE_TRANSITION", syncId, sale.updated_at, now))
            }
        }

        localDetails.filter { detail ->
            detail.sync_status != SyncStatus.SYNCED &&
                localSales.firstOrNull { it.id == detail.ventaId }?.sync_status == SyncStatus.SYNCED
        }.forEach { detail ->
            val syncId = requireNotNull(detail.sync_id)
            val data = detail.toSyncSaleItem(saleIds, productIds, productsById) ?: return@forEach
            queue.enqueue(SaleItemOperation(data).toQueueItem("SALE_ITEM", syncId, detail.created_at, now))
        }

        localPayments.filter { it.sync_status != SyncStatus.SYNCED }.forEach { payment ->
            val syncId = requireNotNull(payment.sync_id)
            val sale = localSales.firstOrNull { it.id == payment.ventaId } ?: return@forEach
            val customerId = sale.clienteId?.let(customerIds::get) ?: return@forEach
            val saleId = saleIds[payment.ventaId] ?: return@forEach
            val data = SyncCreditPayment(
                id = syncId,
                businessId = businessId,
                customerId = customerId,
                saleId = saleId,
                amountCents = payment.monto_centavos,
                paymentMethod = payment.metodo_pago,
                notes = payment.nota,
                paidAt = payment.fecha_hora.toTimestamp()
            )
            queue.enqueue(PaymentOperation(data).toQueueItem("PAYMENT", syncId, payment.created_at, now))
        }

        localMovements.filter { it.sync_status != SyncStatus.SYNCED }.forEach { movement ->
            val productId = productIds[movement.productId] ?: return@forEach
            val saleId = movement.saleId?.let(saleIds::get)
            val data = SyncStockMovement(
                id = movement.sync_id,
                businessId = businessId,
                productId = productId,
                saleId = saleId,
                type = movement.type,
                quantityDelta = movement.quantity_delta,
                notes = movement.notes,
                createdAt = movement.created_at.toTimestamp()
            )
            queue.enqueue(
                StockMovementOperation(data).toQueueItem(
                    "STOCK_MOVEMENT",
                    movement.sync_id,
                    movement.created_at,
                    now
                )
            )
        }
    }

    private suspend fun processQueue() {
        var hadFailure = false
        while (true) {
            val operations = queue.pendingOperations(businessId, System.currentTimeMillis(), limit = 100)
            if (operations.isEmpty()) break
            operations.forEach { operation ->
                runCatching { push(operation) }
                    .onSuccess { queue.confirm(operation.operation_id) }
                    .onFailure { error ->
                        hadFailure = true
                        val exponent = min(operation.attempt_count, 10)
                        val delayMs = min(6L * 60L * 60L * 1000L, 30_000L * (1L shl exponent))
                        queue.markFailed(
                            operationId = operation.operation_id,
                            nextAttemptAt = System.currentTimeMillis() + delayMs,
                            message = error.safeSyncMessage()
                        )
                    }
            }
        }
        if (hadFailure) throw PendingSyncOperationsException()
    }

    private suspend fun push(operation: SyncQueueItem) {
        check(operation.business_id == businessId) { "La operacion pertenece a otro negocio" }
        val now = System.currentTimeMillis()
        when (operation.entity_type) {
            "IMAGE_UPLOAD" -> {
                val payload = json.decodeFromString(ImageUploadOperation.serializer(), operation.payload_json)
                val image = File(payload.localPath)
                check(image.isFile) { "La foto local pendiente ya no existe" }
                runCatching {
                    SupabaseProvider.client.storage.from("product-images")
                        .upload(payload.storagePath, image.readBytes()) { upsert = true }
                }.onFailure {
                    products.markImageUploadFailed(payload.productSyncId)
                }.getOrThrow()
                products.markImageUploaded(payload.productSyncId, payload.storagePath)
            }
            "PRODUCT" -> {
                val payload = json.decodeFromString(ProductOperation.serializer(), operation.payload_json)
                check(!queue.hasPendingOperation(businessId, "IMAGE_UPLOAD", payload.data.id)) {
                    "La foto del producto sigue pendiente"
                }
                SupabaseProvider.client.from("products").upsert(payload.data)
                products.markSyncedBySyncId(payload.data.id, payload.localVersion, now)
            }
            "IMAGE_DELETE" -> {
                val payload = json.decodeFromString(ImageDeleteOperation.serializer(), operation.payload_json)
                SupabaseProvider.client.storage.from("product-images").delete(payload.storagePath)
            }
            "CUSTOMER" -> {
                val payload = json.decodeFromString(CustomerOperation.serializer(), operation.payload_json)
                SupabaseProvider.client.from("customers").upsert(payload.data)
                customers.markSyncedBySyncId(payload.data.id, payload.localVersion, now)
            }
            "SETTINGS" -> {
                val payload = json.decodeFromString(SettingsOperation.serializer(), operation.payload_json)
                SupabaseProvider.client.postgrest.rpc(
                    "update_business_settings",
                    json.encodeToJsonElement(
                        BusinessSettingsRpc.serializer(),
                        BusinessSettingsRpc(
                            targetBusinessId = payload.data.businessId,
                            targetCurrency = payload.data.currency,
                            targetDailyGoalCents = payload.data.dailyGoalCents,
                            targetLowStockEnabled = payload.data.lowStockEnabled,
                            targetReceiptMessage = payload.data.receiptMessage
                        )
                    ).jsonObject
                )
                queue.markBusinessSettingsSynced(businessId, now)
            }
            "SALE_BUNDLE" -> {
                val payload = json.decodeFromString(SaleBundleOperation.serializer(), operation.payload_json)
                SupabaseProvider.client.postgrest.rpc(
                    "insert_sale_bundle_if_absent",
                    json.encodeToJsonElement(
                        SaleBundleRpc.serializer(),
                        SaleBundleRpc(payload = SyncSaleBundle(payload.sale, payload.items))
                    ).jsonObject
                )
                sales.markVentaSyncedBySyncId(payload.sale.id, payload.localVersion, now)
                if (payload.items.isNotEmpty()) {
                    sales.markDetallesSyncedBySyncIds(payload.items.map(SyncSaleItem::id), now)
                }
            }
            "SALE_TRANSITION" -> {
                val payload = json.decodeFromString(SaleTransitionOperation.serializer(), operation.payload_json)
                SupabaseProvider.client.postgrest.rpc(
                    "transition_sale_status",
                    json.encodeToJsonElement(
                        SaleTransitionRpc.serializer(),
                        SaleTransitionRpc(
                            targetSaleId = payload.sale.id,
                            targetBusinessId = payload.sale.businessId,
                            newStatus = payload.sale.status,
                            paidAt = payload.sale.paidAt,
                            cancellationReason = if (payload.sale.status == "ANULADO") "Anulada desde SpaceSale" else null
                        )
                    ).jsonObject
                )
                sales.markVentaSyncedBySyncId(payload.sale.id, payload.localVersion, now)
            }
            "SALE_ITEM" -> {
                val payload = json.decodeFromString(SaleItemOperation.serializer(), operation.payload_json)
                SupabaseProvider.client.postgrest.rpc(
                    "insert_sale_item_if_absent",
                    json.encodeToJsonElement(SaleItemRpc.serializer(), SaleItemRpc(payload.data)).jsonObject
                )
                sales.markDetallesSyncedBySyncIds(listOf(payload.data.id), now)
            }
            "PAYMENT" -> {
                val payload = json.decodeFromString(PaymentOperation.serializer(), operation.payload_json)
                SupabaseProvider.client.postgrest.rpc(
                    "insert_credit_payment_if_absent",
                    json.encodeToJsonElement(CreditPaymentRpc.serializer(), CreditPaymentRpc(payload.data)).jsonObject
                )
                sales.markPagoSyncedBySyncId(payload.data.id, now)
            }
            "STOCK_MOVEMENT" -> {
                val payload = json.decodeFromString(StockMovementOperation.serializer(), operation.payload_json)
                SupabaseProvider.client.postgrest.rpc(
                    "insert_stock_movement_if_absent",
                    json.encodeToJsonElement(StockMovementRpc.serializer(), StockMovementRpc(payload.data)).jsonObject
                )
                movements.markSynced(payload.data.id, now)
            }
            else -> error("Tipo de operacion de sincronizacion desconocido")
        }
    }

    private suspend fun pullRemoteChanges() {
        pullProducts()
        pullBusinessSettings()
        hydrateRemoteProductImages()
        pullCustomers()
        pullSales()
        pullSaleItems()
        pullCreditPayments()
        pullStockMovements()
    }

    private suspend fun pullProducts() {
        val cursor = queue.metadata(businessId, "products")?.last_pulled_at ?: 0L
        val remoteRows = SupabaseProvider.client.from("products")
            .select {
                filter {
                    eq("business_id", businessId)
                    gt("updated_at", cursor.toTimestamp())
                }
            }
            .decodeList<RemoteProduct>()
        if (remoteRows.isEmpty()) return

        database.withTransaction {
            remoteRows.forEach { remote ->
                val existing = products.getBySyncId(remote.id)
                val createdAt = remote.createdAt.toEpochMillisOrZero()
                val updatedAt = remote.updatedAt.toEpochMillisOrZero()
                if (existing != null && existing.sync_status != SyncStatus.SYNCED && existing.updated_at > updatedAt) {
                    return@forEach
                }
                val keepCachedImage = existing?.ruta_imagen?.takeIf {
                    existing.storage_path == remote.imagePath &&
                        (existing.remote_updated_at ?: 0L) >= updatedAt &&
                        File(it).isFile
                }
                val mapped = Producto(
                    id = existing?.id ?: 0,
                    nombre = remote.name,
                    precio_costo = 0.0,
                    precio_venta = 0.0,
                    stock = remote.stock,
                    ruta_imagen = keepCachedImage,
                    storage_path = remote.imagePath,
                    image_sync_status = SyncStatus.SYNCED,
                    busqueda_normalizada = remote.normalizedSearch,
                    sync_id = remote.id,
                    precio_costo_centavos = remote.costCents,
                    precio_venta_centavos = remote.saleCents,
                    codigo_barras = remote.barcode,
                    stock_minimo = remote.minStock,
                    business_id = businessId,
                    created_at = createdAt,
                    updated_at = updatedAt,
                    deleted_at = remote.deletedAt?.toEpochMillisOrZero(),
                    remote_updated_at = updatedAt,
                    sync_status = SyncStatus.SYNCED
                )
                if (existing == null) products.insert(mapped) else products.update(mapped)
            }
            val newest = remoteRows.maxOf { it.updatedAt.toEpochMillisOrZero() }
            queue.upsertMetadata(SyncMetadata(businessId, "products", newest, System.currentTimeMillis()))
        }
    }

    private suspend fun pullBusinessSettings() {
        val remote = SupabaseProvider.client.from("businesses")
            .select { filter { eq("id", businessId) }; limit(1) }
            .decodeList<RemoteBusinessSettings>()
            .firstOrNull() ?: return
        val remoteUpdatedAt = remote.updatedAt.toEpochMillisOrZero()
        val local = queue.businessSettings(businessId)
        if (local != null && local.sync_status != SyncStatus.SYNCED && local.updated_at > remoteUpdatedAt) return
        queue.upsertBusinessSettings(
            com.example.posapp.data.entities.BusinessSettings(
                business_id = businessId,
                currency = remote.currency,
                daily_goal_cents = remote.dailyGoalCents,
                low_stock_enabled = remote.lowStockEnabled,
                receipt_message = remote.receiptMessage,
                updated_at = remoteUpdatedAt,
                sync_status = SyncStatus.SYNCED
            )
        )
    }

    private suspend fun hydrateRemoteProductImages() {
        products.getAllForSync()
            .filter { product ->
                product.business_id == businessId &&
                    product.deleted_at == null &&
                    !product.storage_path.isNullOrBlank() &&
                    (product.ruta_imagen.isNullOrBlank() || !File(product.ruta_imagen).isFile)
            }
            .forEach { product ->
                val syncId = product.sync_id ?: return@forEach
                val storagePath = product.storage_path ?: return@forEach
                val localPath = runCatching {
                    val bytes = SupabaseProvider.client.storage.from("product-images")
                        .downloadAuthenticated(storagePath)
                    com.example.posapp.utils.ImageUtils.saveRemoteImage(
                        appContext,
                        syncId,
                        bytes
                    )
                }.getOrNull()
                if (localPath != null) products.setCachedImage(syncId, localPath)
            }
    }

    private suspend fun pullCustomers() {
        val cursor = queue.metadata(businessId, "customers")?.last_pulled_at ?: 0L
        val remoteRows = SupabaseProvider.client.from("customers")
            .select {
                filter {
                    eq("business_id", businessId)
                    gt("updated_at", cursor.toTimestamp())
                }
            }
            .decodeList<RemoteCustomer>()
        if (remoteRows.isEmpty()) return

        database.withTransaction {
            remoteRows.forEach { remote ->
                val existing = customers.getBySyncId(remote.id)
                val createdAt = remote.createdAt.toEpochMillisOrZero()
                val updatedAt = remote.updatedAt.toEpochMillisOrZero()
                if (existing != null && existing.sync_status != SyncStatus.SYNCED && existing.updated_at > updatedAt) {
                    return@forEach
                }
                val mapped = Cliente(
                    id = existing?.id ?: 0,
                    nombre = remote.name,
                    telefono = remote.phone,
                    deuda_total = 0.0,
                    nota = remote.notes,
                    sync_id = remote.id,
                    deuda_total_centavos = existing?.deuda_total_centavos ?: 0,
                    business_id = businessId,
                    created_at = createdAt,
                    updated_at = updatedAt,
                    deleted_at = remote.deletedAt?.toEpochMillisOrZero(),
                    remote_updated_at = updatedAt,
                    sync_status = SyncStatus.SYNCED
                )
                if (existing == null) customers.insert(mapped) else customers.update(mapped)
            }
            val newest = remoteRows.maxOf { it.updatedAt.toEpochMillisOrZero() }
            queue.upsertMetadata(SyncMetadata(businessId, "customers", newest, System.currentTimeMillis()))
        }
    }

    private suspend fun pullSales() {
        val cursor = queue.metadata(businessId, "sales")?.last_pulled_at ?: 0L
        val remoteRows = SupabaseProvider.client.from("sales")
            .select {
                filter {
                    eq("business_id", businessId)
                    gt("updated_at", cursor.toTimestamp())
                }
            }
            .decodeList<RemoteSale>()
        if (remoteRows.isEmpty()) return

        database.withTransaction {
            remoteRows.forEach { remote ->
                val existing = sales.getBySyncId(remote.id)
                val createdAt = remote.createdAt.toEpochMillisOrZero()
                val updatedAt = remote.updatedAt.toEpochMillisOrZero()
                if (existing != null && existing.sync_status != SyncStatus.SYNCED && existing.updated_at > updatedAt) {
                    return@forEach
                }
                val localCustomerId = remote.customerId?.let { customers.getBySyncId(it)?.id }
                val mapped = Venta(
                    id = existing?.id ?: 0,
                    fecha_hora = remote.soldAt.toEpochMillisOrZero(),
                    total = 0.0,
                    total_centavos = remote.totalCents,
                    tipo_pago = remote.paymentMethod,
                    clienteId = localCustomerId,
                    estado = remote.status,
                    ruta_evidencia = remote.evidencePath,
                    fecha_pago = remote.paidAt?.toEpochMillisOrZero(),
                    sync_id = remote.id,
                    nube_sincronizada = true,
                    business_id = businessId,
                    created_at = createdAt,
                    updated_at = updatedAt,
                    remote_updated_at = updatedAt,
                    sync_status = SyncStatus.SYNCED
                )
                if (existing == null) sales.insertVenta(mapped) else sales.updateVenta(mapped)
            }
            val newest = remoteRows.maxOf { it.updatedAt.toEpochMillisOrZero() }
            queue.upsertMetadata(SyncMetadata(businessId, "sales", newest, System.currentTimeMillis()))
        }
    }

    private suspend fun pullSaleItems() {
        val cursor = queue.metadata(businessId, "sale_items")?.last_pulled_at ?: 0L
        val remoteRows = SupabaseProvider.client.from("sale_items")
            .select {
                filter {
                    eq("business_id", businessId)
                    gt("created_at", cursor.toTimestamp())
                }
            }
            .decodeList<RemoteSaleItem>()
        if (remoteRows.isEmpty()) return

        database.withTransaction {
            remoteRows.forEach { remote ->
                val localSaleId = sales.getBySyncId(remote.saleId)?.id ?: return@forEach
                val localProductId = remote.productId?.let { products.getBySyncId(it)?.id } ?: return@forEach
                val existing = sales.getDetailBySyncId(remote.id)
                val createdAt = remote.createdAt.toEpochMillisOrZero()
                val mapped = DetalleVenta(
                    id = existing?.id ?: 0,
                    ventaId = localSaleId,
                    productoId = localProductId,
                    cantidad = remote.quantity,
                    precio_unitario_historico = 0.0,
                    sync_id = remote.id,
                    nube_sincronizada = true,
                    precio_unitario_centavos = remote.unitPriceCents,
                    costo_unitario_centavos = remote.unitCostCents,
                    business_id = businessId,
                    created_at = createdAt,
                    remote_updated_at = createdAt,
                    sync_status = SyncStatus.SYNCED
                )
                if (existing == null) sales.insertDetalle(mapped) else sales.updateDetalle(mapped)
            }
            val newest = remoteRows.maxOf { it.createdAt.toEpochMillisOrZero() }
            queue.upsertMetadata(SyncMetadata(businessId, "sale_items", newest, System.currentTimeMillis()))
        }
    }

    private suspend fun pullCreditPayments() {
        val cursor = queue.metadata(businessId, "credit_payments")?.last_pulled_at ?: 0L
        val remoteRows = SupabaseProvider.client.from("credit_payments")
            .select {
                filter {
                    eq("business_id", businessId)
                    gt("created_at", cursor.toTimestamp())
                }
            }
            .decodeList<RemoteCreditPayment>()
        if (remoteRows.isEmpty()) return

        database.withTransaction {
            remoteRows.forEach { remote ->
                val localSaleId = sales.getBySyncId(remote.saleId)?.id ?: return@forEach
                val existing = sales.getPaymentBySyncId(remote.id)
                val createdAt = remote.createdAt.toEpochMillisOrZero()
                val mapped = PagoFiado(
                    id = existing?.id ?: 0,
                    ventaId = localSaleId,
                    detalleId = existing?.detalleId,
                    monto = 0.0,
                    fecha_hora = remote.paidAt.toEpochMillisOrZero(),
                    sync_id = remote.id,
                    nube_sincronizada = true,
                    monto_centavos = remote.amountCents,
                    metodo_pago = remote.paymentMethod,
                    nota = remote.notes,
                    business_id = businessId,
                    created_at = createdAt,
                    remote_updated_at = createdAt,
                    sync_status = SyncStatus.SYNCED
                )
                if (existing == null) sales.insertPago(mapped) else sales.updatePago(mapped)
            }
            val newest = remoteRows.maxOf { it.createdAt.toEpochMillisOrZero() }
            queue.upsertMetadata(SyncMetadata(businessId, "credit_payments", newest, System.currentTimeMillis()))
        }
    }

    private suspend fun pullStockMovements() {
        val cursor = queue.metadata(businessId, "stock_movements")?.last_pulled_at ?: 0L
        val remoteRows = SupabaseProvider.client.from("stock_movements")
            .select {
                filter {
                    eq("business_id", businessId)
                    gt("created_at", cursor.toTimestamp())
                }
            }
            .decodeList<RemoteStockMovement>()
        if (remoteRows.isEmpty()) return

        database.withTransaction {
            remoteRows.forEach { remote ->
                if (movements.getBySyncId(remote.id) != null) return@forEach
                val productId = products.getBySyncId(remote.productId)?.id ?: return@forEach
                val saleId = remote.saleId?.let { sales.getBySyncId(it)?.id }
                val createdAt = remote.createdAt.toEpochMillisOrZero()
                movements.insert(
                    StockMovement(
                        productId = productId,
                        saleId = saleId,
                        type = remote.type,
                        quantity_delta = remote.quantityDelta,
                        notes = remote.notes,
                        sync_id = remote.id,
                        business_id = businessId,
                        created_at = createdAt,
                        remote_created_at = createdAt,
                        sync_status = SyncStatus.SYNCED
                    )
                )
            }
            val newest = remoteRows.maxOf { it.createdAt.toEpochMillisOrZero() }
            queue.upsertMetadata(SyncMetadata(businessId, "stock_movements", newest, System.currentTimeMillis()))
        }
    }

    private suspend fun refreshLocalDebts() {
        customers.getAllForSync()
            .filter { it.business_id == businessId && it.deleted_at == null }
            .forEach { customer ->
                sales.updateClientDebtFromRemote(customer.id, customers.calcularDeuda(customer.id))
            }
    }

    private fun Venta.toSyncSale(customerIds: Map<Long, String>) = SyncSale(
        id = requireNotNull(sync_id),
        businessId = businessId,
        customerId = clienteId?.let(customerIds::get),
        totalCents = total_centavos,
        paymentMethod = tipo_pago,
        status = estado,
        soldAt = fecha_hora.toTimestamp(),
        evidencePath = ruta_evidencia,
        paidAt = fecha_pago?.toTimestamp()
    )

    private fun DetalleVenta.toSyncSaleItem(
        saleIds: Map<Long, String>,
        productIds: Map<Long, String>,
        productsById: Map<Long, Producto>
    ): SyncSaleItem? {
        val saleId = saleIds[ventaId] ?: return null
        val productId = productIds[productoId] ?: return null
        return SyncSaleItem(
            id = requireNotNull(sync_id),
            businessId = businessId,
            saleId = saleId,
            productId = productId,
            productNameSnapshot = productsById[productoId]?.nombre ?: "Producto",
            quantity = cantidad,
            unitPriceCents = precio_unitario_centavos,
            unitCostCents = costo_unitario_centavos
        )
    }

    private fun ProductOperation.toQueueItem(type: String, syncId: String, version: Long, now: Long) =
        queueItem(type, syncId, version, now, json.encodeToString(ProductOperation.serializer(), this))

    private fun ImageUploadOperation.toQueueItem(type: String, syncId: String, version: Long, now: Long) =
        queueItem(type, syncId, version, now, json.encodeToString(ImageUploadOperation.serializer(), this))

    private fun ImageDeleteOperation.toQueueItem(type: String, syncId: String, version: Long, now: Long) =
        queueItem(type, syncId, version, now, json.encodeToString(ImageDeleteOperation.serializer(), this))

    private fun CustomerOperation.toQueueItem(type: String, syncId: String, version: Long, now: Long) =
        queueItem(type, syncId, version, now, json.encodeToString(CustomerOperation.serializer(), this))

    private fun SettingsOperation.toQueueItem(type: String, syncId: String, version: Long, now: Long) =
        queueItem(type, syncId, version, now, json.encodeToString(SettingsOperation.serializer(), this))

    private fun SaleBundleOperation.toQueueItem(type: String, syncId: String, version: Long, now: Long) =
        queueItem(type, syncId, version, now, json.encodeToString(SaleBundleOperation.serializer(), this))

    private fun SaleTransitionOperation.toQueueItem(type: String, syncId: String, version: Long, now: Long) =
        queueItem(type, syncId, version, now, json.encodeToString(SaleTransitionOperation.serializer(), this))

    private fun SaleItemOperation.toQueueItem(type: String, syncId: String, version: Long, now: Long) =
        queueItem(type, syncId, version, now, json.encodeToString(SaleItemOperation.serializer(), this))

    private fun PaymentOperation.toQueueItem(type: String, syncId: String, version: Long, now: Long) =
        queueItem(type, syncId, version, now, json.encodeToString(PaymentOperation.serializer(), this))

    private fun StockMovementOperation.toQueueItem(type: String, syncId: String, version: Long, now: Long) =
        queueItem(type, syncId, version, now, json.encodeToString(StockMovementOperation.serializer(), this))

    private fun queueItem(type: String, syncId: String, version: Long, now: Long, payload: String) = SyncQueueItem(
        operation_id = "$type:$syncId:$version",
        business_id = businessId,
        entity_type = type,
        entity_sync_id = syncId,
        operation = "UPSERT",
        payload_json = payload,
        created_at = now
    )

    private suspend fun Producto.ensureSyncId(save: suspend (Producto) -> Unit): Producto {
        if (sync_id != null) return this
        return copy(sync_id = UUID.randomUUID().toString()).also { save(it) }
    }

    private suspend fun Cliente.ensureSyncId(save: suspend (Cliente) -> Unit): Cliente {
        if (sync_id != null) return this
        return copy(sync_id = UUID.randomUUID().toString()).also { save(it) }
    }

    private suspend fun Venta.ensureSyncId(save: suspend (Venta) -> Unit): Venta {
        if (sync_id != null) return this
        return copy(sync_id = UUID.randomUUID().toString()).also { save(it) }
    }

    private suspend fun DetalleVenta.ensureSyncId(save: suspend (DetalleVenta) -> Unit): DetalleVenta {
        if (sync_id != null) return this
        return copy(sync_id = UUID.randomUUID().toString()).also { save(it) }
    }

    private suspend fun PagoFiado.ensureSyncId(save: suspend (PagoFiado) -> Unit): PagoFiado {
        if (sync_id != null) return this
        return copy(sync_id = UUID.randomUUID().toString()).also { save(it) }
    }
}

private class PendingSyncOperationsException : IllegalStateException("Quedan cambios pendientes de sincronizar")

private fun Throwable.safeSyncMessage(): String =
    (message ?: "No se pudo sincronizar").replace(Regex("[\\r\\n]+"), " ").take(300)

private fun Long.toTimestamp(): String = Instant.ofEpochMilli(this).toString()
private fun String.toEpochMillisOrZero(): Long = runCatching { Instant.parse(this).toEpochMilli() }.getOrDefault(0L)

@Serializable private data class ProductOperation(val localVersion: Long, val data: SyncProduct)
@Serializable private data class ImageUploadOperation(
    val productSyncId: String,
    val localPath: String,
    val storagePath: String
)
@Serializable private data class ImageDeleteOperation(val productSyncId: String, val storagePath: String)
@Serializable private data class CustomerOperation(val localVersion: Long, val data: SyncCustomer)
@Serializable private data class SettingsOperation(val localVersion: Long, val data: SyncBusinessSettings)
@Serializable private data class SaleBundleOperation(
    val localVersion: Long,
    val sale: SyncSale,
    val items: List<SyncSaleItem>
)
@Serializable private data class SaleTransitionOperation(val localVersion: Long, val sale: SyncSale)
@Serializable private data class SaleItemOperation(val data: SyncSaleItem)
@Serializable private data class PaymentOperation(val data: SyncCreditPayment)
@Serializable private data class StockMovementOperation(val data: SyncStockMovement)

@Serializable private data class SaleBundleRpc(val payload: SyncSaleBundle)
@Serializable private data class SaleItemRpc(val payload: SyncSaleItem)
@Serializable private data class CreditPaymentRpc(val payload: SyncCreditPayment)
@Serializable private data class StockMovementRpc(val payload: SyncStockMovement)
@Serializable private data class BusinessSettingsRpc(
    @SerialName("target_business_id") val targetBusinessId: String,
    @SerialName("target_currency") val targetCurrency: String,
    @SerialName("target_daily_goal_cents") val targetDailyGoalCents: Long,
    @SerialName("target_low_stock_enabled") val targetLowStockEnabled: Boolean,
    @SerialName("target_receipt_message") val targetReceiptMessage: String
)
@Serializable private data class SaleTransitionRpc(
    @SerialName("target_sale_id") val targetSaleId: String,
    @SerialName("target_business_id") val targetBusinessId: String,
    @SerialName("new_status") val newStatus: String,
    @SerialName("target_paid_at") val paidAt: String?,
    @SerialName("target_cancellation_reason") val cancellationReason: String?
)

@Serializable private data class SyncSaleBundle(val sale: SyncSale, val items: List<SyncSaleItem>)

@Serializable private data class SyncProduct(
    val id: String,
    @SerialName("business_id") val businessId: String,
    val name: String,
    val barcode: String?,
    @SerialName("cost_cents") val costCents: Long,
    @SerialName("sale_cents") val saleCents: Long,
    val stock: Int,
    @SerialName("min_stock") val minStock: Int = 5,
    @SerialName("image_path") val imagePath: String?,
    @SerialName("normalized_search") val normalizedSearch: String,
    @SerialName("deleted_at") val deletedAt: String? = null
)

@Serializable private data class SyncCustomer(
    val id: String,
    @SerialName("business_id") val businessId: String,
    val name: String,
    val phone: String?,
    val notes: String,
    @SerialName("deleted_at") val deletedAt: String? = null
)

@Serializable private data class SyncBusinessSettings(
    @SerialName("business_id") val businessId: String,
    val currency: String,
    @SerialName("daily_goal_cents") val dailyGoalCents: Long,
    @SerialName("low_stock_enabled") val lowStockEnabled: Boolean,
    @SerialName("receipt_message") val receiptMessage: String
)

@Serializable private data class SyncSale(
    val id: String,
    @SerialName("business_id") val businessId: String,
    @SerialName("customer_id") val customerId: String?,
    @SerialName("total_cents") val totalCents: Long,
    @SerialName("payment_method") val paymentMethod: String,
    val status: String,
    @SerialName("sold_at") val soldAt: String,
    @SerialName("evidence_path") val evidencePath: String?,
    @SerialName("paid_at") val paidAt: String?
)

@Serializable private data class SyncSaleItem(
    val id: String,
    @SerialName("business_id") val businessId: String,
    @SerialName("sale_id") val saleId: String,
    @SerialName("product_id") val productId: String,
    @SerialName("product_name_snapshot") val productNameSnapshot: String,
    val quantity: Int,
    @SerialName("unit_price_cents") val unitPriceCents: Long,
    @SerialName("unit_cost_cents") val unitCostCents: Long
)

@Serializable private data class SyncCreditPayment(
    val id: String,
    @SerialName("business_id") val businessId: String,
    @SerialName("customer_id") val customerId: String,
    @SerialName("sale_id") val saleId: String,
    @SerialName("amount_cents") val amountCents: Long,
    @SerialName("payment_method") val paymentMethod: String,
    val notes: String,
    @SerialName("paid_at") val paidAt: String
)

@Serializable private data class SyncStockMovement(
    val id: String,
    @SerialName("business_id") val businessId: String,
    @SerialName("product_id") val productId: String,
    @SerialName("sale_id") val saleId: String? = null,
    val type: String,
    @SerialName("quantity_delta") val quantityDelta: Int,
    val notes: String,
    @SerialName("created_at") val createdAt: String
)

@Serializable private data class RemoteProduct(
    val id: String,
    @SerialName("business_id") val businessId: String,
    val name: String,
    val barcode: String? = null,
    @SerialName("cost_cents") val costCents: Long,
    @SerialName("sale_cents") val saleCents: Long,
    val stock: Int,
    @SerialName("min_stock") val minStock: Int,
    @SerialName("image_path") val imagePath: String? = null,
    @SerialName("normalized_search") val normalizedSearch: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null
)

@Serializable private data class RemoteCustomer(
    val id: String,
    @SerialName("business_id") val businessId: String,
    val name: String,
    val phone: String? = null,
    val notes: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null
)

@Serializable private data class RemoteBusinessSettings(
    val id: String,
    val currency: String = "PEN",
    @SerialName("daily_goal_cents") val dailyGoalCents: Long = 50_000,
    @SerialName("low_stock_enabled") val lowStockEnabled: Boolean = true,
    @SerialName("receipt_message") val receiptMessage: String = "",
    @SerialName("updated_at") val updatedAt: String
)

@Serializable private data class RemoteSale(
    val id: String,
    @SerialName("business_id") val businessId: String,
    @SerialName("customer_id") val customerId: String? = null,
    @SerialName("total_cents") val totalCents: Long,
    @SerialName("payment_method") val paymentMethod: String,
    val status: String,
    @SerialName("sold_at") val soldAt: String,
    @SerialName("evidence_path") val evidencePath: String? = null,
    @SerialName("paid_at") val paidAt: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String
)

@Serializable private data class RemoteSaleItem(
    val id: String,
    @SerialName("business_id") val businessId: String,
    @SerialName("sale_id") val saleId: String,
    @SerialName("product_id") val productId: String? = null,
    @SerialName("product_name_snapshot") val productNameSnapshot: String,
    val quantity: Int,
    @SerialName("unit_price_cents") val unitPriceCents: Long,
    @SerialName("unit_cost_cents") val unitCostCents: Long,
    @SerialName("created_at") val createdAt: String
)

@Serializable private data class RemoteCreditPayment(
    val id: String,
    @SerialName("business_id") val businessId: String,
    @SerialName("customer_id") val customerId: String,
    @SerialName("sale_id") val saleId: String,
    @SerialName("amount_cents") val amountCents: Long,
    @SerialName("payment_method") val paymentMethod: String,
    val notes: String,
    @SerialName("paid_at") val paidAt: String,
    @SerialName("created_at") val createdAt: String
)

@Serializable private data class RemoteStockMovement(
    val id: String,
    @SerialName("business_id") val businessId: String,
    @SerialName("product_id") val productId: String,
    @SerialName("sale_id") val saleId: String? = null,
    val type: String,
    @SerialName("quantity_delta") val quantityDelta: Int,
    val notes: String,
    @SerialName("created_at") val createdAt: String
)
