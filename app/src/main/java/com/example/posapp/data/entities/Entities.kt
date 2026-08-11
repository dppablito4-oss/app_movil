package com.example.posapp.data.entities

import androidx.room.*

@Entity(
    tableName = "producto",
    indices = [Index(value = ["busqueda_normalizada"]), Index(value = ["nombre"]), Index(value = ["sync_id"], unique = true)])
data class Producto(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    val precio_costo: Double,
    val precio_venta: Double,
    val stock: Int,
    val ruta_imagen: String?,
    val busqueda_normalizada: String,
    /** UUID estable usado por Supabase. El id numérico sigue siendo solo de Room. */
    val sync_id: String? = null
)

@Entity(tableName = "cliente", indices = [Index(value = ["sync_id"], unique = true)])
data class Cliente(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    val telefono: String?,
    val deuda_total: Double? = 0.0,
    val nota: String = "",
    val sync_id: String? = null
)

@Entity(
    tableName = "venta",
    foreignKeys = [ForeignKey(entity = Cliente::class, parentColumns = ["id"], childColumns = ["clienteId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index(value = ["clienteId"]), Index(value = ["sync_id"], unique = true)]
)
data class Venta(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fecha_hora: Long,
    val total: Double,
    val tipo_pago: String,
    val clienteId: Long?,
    val estado: String = "PENDIENTE",
    val ruta_evidencia: String? = null,
    val fecha_pago: Long? = null,
    val sync_id: String? = null,
    val nube_sincronizada: Boolean = false
)

@Entity(
    tableName = "detalle_venta",
    foreignKeys = [
        ForeignKey(entity = Venta::class, parentColumns = ["id"], childColumns = ["ventaId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Producto::class, parentColumns = ["id"], childColumns = ["productoId"], onDelete = ForeignKey.NO_ACTION)
    ],
    indices = [Index(value = ["ventaId"]), Index(value = ["productoId"]), Index(value = ["sync_id"], unique = true)])
data class DetalleVenta(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ventaId: Long,
    val productoId: Long,
    val cantidad: Int,
    val precio_unitario_historico: Double,
    val sync_id: String? = null,
    val nube_sincronizada: Boolean = false
)

@Entity(
    tableName = "pago_fiado",
    foreignKeys = [
        ForeignKey(entity = Venta::class, parentColumns = ["id"], childColumns = ["ventaId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = DetalleVenta::class, parentColumns = ["id"], childColumns = ["detalleId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["ventaId"]), Index(value = ["detalleId"], unique = true), Index(value = ["sync_id"], unique = true)]
)
data class PagoFiado(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ventaId: Long,
    val detalleId: Long,
    val monto: Double,
    val fecha_hora: Long,
    val sync_id: String? = null,
    val nube_sincronizada: Boolean = false
)
