package com.example.posapp.data.entities

import androidx.room.*

@Entity(
    tableName = "producto",
    indices = [Index(value = ["busqueda_normalizada"]), Index(value = ["nombre"])])
data class Producto(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    val precio_costo: Double,
    val precio_venta: Double,
    val stock: Int,
    val ruta_imagen: String?,
    val busqueda_normalizada: String
)

@Entity(tableName = "cliente")
data class Cliente(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    val telefono: String?,
    // Recomiendo calcular deuda con consultas; guardamos como ayuda si se desea
    val deuda_total: Double? = 0.0
)

@Entity(
    tableName = "venta",
    foreignKeys = [ForeignKey(entity = Cliente::class, parentColumns = ["id"], childColumns = ["clienteId"], onDelete = ForeignKey.SET_NULL)])
data class Venta(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fecha_hora: Long,
    val total: Double,
    val tipo_pago: String,
    val clienteId: Long?,
    val estado: String = "PENDIENTE",
    val ruta_evidencia: String? = null,
    val fecha_pago: Long? = null
)

@Entity(
    tableName = "detalle_venta",
    foreignKeys = [
        ForeignKey(entity = Venta::class, parentColumns = ["id"], childColumns = ["ventaId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Producto::class, parentColumns = ["id"], childColumns = ["productoId"], onDelete = ForeignKey.NO_ACTION)
    ],
    indices = [Index(value = ["ventaId"]), Index(value = ["productoId"])])
data class DetalleVenta(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ventaId: Long,
    val productoId: Long,
    val cantidad: Int,
    val precio_unitario_historico: Double
)
