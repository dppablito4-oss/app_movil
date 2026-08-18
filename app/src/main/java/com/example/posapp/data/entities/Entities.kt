package com.example.posapp.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "producto",
    indices = [
        Index(value = ["busqueda_normalizada"]),
        Index(value = ["nombre"]),
        Index(value = ["sync_id"], unique = true),
        Index(value = ["business_id", "updated_at"]),
        Index(value = ["business_id", "codigo_barras"], unique = true)
    ]
)
data class Producto(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    /** Solo se conserva para migrar respaldos antiguos. No usar en cálculos nuevos. */
    val precio_costo: Double,
    /** Solo se conserva para migrar respaldos antiguos. No usar en cálculos nuevos. */
    val precio_venta: Double,
    val stock: Int,
    /** Ruta privada en Supabase Storage; ruta_imagen sigue siendo el archivo/cache local. */
    val storage_path: String? = null,
    @ColumnInfo(defaultValue = "'NONE'") val image_sync_status: String = ImageSyncStatus.NONE,
    val ruta_imagen: String?,
    val busqueda_normalizada: String,
    val sync_id: String? = null,
    @ColumnInfo(defaultValue = "0") val precio_costo_centavos: Long = 0,
    @ColumnInfo(defaultValue = "0") val precio_venta_centavos: Long = 0,
    val codigo_barras: String? = null,
    @ColumnInfo(defaultValue = "5") val stock_minimo: Int = 5,
    @ColumnInfo(defaultValue = "''") val business_id: String = "",
    @ColumnInfo(defaultValue = "0") val created_at: Long = 0,
    @ColumnInfo(defaultValue = "0") val updated_at: Long = 0,
    val deleted_at: Long? = null,
    val remote_updated_at: Long? = null,
    @ColumnInfo(defaultValue = "'PENDING'") val sync_status: String = SyncStatus.PENDING
)

@Entity(
    tableName = "cliente",
    indices = [
        Index(value = ["sync_id"], unique = true),
        Index(value = ["business_id", "updated_at"])
    ]
)
data class Cliente(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    val telefono: String?,
    /** Solo se conserva para migrar respaldos antiguos. */
    val deuda_total: Double? = 0.0,
    val nota: String = "",
    val sync_id: String? = null,
    @ColumnInfo(defaultValue = "0") val deuda_total_centavos: Long = 0,
    @ColumnInfo(defaultValue = "''") val business_id: String = "",
    @ColumnInfo(defaultValue = "0") val created_at: Long = 0,
    @ColumnInfo(defaultValue = "0") val updated_at: Long = 0,
    val deleted_at: Long? = null,
    val remote_updated_at: Long? = null,
    @ColumnInfo(defaultValue = "'PENDING'") val sync_status: String = SyncStatus.PENDING
)

@Entity(
    tableName = "venta",
    foreignKeys = [
        ForeignKey(
            entity = Cliente::class,
            parentColumns = ["id"],
            childColumns = ["clienteId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["clienteId"]),
        Index(value = ["sync_id"], unique = true),
        Index(value = ["business_id", "fecha_hora"])
    ]
)
data class Venta(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fecha_hora: Long,
    /** Solo se conserva para migrar respaldos antiguos. */
    val total: Double,
    val tipo_pago: String,
    val clienteId: Long?,
    val estado: String = "PENDIENTE",
    val ruta_evidencia: String? = null,
    val fecha_pago: Long? = null,
    val sync_id: String? = null,
    val nube_sincronizada: Boolean = false,
    @ColumnInfo(defaultValue = "0") val total_centavos: Long = 0,
    @ColumnInfo(defaultValue = "''") val business_id: String = "",
    @ColumnInfo(defaultValue = "0") val created_at: Long = 0,
    @ColumnInfo(defaultValue = "0") val updated_at: Long = 0,
    val remote_updated_at: Long? = null,
    val job_id: String? = null,
    @ColumnInfo(defaultValue = "'PENDING'") val sync_status: String = SyncStatus.PENDING
)

@Entity(
    tableName = "detalle_venta",
    foreignKeys = [
        ForeignKey(
            entity = Venta::class,
            parentColumns = ["id"],
            childColumns = ["ventaId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Producto::class,
            parentColumns = ["id"],
            childColumns = ["productoId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["ventaId"]),
        Index(value = ["productoId"]),
        Index(value = ["sync_id"], unique = true),
        Index(value = ["business_id", "created_at"])
    ]
)
data class DetalleVenta(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ventaId: Long,
    val productoId: Long?,
    val product_sync_id_snapshot: String? = null,
    @ColumnInfo(defaultValue = "'Producto'") val product_name_snapshot: String = "Producto",
    val cantidad: Int,
    /** Solo se conserva para migrar respaldos antiguos. */
    val precio_unitario_historico: Double,
    val sync_id: String? = null,
    val nube_sincronizada: Boolean = false,
    @ColumnInfo(defaultValue = "0") val precio_unitario_centavos: Long = 0,
    @ColumnInfo(defaultValue = "0") val costo_unitario_centavos: Long = 0,
    @ColumnInfo(defaultValue = "''") val business_id: String = "",
    @ColumnInfo(defaultValue = "0") val created_at: Long = 0,
    val remote_updated_at: Long? = null,
    val job_item_id: String? = null,
    @ColumnInfo(defaultValue = "'PRODUCT'") val item_type: String = "PRODUCT",
    @ColumnInfo(defaultValue = "'PENDING'") val sync_status: String = SyncStatus.PENDING
)

@Entity(
    tableName = "pago_fiado",
    foreignKeys = [
        ForeignKey(
            entity = Venta::class,
            parentColumns = ["id"],
            childColumns = ["ventaId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = DetalleVenta::class,
            parentColumns = ["id"],
            childColumns = ["detalleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["ventaId"]),
        Index(value = ["detalleId"]),
        Index(value = ["sync_id"], unique = true),
        Index(value = ["business_id", "created_at"])
    ]
)
data class PagoFiado(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ventaId: Long,
    val detalleId: Long?,
    /** Solo se conserva para migrar respaldos antiguos. */
    val monto: Double,
    val fecha_hora: Long,
    val sync_id: String? = null,
    val nube_sincronizada: Boolean = false,
    @ColumnInfo(defaultValue = "0") val monto_centavos: Long = 0,
    @ColumnInfo(defaultValue = "'EFECTIVO'") val metodo_pago: String = "EFECTIVO",
    @ColumnInfo(defaultValue = "''") val nota: String = "",
    @ColumnInfo(defaultValue = "''") val business_id: String = "",
    @ColumnInfo(defaultValue = "0") val created_at: Long = 0,
    val remote_updated_at: Long? = null,
    @ColumnInfo(defaultValue = "'PENDING'") val sync_status: String = SyncStatus.PENDING
)

@Entity(
    tableName = "stock_movement",
    foreignKeys = [
        ForeignKey(
            entity = Producto::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.NO_ACTION
        ),
        ForeignKey(
            entity = Venta::class,
            parentColumns = ["id"],
            childColumns = ["saleId"],
            onDelete = ForeignKey.NO_ACTION
        )
    ],
    indices = [
        Index(value = ["productId"]),
        Index(value = ["saleId"]),
        Index(value = ["sync_id"], unique = true),
        Index(value = ["business_id", "created_at"])
    ]
)
data class StockMovement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val saleId: Long? = null,
    val type: String,
    val quantity_delta: Int,
    val notes: String = "",
    val sync_id: String,
    val business_id: String,
    val created_at: Long,
    val remote_created_at: Long? = null,
    @ColumnInfo(defaultValue = "'PENDING'") val sync_status: String = SyncStatus.PENDING
)

// ---------------------------------------------------------------------------
// ENTIDADES: TRABAJOS, SERVICIOS Y CÓDIGOS QR FÍSICOS
// ---------------------------------------------------------------------------

object JobStatus {
    const val RECEIVED = "received"
    const val IN_PROGRESS = "in_progress"
    const val READY = "ready"
    const val DELIVERED = "delivered"
    const val CANCELLED = "cancelled"
}

object QrTokenStatus {
    const val UNUSED = "unused"
    const val ASSIGNED = "assigned"
    const val DISABLED = "disabled"
}

object ItemType {
    const val PRODUCT = "PRODUCT"
    const val SERVICE = "SERVICE"
    const val JOB = "JOB"
}

@Entity(
    tableName = "job",
    foreignKeys = [
        ForeignKey(
            entity = Cliente::class,
            parentColumns = ["id"],
            childColumns = ["clienteId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = Venta::class,
            parentColumns = ["id"],
            childColumns = ["ventaId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["clienteId"]),
        Index(value = ["ventaId"]),
        Index(value = ["sync_id"], unique = true),
        Index(value = ["business_id", "status", "created_at"]),
        Index(value = ["business_id", "updated_at"])
    ]
)
data class Job(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "''") val business_id: String = "",
    val sync_id: String? = null,
    val clienteId: Long? = null,
    val customer_sync_id: String? = null,
    val customer_name_snapshot: String,
    val customer_phone_snapshot: String? = null,
    val description: String? = null,
    @ColumnInfo(defaultValue = "'received'") val status: String = JobStatus.RECEIVED,
    @ColumnInfo(defaultValue = "0") val total_cents: Long = 0,
    @ColumnInfo(defaultValue = "''") val notes: String = "",
    val ventaId: Long? = null,
    val sale_id: String? = null,
    val ready_at: Long? = null,
    val delivered_at: Long? = null,
    val created_by: String? = null,
    @ColumnInfo(defaultValue = "0") val created_at: Long = 0,
    @ColumnInfo(defaultValue = "0") val updated_at: Long = 0,
    val deleted_at: Long? = null,
    val remote_updated_at: Long? = null,
    @ColumnInfo(defaultValue = "'PENDING'") val sync_status: String = SyncStatus.PENDING
)

@Entity(
    tableName = "job_item",
    foreignKeys = [
        ForeignKey(
            entity = Job::class,
            parentColumns = ["id"],
            childColumns = ["jobId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["jobId"]),
        Index(value = ["sync_id"], unique = true),
        Index(value = ["business_id", "created_at"])
    ]
)
data class JobItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "''") val business_id: String = "",
    val sync_id: String? = null,
    val jobId: Long,
    val job_sync_id: String? = null,
    @ColumnInfo(defaultValue = "'GENERAL'") val service_type: String = "GENERAL",
    val description: String,
    val paper_size: String? = null,
    val color_mode: String? = null,
    val side_mode: String? = null,
    @ColumnInfo(defaultValue = "1") val quantity: Int = 1,
    @ColumnInfo(defaultValue = "1") val pages: Int = 1,
    @ColumnInfo(defaultValue = "1") val copies: Int = 1,
    @ColumnInfo(defaultValue = "0") val unit_price_cents: Long = 0,
    @ColumnInfo(defaultValue = "0") val subtotal_cents: Long = 0,
    @ColumnInfo(defaultValue = "''") val notes: String = "",
    @ColumnInfo(defaultValue = "0") val created_at: Long = 0,
    val remote_updated_at: Long? = null,
    @ColumnInfo(defaultValue = "'PENDING'") val sync_status: String = SyncStatus.PENDING
)

@Entity(
    tableName = "qr_token",
    foreignKeys = [
        ForeignKey(
            entity = Job::class,
            parentColumns = ["id"],
            childColumns = ["jobId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["token"], unique = true),
        Index(value = ["sync_id"], unique = true),
        Index(value = ["jobId"]),
        Index(value = ["business_id", "status"])
    ]
)
data class QrToken(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "''") val business_id: String = "",
    val sync_id: String? = null,
    val token: String,
    @ColumnInfo(defaultValue = "'unused'") val status: String = QrTokenStatus.UNUSED,
    val jobId: Long? = null,
    val job_sync_id: String? = null,
    val batch_id: String? = null,
    val assigned_at: Long? = null,
    val released_at: Long? = null,
    @ColumnInfo(defaultValue = "0") val created_at: Long = 0,
    @ColumnInfo(defaultValue = "0") val updated_at: Long = 0,
    val remote_updated_at: Long? = null,
    @ColumnInfo(defaultValue = "'PENDING'") val sync_status: String = SyncStatus.PENDING
)

@Entity(
    tableName = "qr_batch",
    indices = [
        Index(value = ["sync_id"], unique = true),
        Index(value = ["business_id", "created_at"])
    ]
)
data class QrBatch(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "''") val business_id: String = "",
    val sync_id: String? = null,
    @ColumnInfo(defaultValue = "0") val quantity: Int = 0,
    val created_by: String? = null,
    @ColumnInfo(defaultValue = "0") val created_at: Long = 0
)

object SyncStatus {
    const val PENDING = "PENDING"
    const val SYNCED = "SYNCED"
    const val ERROR = "ERROR"
}

object ImageSyncStatus {
    const val NONE = "NONE"
    const val LOCAL_PENDING = "LOCAL_PENDING"
    const val UPLOADING = "UPLOADING"
    const val SYNCED = "SYNCED"
    const val DOWNLOAD_PENDING = "DOWNLOAD_PENDING"
    const val ERROR_RETRYABLE = "ERROR_RETRYABLE"
    const val ERROR_MISSING_FILE = "ERROR_MISSING_FILE"
}

@Entity(
    tableName = "sync_queue",
    indices = [
        Index(value = ["operation_id"], unique = true),
        Index(value = ["business_id", "next_attempt_at"]),
        Index(value = ["entity_type", "entity_sync_id"])
    ]
)
data class SyncQueueItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val operation_id: String,
    val business_id: String,
    val entity_type: String,
    val entity_sync_id: String,
    val operation: String,
    val payload_json: String,
    @ColumnInfo(defaultValue = "0") val attempt_count: Int = 0,
    @ColumnInfo(defaultValue = "0") val next_attempt_at: Long = 0,
    val last_error: String? = null,
    val created_at: Long
)

@Entity(tableName = "sync_metadata", primaryKeys = ["business_id", "entity_type"])
data class SyncMetadata(
    val business_id: String,
    val entity_type: String,
    @ColumnInfo(defaultValue = "'1970-01-01T00:00:00Z'")
    val last_server_timestamp: String = "1970-01-01T00:00:00Z",
    @ColumnInfo(defaultValue = "''") val last_remote_id: String = "",
    @ColumnInfo(defaultValue = "0") val last_success_at: Long = 0
)

@Entity(tableName = "business_settings")
data class BusinessSettings(
    @PrimaryKey val business_id: String,
    @ColumnInfo(defaultValue = "'PEN'") val currency: String = "PEN",
    @ColumnInfo(defaultValue = "50000") val daily_goal_cents: Long = 50_000,
    @ColumnInfo(defaultValue = "1") val low_stock_enabled: Boolean = true,
    @ColumnInfo(defaultValue = "''") val receipt_message: String = "",
    @ColumnInfo(defaultValue = "0") val updated_at: Long = 0,
    @ColumnInfo(defaultValue = "'PENDING'") val sync_status: String = SyncStatus.PENDING
)
