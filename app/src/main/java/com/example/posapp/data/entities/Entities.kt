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
        Index(value = ["business_id", "updated_at"])
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
    @ColumnInfo(defaultValue = "'SYNCED'") val image_sync_status: String = SyncStatus.SYNCED,
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
            onDelete = ForeignKey.NO_ACTION
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
    val productoId: Long,
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

object SyncStatus {
    const val PENDING = "PENDING"
    const val SYNCED = "SYNCED"
    const val ERROR = "ERROR"
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
    @ColumnInfo(defaultValue = "0") val last_pulled_at: Long = 0,
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
