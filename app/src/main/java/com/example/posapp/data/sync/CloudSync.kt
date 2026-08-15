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
import com.example.posapp.auth.AuthRepository
import com.example.posapp.auth.isInvalidRemoteSession
import com.example.posapp.data.ActiveBusinessStore
import com.example.posapp.data.AppDatabase
import com.example.posapp.data.normalizedUuidOrNull
import com.example.posapp.data.requireCloudUuid
import com.example.posapp.data.entities.Cliente
import com.example.posapp.data.entities.DetalleVenta
import com.example.posapp.data.entities.ImageSyncStatus
import com.example.posapp.data.entities.PagoFiado
import com.example.posapp.data.entities.Producto
import com.example.posapp.data.entities.SyncMetadata
import com.example.posapp.data.entities.SyncQueueItem
import com.example.posapp.data.entities.SyncStatus
import com.example.posapp.data.entities.StockMovement
import com.example.posapp.data.entities.Venta
import com.example.posapp.data.remote.SupabaseProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
            val activeBusinessStore = ActiveBusinessStore(applicationContext)
            val user = client.auth.currentUserOrNull() ?: run {
                if (activeBusinessStore.businessId().isNotBlank()) {
                    AuthRepository(applicationContext).clearLocalAccountData(cancelScheduledSync = false)
                }
                CloudSyncRuntime.update(
                    CloudSyncState(
                        CloudSyncPhase.ERROR,
                        message = "La sesion local termino. Ingresa nuevamente."
                    )
                )
                return Result.success()
            }
            val userId = user.id.requireCloudUuid("user_id")
            if (activeBusinessStore.userId() != null && activeBusinessStore.userId() != userId) {
                AuthRepository(applicationContext).clearLocalAccountData(cancelScheduledSync = false)
            }
            val preferredBusinessId = activeBusinessStore.businessId()
            val accessibleBusinesses = client.from("businesses")
                .select()
                .decodeList<RemoteBusiness>()
                .filter { it.id.normalizedUuidOrNull() != null }
            val business = accessibleBusinesses.firstOrNull { it.id == preferredBusinessId }
                ?: accessibleBusinesses.firstOrNull()
                ?: run {
                    if (preferredBusinessId.isNotBlank()) {
                        AuthRepository(applicationContext).clearLocalAccountData(cancelScheduledSync = false)
                    }
                    CloudSyncRuntime.update(
                        CloudSyncState(
                            CloudSyncPhase.ERROR,
                            message = "No hay un negocio disponible. Abre SpaceSale para cargar o crear uno."
                        )
                    )
                    return Result.success()
                }

            if (preferredBusinessId != business.id) {
                if (preferredBusinessId.isNotBlank()) {
                    AuthRepository(applicationContext).clearLocalAccountData(cancelScheduledSync = false)
                }
                AuthRepository(applicationContext).bindLocalDataTo(userId, business.id)
            }
            CloudSyncCoordinator.synchronizeNow(applicationContext, business.id)
        }.fold(
            onSuccess = {
                val now = System.currentTimeMillis()
                CloudSyncRuntime.update(CloudSyncState(CloudSyncPhase.SYNCED, lastSuccessAt = now))
                Result.success()
            },
            onFailure = { error ->
                if (isInvalidRemoteSession(error)) {
                    AuthRepository(applicationContext).signOutAndClearLocalData()
                    CloudSyncRuntime.update(
                        CloudSyncState(
                            CloudSyncPhase.ERROR,
                            message = "La sesion remota ya no es valida. Ingresa nuevamente."
                        )
                    )
                    return@fold Result.success()
                }
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
                if (error is ActionRequiredSyncException) Result.failure() else Result.retry()
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
        val businessId = ActiveBusinessStore(appContext).businessId().normalizedUuidOrNull()
        if (businessId == null) {
            CloudSyncRuntime.update(
                CloudSyncState(
                    CloudSyncPhase.ERROR,
                    message = "No hay un negocio activo. Carga o crea uno antes de sincronizar."
                )
            )
            return
        }
        val businessSuffix = businessId
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

    suspend fun retryPendingChanges(context: Context) {
        val appContext = context.applicationContext
        val businessId = ActiveBusinessStore(appContext).businessId().normalizedUuidOrNull()
        if (businessId == null) {
            CloudSyncRuntime.update(
                CloudSyncState(
                    CloudSyncPhase.ERROR,
                    message = "No hay un negocio activo. Carga o crea uno antes de sincronizar."
                )
            )
            return
        }
        AppDatabase.getInstance(appContext).syncDao().retryActionRequired(businessId)
        schedule(appContext)
    }
}

object CloudSyncCoordinator {
    private val mutex = Mutex()

    suspend fun prepareQueue(context: Context, businessId: String) = mutex.withLock {
        CloudSyncEngine(context.applicationContext, businessId.requireCloudUuid("business_id")).prepareQueue()
    }

    suspend fun synchronizeNow(context: Context, businessId: String) = mutex.withLock {
        CloudSyncEngine(context.applicationContext, businessId.requireCloudUuid("business_id")).synchronize()
    }
}

private class CloudSyncEngine(context: Context, businessId: String) {
    private val businessId = businessId.requireCloudUuid("business_id")
    private val appContext = context.applicationContext
    private val database = AppDatabase.getInstance(appContext)
    private val products = database.productoDao()
    private val customers = database.clienteDao()
    private val sales = database.ventaDao()
    private val queue = database.syncDao()
    private val movements = database.stockMovementDao()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private companion object {
        const val PULL_PAGE_SIZE = 500
    }

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
        if (queue.actionRequiredCount(businessId) > 0) throw ActionRequiredSyncException()
        if (pending > 0) throw PendingSyncOperationsException()
    }

    suspend fun prepareQueue() {
        enqueuePendingChanges()
    }

    private suspend fun enqueuePendingChanges() {
        val now = System.currentTimeMillis()
        val localProducts = products.getAllForSync(businessId)
            .map { it.ensureSyncId(products::update) }
        val localCustomers = customers.getAllForSync(businessId)
            .map { it.ensureSyncId(customers::update) }
        val localSales = sales.getAllVentas(businessId)
            .map { it.ensureSyncId(sales::updateVenta) }
        val localDetails = sales.getAllDetalles(businessId)
            .map { it.ensureSyncId(sales::updateDetalle) }
        val localPayments = sales.getAllPagos(businessId)
            .map { it.ensureSyncId(sales::updatePago) }
        val localMovements = movements.getAllForSync(businessId)
            .map { it.ensureSyncId() }
        val localSettings = queue.businessSettings(businessId)

        val productIds = localProducts.associate { it.id to requireNotNull(it.sync_id) }
        val customerIds = localCustomers.associate { it.id to requireNotNull(it.sync_id) }
        val saleIds = localSales.associate { it.id to requireNotNull(it.sync_id) }
        val productsById = localProducts.associateBy(Producto::id)
        val detailsBySale = localDetails.groupBy(DetalleVenta::ventaId)
        val movementsBySale = localMovements.filter { it.saleId != null }.groupBy { requireNotNull(it.saleId) }

        localProducts.filter { it.sync_status != SyncStatus.SYNCED }.forEach { product ->
            val syncId = requireNotNull(product.sync_id)
            val payload = ProductOperation(
                localVersion = product.updated_at,
                data = product.toSyncProduct(product.storage_path)
            )
            queue.enqueue(payload.toQueueItem("PRODUCT", syncId, product.updated_at, now))
            if (product.deleted_at != null && !product.storage_path.isNullOrBlank()) {
                val deletePayload = ImageDeleteOperation(syncId, product.storage_path)
                queue.enqueue(deletePayload.toQueueItem("IMAGE_DELETE", syncId, product.updated_at, now))
            }
        }

        // La foto tiene su propio ciclo de vida: nunca bloquea los datos del producto.
        localProducts.filter { it.deleted_at == null && it.image_sync_status != ImageSyncStatus.SYNCED && it.image_sync_status != ImageSyncStatus.NONE }
            .forEach { product ->
                val syncId = requireNotNull(product.sync_id)
                val localPath = product.ruta_imagen
                val localImage = localPath?.let(::File)
                if (localImage?.isFile == true) {
                    val storagePath = product.storage_path ?: "$businessId/products/$syncId/main.jpg"
                    queue.enqueue(
                        ImageUploadOperation(syncId, localImage.absolutePath, storagePath)
                            .toQueueItem("IMAGE_UPLOAD", syncId, product.updated_at, now)
                    )
                } else if (product.image_sync_status in setOf(
                        ImageSyncStatus.LOCAL_PENDING,
                        ImageSyncStatus.UPLOADING,
                        ImageSyncStatus.ERROR_RETRYABLE,
                        ImageSyncStatus.ERROR_MISSING_FILE
                    )) {
                    products.updateImageStatus(businessId, syncId, ImageSyncStatus.ERROR_MISSING_FILE)
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
            val movementPayloads = movementsBySale[sale.id].orEmpty().mapNotNull { movement ->
                movement.toSyncStockMovement(productIds, saleIds)
            }
            queue.deleteEntityOperations(businessId, "SALE_BUNDLE", syncId)
            if (!sale.nube_sincronizada || sale.remote_updated_at == null) {
                val itemPayloads = detailsBySale[sale.id].orEmpty().mapNotNull { detail ->
                    detail.toSyncSaleItem(saleIds, productIds, productsById)
                }
                queue.deleteEntityOperations(businessId, "SALE_CANCEL_ATOMIC", syncId)
                val payload = SaleBundleOperation(sale.updated_at, salePayload, itemPayloads, movementPayloads)
                val operation = payload.toQueueItem("SALE_ATOMIC", syncId, sale.updated_at, now)
                queue.deleteStaleEntityOperations(businessId, "SALE_ATOMIC", syncId, operation.operation_id)
                queue.enqueue(operation)
            } else if (sale.estado == "ANULADO") {
                queue.deleteEntityOperations(businessId, "SALE_TRANSITION", syncId)
                queue.deleteEntityOperations(businessId, "SALE_ATOMIC", syncId)
                val payload = SaleCancellationOperation(sale.updated_at, salePayload, movementPayloads)
                val operation = payload.toQueueItem("SALE_CANCEL_ATOMIC", syncId, sale.updated_at, now)
                queue.deleteStaleEntityOperations(businessId, "SALE_CANCEL_ATOMIC", syncId, operation.operation_id)
                queue.enqueue(operation)
            } else {
                queue.deleteEntityOperations(businessId, "SALE_CANCEL_ATOMIC", syncId)
                queue.deleteEntityOperations(businessId, "SALE_ATOMIC", syncId)
                val payload = SaleTransitionOperation(sale.updated_at, salePayload)
                val operation = payload.toQueueItem("SALE_TRANSITION", syncId, sale.updated_at, now)
                queue.deleteStaleEntityOperations(businessId, "SALE_TRANSITION", syncId, operation.operation_id)
                queue.enqueue(operation)
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
            if (movement.saleId != null) {
                queue.deleteEntityOperations(businessId, "STOCK_MOVEMENT", movement.sync_id)
                return@forEach
            }
            val data = movement.toSyncStockMovement(productIds, saleIds) ?: return@forEach
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
                    .onSuccess { queue.confirm(businessId, operation.operation_id) }
                    .onFailure { error ->
                        hadFailure = true
                        val disposition = classifySyncFailure(error)
                        val exponent = min(operation.attempt_count, 10)
                        val delayMs = if (disposition == SyncFailureDisposition.ACTION_REQUIRED) {
                            Long.MAX_VALUE
                        } else {
                            System.currentTimeMillis() + min(6L * 60L * 60L * 1000L, 30_000L * (1L shl exponent))
                        }
                        queue.markFailed(
                            businessId = businessId,
                            operationId = operation.operation_id,
                            nextAttemptAt = delayMs,
                            message = error.userSafeSyncMessage(disposition)
                        )
                    }
            }
        }
        if (hadFailure) throw PendingSyncOperationsException()
    }

    private suspend fun push(operation: SyncQueueItem) {
        check(operation.business_id.requireCloudUuid("business_id") == businessId) {
            "La operacion pertenece a otro negocio"
        }
        operation.entity_sync_id.requireCloudUuid("sync_id")
        val now = System.currentTimeMillis()
        when (operation.entity_type) {
            "IMAGE_UPLOAD" -> {
                val payload = json.decodeFromString(ImageUploadOperation.serializer(), operation.payload_json)
                val image = File(payload.localPath)
                if (!image.isFile) {
                    products.updateImageStatus(businessId, payload.productSyncId, ImageSyncStatus.ERROR_MISSING_FILE)
                    throw MissingImageFileException()
                }
                products.updateImageStatus(businessId, payload.productSyncId, ImageSyncStatus.UPLOADING)
                runCatching {
                    SupabaseProvider.client.storage.from("product-images")
                        .upload(payload.storagePath, image.readBytes()) { upsert = true }
                }.onFailure {
                    products.updateImageStatus(businessId, payload.productSyncId, ImageSyncStatus.ERROR_RETRYABLE)
                }.getOrThrow()
                val product = products.getBySyncId(businessId, payload.productSyncId)
                    ?: throw MissingImageFileException("El producto de la foto ya no existe")
                SupabaseProvider.client.postgrest.rpc(
                    "upsert_product_metadata",
                    json.encodeToJsonElement(
                        ProductMetadataRpc.serializer(),
                        ProductMetadataRpc(product.toSyncProduct(payload.storagePath))
                    ).jsonObject
                )
                products.markImageUploaded(businessId, payload.productSyncId, payload.storagePath)
            }
            "PRODUCT" -> {
                val payload = json.decodeFromString(ProductOperation.serializer(), operation.payload_json)
                SupabaseProvider.client.postgrest.rpc(
                    "upsert_product_metadata",
                    json.encodeToJsonElement(ProductMetadataRpc.serializer(), ProductMetadataRpc(payload.data)).jsonObject
                )
                products.markSyncedBySyncId(businessId, payload.data.id, payload.localVersion, now)
            }
            "IMAGE_DELETE" -> {
                val payload = json.decodeFromString(ImageDeleteOperation.serializer(), operation.payload_json)
                SupabaseProvider.client.storage.from("product-images").delete(payload.storagePath)
            }
            "CUSTOMER" -> {
                val payload = json.decodeFromString(CustomerOperation.serializer(), operation.payload_json)
                SupabaseProvider.client.from("customers").upsert(payload.data)
                customers.markSyncedBySyncId(businessId, payload.data.id, payload.localVersion, now)
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
            "SALE_ATOMIC" -> {
                val payload = json.decodeFromString(SaleBundleOperation.serializer(), operation.payload_json)
                val result = SupabaseProvider.client.postgrest.rpc(
                    "confirm_sale_bundle",
                    json.encodeToJsonElement(
                        SaleBundleRpc.serializer(),
                        SaleBundleRpc(payload = SyncSaleBundle(payload.sale, payload.items, payload.movements))
                    ).jsonObject
                ).decodeSingle<AtomicStockResult>()
                applyConfirmedStocks(result.stocks, now)
                sales.markVentaSyncedBySyncId(businessId, payload.sale.id, payload.localVersion, now)
                if (payload.items.isNotEmpty()) {
                    sales.markDetallesSyncedBySyncIds(businessId, payload.items.map(SyncSaleItem::id), now)
                }
                payload.movements.forEach { movements.markSynced(businessId, it.id, now) }
            }
            "SALE_CANCEL_ATOMIC" -> {
                val payload = json.decodeFromString(SaleCancellationOperation.serializer(), operation.payload_json)
                val result = SupabaseProvider.client.postgrest.rpc(
                    "cancel_sale_bundle",
                    json.encodeToJsonElement(
                        SaleCancellationRpc.serializer(),
                        SaleCancellationRpc(
                            payload = SyncSaleCancellation(
                                saleId = payload.sale.id,
                                businessId = payload.sale.businessId,
                                reason = "Anulada desde SpaceSale",
                                movements = payload.movements
                            )
                        )
                    ).jsonObject
                ).decodeSingle<AtomicStockResult>()
                applyConfirmedStocks(result.stocks, now)
                sales.markVentaSyncedBySyncId(businessId, payload.sale.id, payload.localVersion, now)
                payload.movements.forEach { movements.markSynced(businessId, it.id, now) }
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
                sales.markVentaSyncedBySyncId(businessId, payload.sale.id, payload.localVersion, now)
            }
            "SALE_ITEM" -> {
                val payload = json.decodeFromString(SaleItemOperation.serializer(), operation.payload_json)
                SupabaseProvider.client.postgrest.rpc(
                    "insert_sale_item_if_absent",
                    json.encodeToJsonElement(SaleItemRpc.serializer(), SaleItemRpc(payload.data)).jsonObject
                )
                sales.markDetallesSyncedBySyncIds(businessId, listOf(payload.data.id), now)
            }
            "PAYMENT" -> {
                val payload = json.decodeFromString(PaymentOperation.serializer(), operation.payload_json)
                SupabaseProvider.client.postgrest.rpc(
                    "insert_credit_payment_if_absent",
                    json.encodeToJsonElement(CreditPaymentRpc.serializer(), CreditPaymentRpc(payload.data)).jsonObject
                )
                sales.markPagoSyncedBySyncId(businessId, payload.data.id, now)
            }
            "STOCK_MOVEMENT" -> {
                val payload = json.decodeFromString(StockMovementOperation.serializer(), operation.payload_json)
                val result = SupabaseProvider.client.postgrest.rpc(
                    "apply_stock_movement",
                    json.encodeToJsonElement(StockMovementRpc.serializer(), StockMovementRpc(payload.data)).jsonObject
                ).decodeSingle<AtomicStockResult>()
                applyConfirmedStocks(result.stocks, now)
                movements.markSynced(businessId, payload.data.id, now)
            }
            else -> error("Tipo de operacion de sincronizacion desconocido")
        }
    }

    private suspend fun pullRemoteChanges() {
        pullProducts()
        pullBusinessSettings()
        pullCustomers()
        pullSales()
        pullSaleItems()
        pullCreditPayments()
        pullStockMovements()
    }

    private suspend inline fun <reified T> fetchRemotePage(
        table: String,
        timestampColumn: String,
        cursor: RemotePullCursor
    ): List<T> = SupabaseProvider.client.from(table)
        .select {
            filter {
                eq("business_id", businessId)
                val remoteIdFilter = cursor.remoteIdForFilter()
                if (remoteIdFilter == null) {
                    gt(timestampColumn, cursor.serverTimestamp)
                } else {
                    or {
                        gt(timestampColumn, cursor.serverTimestamp)
                        and {
                            eq(timestampColumn, cursor.serverTimestamp)
                            gt("id", remoteIdFilter)
                        }
                    }
                }
            }
            order(timestampColumn, Order.ASCENDING)
            order("id", Order.ASCENDING)
            limit(PULL_PAGE_SIZE.toLong())
        }
        .decodeList<T>()

    private suspend fun <T> pullPaged(
        entityType: String,
        timestampOf: (T) -> String,
        idOf: (T) -> String,
        fetchPage: suspend (RemotePullCursor) -> List<T>,
        applyPage: suspend (List<T>) -> Unit
    ) {
        val saved = queue.metadata(businessId, entityType)
        var cursor = RemotePullCursor(
            serverTimestamp = saved?.last_server_timestamp ?: RemotePullCursor.EPOCH,
            remoteId = saved?.last_remote_id.normalizedUuidOrNull().orEmpty()
        )
        var downloadedPage = false

        while (true) {
            val rows = fetchPage(cursor)
            if (rows.isEmpty()) break
            val last = rows.last()
            val nextCursor = advanceRemoteCursor(cursor, timestampOf(last), idOf(last))

            database.withTransaction {
                applyPage(rows)
                queue.upsertMetadata(
                    SyncMetadata(
                        business_id = businessId,
                        entity_type = entityType,
                        last_server_timestamp = nextCursor.serverTimestamp,
                        last_remote_id = nextCursor.remoteId,
                        last_success_at = System.currentTimeMillis()
                    )
                )
            }
            cursor = nextCursor
            downloadedPage = true
            if (rows.size < PULL_PAGE_SIZE) break
        }

        if (!downloadedPage) {
            queue.upsertMetadata(
                SyncMetadata(
                    business_id = businessId,
                    entity_type = entityType,
                    last_server_timestamp = cursor.serverTimestamp,
                    last_remote_id = cursor.remoteId,
                    last_success_at = System.currentTimeMillis()
                )
            )
        }
    }

    private suspend fun pullProducts() {
        pullPaged(
            entityType = "products",
            timestampOf = RemoteProduct::updatedAt,
            idOf = RemoteProduct::id,
            fetchPage = { cursor -> fetchRemotePage("products", "updated_at", cursor) }
        ) { remoteRows ->
            remoteRows.forEach { remote ->
                val existing = products.getBySyncId(businessId, remote.id)
                val createdAt = remote.createdAt.toEpochMillisOrZero()
                val updatedAt = remote.updatedAt.toEpochMillisOrZero()
                if (existing != null && existing.sync_status != SyncStatus.SYNCED && existing.updated_at > updatedAt) {
                    return@forEach
                }
                val hasPendingLocalImage = existing?.ruta_imagen?.let(::File)?.isFile == true &&
                    existing.image_sync_status in setOf(
                        ImageSyncStatus.LOCAL_PENDING,
                        ImageSyncStatus.UPLOADING,
                        ImageSyncStatus.ERROR_RETRYABLE,
                        ImageSyncStatus.ERROR_MISSING_FILE
                    )
                val keepCachedImage = existing?.ruta_imagen?.takeIf {
                    !remote.imagePath.isNullOrBlank() && existing.storage_path == remote.imagePath &&
                        (existing.remote_updated_at ?: 0L) >= updatedAt &&
                        File(it).isFile
                }
                val mapped = Producto(
                    id = existing?.id ?: 0,
                    nombre = remote.name,
                    precio_costo = 0.0,
                    precio_venta = 0.0,
                    stock = remote.stock,
                    ruta_imagen = if (hasPendingLocalImage) requireNotNull(existing).ruta_imagen else keepCachedImage,
                    storage_path = remote.imagePath,
                    image_sync_status = when {
                        hasPendingLocalImage -> requireNotNull(existing).image_sync_status
                        remote.imagePath.isNullOrBlank() -> ImageSyncStatus.NONE
                        keepCachedImage != null -> ImageSyncStatus.SYNCED
                        else -> ImageSyncStatus.DOWNLOAD_PENDING
                    },
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

    private suspend fun pullCustomers() {
        pullPaged(
            entityType = "customers",
            timestampOf = RemoteCustomer::updatedAt,
            idOf = RemoteCustomer::id,
            fetchPage = { cursor -> fetchRemotePage("customers", "updated_at", cursor) }
        ) { remoteRows ->
            remoteRows.forEach { remote ->
                val existing = customers.getBySyncId(businessId, remote.id)
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
        }
    }

    private suspend fun pullSales() {
        pullPaged(
            entityType = "sales",
            timestampOf = RemoteSale::updatedAt,
            idOf = RemoteSale::id,
            fetchPage = { cursor -> fetchRemotePage("sales", "updated_at", cursor) }
        ) { remoteRows ->
            remoteRows.forEach { remote ->
                val existing = sales.getBySyncId(businessId, remote.id)
                val createdAt = remote.createdAt.toEpochMillisOrZero()
                val updatedAt = remote.updatedAt.toEpochMillisOrZero()
                if (existing != null && existing.sync_status != SyncStatus.SYNCED && existing.updated_at > updatedAt) {
                    return@forEach
                }
                val localCustomerId = remote.customerId?.let { customerId ->
                    customers.getBySyncId(businessId, customerId)?.id
                        ?: error("La venta remota depende de un cliente que aun no se ha descargado")
                }
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
        }
    }

    private suspend fun pullSaleItems() {
        pullPaged(
            entityType = "sale_items",
            timestampOf = RemoteSaleItem::serverCreatedAt,
            idOf = RemoteSaleItem::id,
            fetchPage = { cursor -> fetchRemotePage("sale_items", "server_created_at", cursor) }
        ) { remoteRows ->
            remoteRows.forEach { remote ->
                val localSaleId = sales.getBySyncId(businessId, remote.saleId)?.id
                    ?: error("El detalle remoto depende de una venta que aun no se ha descargado")
                val localProductId = remote.productId?.let { productId ->
                    products.getBySyncId(businessId, productId)?.id
                }
                val existing = sales.getDetailBySyncId(businessId, remote.id)
                val createdAt = remote.createdAt.toEpochMillisOrZero()
                val mapped = DetalleVenta(
                    id = existing?.id ?: 0,
                    ventaId = localSaleId,
                    productoId = localProductId,
                    product_sync_id_snapshot = remote.productId,
                    product_name_snapshot = remote.productNameSnapshot,
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
        }
    }

    private suspend fun pullCreditPayments() {
        pullPaged(
            entityType = "credit_payments",
            timestampOf = RemoteCreditPayment::serverCreatedAt,
            idOf = RemoteCreditPayment::id,
            fetchPage = { cursor -> fetchRemotePage("credit_payments", "server_created_at", cursor) }
        ) { remoteRows ->
            remoteRows.forEach { remote ->
                val localSaleId = sales.getBySyncId(businessId, remote.saleId)?.id
                    ?: error("El pago remoto depende de una venta que aun no se ha descargado")
                val existing = sales.getPaymentBySyncId(businessId, remote.id)
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
        }
    }

    private suspend fun pullStockMovements() {
        pullPaged(
            entityType = "stock_movements",
            timestampOf = RemoteStockMovement::serverCreatedAt,
            idOf = RemoteStockMovement::id,
            fetchPage = { cursor -> fetchRemotePage("stock_movements", "server_created_at", cursor) }
        ) { remoteRows ->
            remoteRows.forEach { remote ->
                if (movements.getBySyncId(businessId, remote.id) != null) return@forEach
                val productId = products.getBySyncId(businessId, remote.productId)?.id
                    ?: error("El movimiento remoto depende de un producto que aun no se ha descargado")
                val saleId = remote.saleId?.let { remoteSaleId ->
                    sales.getBySyncId(businessId, remoteSaleId)?.id
                        ?: error("El movimiento remoto depende de una venta que aun no se ha descargado")
                }
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
        }
    }

    private suspend fun refreshLocalDebts() {
        customers.getAllForSync(businessId)
            .filter { it.deleted_at == null }
            .forEach { customer ->
                sales.updateClientDebtFromRemote(businessId, customer.id, customers.calcularDeuda(businessId, customer.id))
            }
    }

    private suspend fun applyConfirmedStocks(stocks: List<RemoteStockProjection>, now: Long) {
        stocks.forEach { projection ->
            products.applyRemoteStock(businessId, projection.productId, projection.stock, now)
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
        val productId = productoId?.let(productIds::get) ?: product_sync_id_snapshot.normalizedUuidOrNull()
        return SyncSaleItem(
            id = requireNotNull(sync_id),
            businessId = businessId,
            saleId = saleId,
            productId = productId,
            productNameSnapshot = product_name_snapshot.ifBlank {
                productoId?.let(productsById::get)?.nombre ?: "Producto"
            },
            quantity = cantidad,
            unitPriceCents = precio_unitario_centavos,
            unitCostCents = costo_unitario_centavos
        )
    }

    private fun StockMovement.toSyncStockMovement(
        productIds: Map<Long, String>,
        saleIds: Map<Long, String>
    ): SyncStockMovement? {
        val remoteProductId = productIds[productId] ?: return null
        val remoteSaleId = saleId?.let { saleIds[it] ?: return null }
        return SyncStockMovement(
            id = sync_id,
            businessId = businessId,
            productId = remoteProductId,
            saleId = remoteSaleId,
            type = type,
            quantityDelta = quantity_delta,
            notes = notes,
            createdAt = created_at.toTimestamp()
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

    private fun SaleCancellationOperation.toQueueItem(type: String, syncId: String, version: Long, now: Long) =
        queueItem(type, syncId, version, now, json.encodeToString(SaleCancellationOperation.serializer(), this))

    private fun SaleItemOperation.toQueueItem(type: String, syncId: String, version: Long, now: Long) =
        queueItem(type, syncId, version, now, json.encodeToString(SaleItemOperation.serializer(), this))

    private fun PaymentOperation.toQueueItem(type: String, syncId: String, version: Long, now: Long) =
        queueItem(type, syncId, version, now, json.encodeToString(PaymentOperation.serializer(), this))

    private fun StockMovementOperation.toQueueItem(type: String, syncId: String, version: Long, now: Long) =
        queueItem(type, syncId, version, now, json.encodeToString(StockMovementOperation.serializer(), this))

    private fun queueItem(type: String, syncId: String, version: Long, now: Long, payload: String) = SyncQueueItem(
        operation_id = "$type:${syncId.requireCloudUuid("sync_id")}:$version",
        business_id = businessId,
        entity_type = type,
        entity_sync_id = syncId.requireCloudUuid("sync_id"),
        operation = "UPSERT",
        payload_json = payload,
        created_at = now
    )

    private suspend fun Producto.ensureSyncId(save: suspend (Producto) -> Unit): Producto {
        if (sync_id.normalizedUuidOrNull() != null) return this
        return copy(sync_id = UUID.randomUUID().toString()).also { save(it) }
    }

    private suspend fun Cliente.ensureSyncId(save: suspend (Cliente) -> Unit): Cliente {
        if (sync_id.normalizedUuidOrNull() != null) return this
        return copy(sync_id = UUID.randomUUID().toString()).also { save(it) }
    }

    private suspend fun Venta.ensureSyncId(save: suspend (Venta) -> Unit): Venta {
        if (sync_id.normalizedUuidOrNull() != null) return this
        return copy(sync_id = UUID.randomUUID().toString()).also { save(it) }
    }

    private suspend fun DetalleVenta.ensureSyncId(save: suspend (DetalleVenta) -> Unit): DetalleVenta {
        if (sync_id.normalizedUuidOrNull() != null) return this
        return copy(sync_id = UUID.randomUUID().toString()).also { save(it) }
    }

    private suspend fun PagoFiado.ensureSyncId(save: suspend (PagoFiado) -> Unit): PagoFiado {
        if (sync_id.normalizedUuidOrNull() != null) return this
        return copy(sync_id = UUID.randomUUID().toString()).also { save(it) }
    }

    private suspend fun StockMovement.ensureSyncId(): StockMovement {
        if (sync_id.normalizedUuidOrNull() != null) return this
        val generated = UUID.randomUUID().toString()
        movements.updateSyncId(businessId, id, generated)
        return copy(sync_id = generated)
    }

    private fun Producto.toSyncProduct(imagePath: String?): SyncProduct = SyncProduct(
        id = requireNotNull(sync_id),
        businessId = businessId,
        name = nombre,
        costCents = precio_costo_centavos,
        saleCents = precio_venta_centavos,
        stock = stock,
        barcode = codigo_barras,
        minStock = stock_minimo,
        imagePath = imagePath,
        normalizedSearch = busqueda_normalizada,
        deletedAt = deleted_at?.toTimestamp()
    )
}

private class PendingSyncOperationsException : IllegalStateException("Quedan cambios pendientes de sincronizar")
private class ActionRequiredSyncException : IllegalStateException("Hay un cambio que requiere revision")
private class MissingImageFileException(
    message: String = "La foto local pendiente ya no existe"
) : IllegalStateException(message)

internal data class RemotePullCursor(
    val serverTimestamp: String = EPOCH,
    val remoteId: String = ""
) {
    companion object {
        const val EPOCH = "1970-01-01T00:00:00Z"
    }
}

internal fun RemotePullCursor.remoteIdForFilter(): String? = remoteId.normalizedUuidOrNull()

internal fun advanceRemoteCursor(
    current: RemotePullCursor,
    serverTimestamp: String,
    remoteId: String
): RemotePullCursor {
    require(serverTimestamp.isNotBlank() && remoteId.isNotBlank()) { "Cursor remoto incompleto" }
    val timestampOrder = Instant.parse(serverTimestamp).compareTo(Instant.parse(current.serverTimestamp))
    require(timestampOrder > 0 || timestampOrder == 0 && remoteId > current.remoteId) {
        "La pagina remota no avanzo el cursor"
    }
    return RemotePullCursor(serverTimestamp, remoteId)
}

internal enum class SyncFailureDisposition { RETRY, ACTION_REQUIRED }

internal fun classifySyncFailure(statusCode: Int?, databaseCode: String?): SyncFailureDisposition {
    if (statusCode == 408 || statusCode == 429 || statusCode != null && statusCode >= 500) {
        return SyncFailureDisposition.RETRY
    }
    val permanentCodes = setOf("22P02", "22023", "23503", "23505", "23514", "42501", "P0001", "P0002")
    return if (statusCode != null && statusCode in 400..499 || databaseCode in permanentCodes) {
        SyncFailureDisposition.ACTION_REQUIRED
    } else {
        SyncFailureDisposition.RETRY
    }
}

private fun classifySyncFailure(error: Throwable): SyncFailureDisposition {
    if (error is MissingImageFileException || error is IllegalArgumentException) {
        return SyncFailureDisposition.ACTION_REQUIRED
    }
    val postgrest = error as? PostgrestRestException
    return classifySyncFailure(postgrest?.statusCode, postgrest?.code)
}

private fun Throwable.safeSyncMessage(): String =
    (message ?: "No se pudo sincronizar").replace(Regex("[\\r\\n]+"), " ").take(300)

private fun Throwable.userSafeSyncMessage(disposition: SyncFailureDisposition): String {
    val postgrest = this as? PostgrestRestException
    val raw = listOfNotNull(message, postgrest?.error, postgrest?.description).joinToString(" ")
    return when {
        this is MissingImageFileException ->
            "La foto pendiente ya no esta en el telefono. Elige otra foto o quitala para continuar."
        raw.contains("STOCK_INSUFFICIENT", ignoreCase = true) ->
            "Stock insuficiente en la nube. Revisa el inventario antes de reintentar."
        disposition == SyncFailureDisposition.ACTION_REQUIRED ->
            "No se pudo aplicar este cambio. Revisa los datos y vuelve a intentarlo."
        else -> "No se pudo sincronizar por ahora. Se reintentara automaticamente."
    }
}

private fun Long.toTimestamp(): String = Instant.ofEpochMilli(this).toString()
private fun String.toEpochMillisOrZero(): Long = runCatching { Instant.parse(this).toEpochMilli() }.getOrDefault(0L)

@Serializable private data class ProductOperation(val localVersion: Long, val data: SyncProduct)
@Serializable private data class ImageUploadOperation(
    val productSyncId: String,
    val localPath: String,
    val storagePath: String
) {
    init { productSyncId.requireCloudUuid("sync_id") }
}
@Serializable private data class ImageDeleteOperation(val productSyncId: String, val storagePath: String) {
    init { productSyncId.requireCloudUuid("sync_id") }
}
@Serializable private data class CustomerOperation(val localVersion: Long, val data: SyncCustomer)
@Serializable private data class SettingsOperation(val localVersion: Long, val data: SyncBusinessSettings)
@Serializable private data class SaleBundleOperation(
    val localVersion: Long,
    val sale: SyncSale,
    val items: List<SyncSaleItem>,
    val movements: List<SyncStockMovement> = emptyList()
)
@Serializable private data class SaleTransitionOperation(val localVersion: Long, val sale: SyncSale)
@Serializable private data class SaleCancellationOperation(
    val localVersion: Long,
    val sale: SyncSale,
    val movements: List<SyncStockMovement> = emptyList()
)
@Serializable private data class SaleItemOperation(val data: SyncSaleItem)
@Serializable private data class PaymentOperation(val data: SyncCreditPayment)
@Serializable private data class StockMovementOperation(val data: SyncStockMovement)

@Serializable private data class ProductMetadataRpc(val payload: SyncProduct)
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
) {
    init { targetBusinessId.requireCloudUuid("business_id") }
}
@Serializable private data class SaleTransitionRpc(
    @SerialName("target_sale_id") val targetSaleId: String,
    @SerialName("target_business_id") val targetBusinessId: String,
    @SerialName("new_status") val newStatus: String,
    @SerialName("target_paid_at") val paidAt: String?,
    @SerialName("target_cancellation_reason") val cancellationReason: String?
) {
    init {
        targetSaleId.requireCloudUuid("sale_id")
        targetBusinessId.requireCloudUuid("business_id")
    }
}

@Serializable private data class SaleCancellationRpc(val payload: SyncSaleCancellation)

@Serializable private data class SyncSaleCancellation(
    @SerialName("sale_id") val saleId: String,
    @SerialName("business_id") val businessId: String,
    val reason: String,
    val movements: List<SyncStockMovement>
) {
    init {
        saleId.requireCloudUuid("sale_id")
        businessId.requireCloudUuid("business_id")
    }
}

@Serializable private data class SyncSaleBundle(
    val sale: SyncSale,
    val items: List<SyncSaleItem>,
    val movements: List<SyncStockMovement>
)

@Serializable private data class AtomicStockResult(
    val stocks: List<RemoteStockProjection> = emptyList()
)

@Serializable private data class RemoteStockProjection(
    @SerialName("product_id") val productId: String,
    val stock: Int
) {
    init { productId.requireCloudUuid("product_id") }
}

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
) {
    init {
        id.requireCloudUuid("sync_id")
        businessId.requireCloudUuid("business_id")
    }
}

@Serializable private data class SyncCustomer(
    val id: String,
    @SerialName("business_id") val businessId: String,
    val name: String,
    val phone: String?,
    val notes: String,
    @SerialName("deleted_at") val deletedAt: String? = null
) {
    init {
        id.requireCloudUuid("sync_id")
        businessId.requireCloudUuid("business_id")
    }
}

@Serializable private data class SyncBusinessSettings(
    @SerialName("business_id") val businessId: String,
    val currency: String,
    @SerialName("daily_goal_cents") val dailyGoalCents: Long,
    @SerialName("low_stock_enabled") val lowStockEnabled: Boolean,
    @SerialName("receipt_message") val receiptMessage: String
) {
    init { businessId.requireCloudUuid("business_id") }
}

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
) {
    init {
        id.requireCloudUuid("sync_id")
        businessId.requireCloudUuid("business_id")
        customerId?.requireCloudUuid("customer_id")
    }
}

@Serializable private data class SyncSaleItem(
    val id: String,
    @SerialName("business_id") val businessId: String,
    @SerialName("sale_id") val saleId: String,
    @SerialName("product_id") val productId: String?,
    @SerialName("product_name_snapshot") val productNameSnapshot: String,
    val quantity: Int,
    @SerialName("unit_price_cents") val unitPriceCents: Long,
    @SerialName("unit_cost_cents") val unitCostCents: Long
) {
    init {
        id.requireCloudUuid("sync_id")
        businessId.requireCloudUuid("business_id")
        saleId.requireCloudUuid("sale_id")
        productId?.requireCloudUuid("product_id")
    }
}

@Serializable private data class SyncCreditPayment(
    val id: String,
    @SerialName("business_id") val businessId: String,
    @SerialName("customer_id") val customerId: String,
    @SerialName("sale_id") val saleId: String,
    @SerialName("amount_cents") val amountCents: Long,
    @SerialName("payment_method") val paymentMethod: String,
    val notes: String,
    @SerialName("paid_at") val paidAt: String
) {
    init {
        id.requireCloudUuid("sync_id")
        businessId.requireCloudUuid("business_id")
        customerId.requireCloudUuid("customer_id")
        saleId.requireCloudUuid("sale_id")
    }
}

@Serializable private data class SyncStockMovement(
    val id: String,
    @SerialName("business_id") val businessId: String,
    @SerialName("product_id") val productId: String,
    @SerialName("sale_id") val saleId: String? = null,
    val type: String,
    @SerialName("quantity_delta") val quantityDelta: Int,
    val notes: String,
    @SerialName("created_at") val createdAt: String
) {
    init {
        id.requireCloudUuid("sync_id")
        businessId.requireCloudUuid("business_id")
        productId.requireCloudUuid("product_id")
        saleId?.requireCloudUuid("sale_id")
    }
}

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
) {
    init {
        id.requireCloudUuid("sync_id")
        businessId.requireCloudUuid("business_id")
    }
}

@Serializable private data class RemoteCustomer(
    val id: String,
    @SerialName("business_id") val businessId: String,
    val name: String,
    val phone: String? = null,
    val notes: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null
) {
    init {
        id.requireCloudUuid("sync_id")
        businessId.requireCloudUuid("business_id")
    }
}

@Serializable private data class RemoteBusinessSettings(
    val id: String,
    val currency: String = "PEN",
    @SerialName("daily_goal_cents") val dailyGoalCents: Long = 50_000,
    @SerialName("low_stock_enabled") val lowStockEnabled: Boolean = true,
    @SerialName("receipt_message") val receiptMessage: String = "",
    @SerialName("updated_at") val updatedAt: String
) {
    init { id.requireCloudUuid("business_id") }
}

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
) {
    init {
        id.requireCloudUuid("sync_id")
        businessId.requireCloudUuid("business_id")
        customerId?.requireCloudUuid("customer_id")
    }
}

@Serializable private data class RemoteSaleItem(
    val id: String,
    @SerialName("business_id") val businessId: String,
    @SerialName("sale_id") val saleId: String,
    @SerialName("product_id") val productId: String? = null,
    @SerialName("product_name_snapshot") val productNameSnapshot: String,
    val quantity: Int,
    @SerialName("unit_price_cents") val unitPriceCents: Long,
    @SerialName("unit_cost_cents") val unitCostCents: Long,
    @SerialName("created_at") val createdAt: String,
    @SerialName("server_created_at") val serverCreatedAt: String
) {
    init {
        id.requireCloudUuid("sync_id")
        businessId.requireCloudUuid("business_id")
        saleId.requireCloudUuid("sale_id")
        productId?.requireCloudUuid("product_id")
    }
}

@Serializable private data class RemoteCreditPayment(
    val id: String,
    @SerialName("business_id") val businessId: String,
    @SerialName("customer_id") val customerId: String,
    @SerialName("sale_id") val saleId: String,
    @SerialName("amount_cents") val amountCents: Long,
    @SerialName("payment_method") val paymentMethod: String,
    val notes: String,
    @SerialName("paid_at") val paidAt: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("server_created_at") val serverCreatedAt: String
) {
    init {
        id.requireCloudUuid("sync_id")
        businessId.requireCloudUuid("business_id")
        customerId.requireCloudUuid("customer_id")
        saleId.requireCloudUuid("sale_id")
    }
}

@Serializable private data class RemoteStockMovement(
    val id: String,
    @SerialName("business_id") val businessId: String,
    @SerialName("product_id") val productId: String,
    @SerialName("sale_id") val saleId: String? = null,
    val type: String,
    @SerialName("quantity_delta") val quantityDelta: Int,
    val notes: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("server_created_at") val serverCreatedAt: String
) {
    init {
        id.requireCloudUuid("sync_id")
        businessId.requireCloudUuid("business_id")
        productId.requireCloudUuid("product_id")
        saleId?.requireCloudUuid("sale_id")
    }
}
