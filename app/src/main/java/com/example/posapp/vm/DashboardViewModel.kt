package com.example.posapp.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.posapp.data.AppDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import java.util.*
import kotlin.math.round

data class RecentSale(
    val id: Long,
    val total: Double,
    val fechaMillis: Long,
    val productName: String,
    val paymentMethod: String
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val ventaDao = AppDatabase.getInstance(application).ventaDao()
    private val clienteDao = AppDatabase.getInstance(application).clienteDao()
    private val productoDao = AppDatabase.getInstance(application).productoDao()

    private val _ventasHoy = MutableStateFlow(0.0)
    val ventasHoy: StateFlow<Double> = _ventasHoy.asStateFlow()

    private val _porCobrar = MutableStateFlow(0.0)
    val porCobrar: StateFlow<Double> = _porCobrar.asStateFlow()

    private val _recentSales = MutableStateFlow<List<RecentSale>>(emptyList())
    val recentSales: StateFlow<List<RecentSale>> = _recentSales.asStateFlow()

    private val _ventasAyer = MutableStateFlow(0.0)
    val ventasAyer: StateFlow<Double> = _ventasAyer.asStateFlow()

    private val _mayorDeudor = MutableStateFlow<Pair<String, Double>?>(null)
    val mayorDeudor: StateFlow<Pair<String, Double>?> = _mayorDeudor.asStateFlow()

    private val _gananciaHoy = MutableStateFlow(0.0)
    val gananciaHoy: StateFlow<Double> = _gananciaHoy.asStateFlow()

    private val _cantidadVentasHoy = MutableStateFlow(0)
    val cantidadVentasHoy: StateFlow<Int> = _cantidadVentasHoy.asStateFlow()

    private val _productosStockBajo = MutableStateFlow<List<String>>(emptyList())
    val productosStockBajo: StateFlow<List<String>> = _productosStockBajo.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                ventaDao.observeAllVentas(),
                clienteDao.getAll(),
                ventaDao.observeAllDetalles(),
                productoDao.getAll()
            ) { ventas, clientes, detalles, productos ->
                val todayStart = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val todayEnd = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
                }.timeInMillis
                val ayerStart = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, -1)
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val ayerEnd = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, -1)
                    set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
                }.timeInMillis

                var sumHoy = 0.0
                var sumAyer = 0.0
                val validSales = ventas.filter { it.estado != "ANULADO" }
                val todaySales = validSales.filter { it.fecha_hora in todayStart..todayEnd }
                for (v in validSales) {
                    if (v.fecha_hora in todayStart..todayEnd) sumHoy += v.total
                    if (v.fecha_hora in ayerStart..ayerEnd) sumAyer += v.total
                }
                var deuda = 0.0
                var topDeuda = 0.0
                var topNombre = ""
                for (c in clientes) {
                    val monto = c.deuda_total ?: 0.0
                    deuda += monto
                    if (monto > topDeuda) {
                        topDeuda = monto
                        topNombre = c.nombre
                    }
                }
                val productNames = productos.associate { it.id to it.nombre }
                val productCosts = productos.associate { it.id to it.precio_costo }
                val todaySaleIds = todaySales.mapTo(mutableSetOf()) { it.id }
                val estimatedProfit = detalles.asSequence()
                    .filter { it.ventaId in todaySaleIds }
                    .sumOf { detail ->
                        val cost = productCosts[detail.productoId] ?: 0.0
                        (detail.precio_unitario_historico - cost) * detail.cantidad
                    }
                val lowStock = productos
                    .filter { it.stock <= 5 }
                    .sortedWith(compareBy({ it.stock }, { it.nombre.lowercase(Locale.getDefault()) }))
                    .map { it.nombre }
                val recientes = validSales.sortedByDescending { it.fecha_hora }
                    .take(8)
                    .map { v ->
                        val firstDet = detalles.firstOrNull { it.ventaId == v.id }
                        val pName = firstDet?.let { productNames[it.productoId] } ?: "Venta"
                        RecentSale(
                            id = v.id,
                            total = v.total,
                            fechaMillis = v.fecha_hora,
                            productName = pName,
                            paymentMethod = v.tipo_pago
                        )
                    }
                DashboardSnapshot(
                    ventasHoy = round(sumHoy * 100) / 100.0,
                    ventasAyer = round(sumAyer * 100) / 100.0,
                    porCobrar = round(deuda * 100) / 100.0,
                    gananciaHoy = round(estimatedProfit * 100) / 100.0,
                    cantidadVentasHoy = todaySales.size,
                    productosStockBajo = lowStock,
                    mayorDeudor = if (topDeuda > 0) topNombre to round(topDeuda * 100) / 100.0 else null,
                    recentSales = recientes
                )
            }.collect { snapshot ->
                _ventasHoy.value = snapshot.ventasHoy
                _ventasAyer.value = snapshot.ventasAyer
                _porCobrar.value = snapshot.porCobrar
                _mayorDeudor.value = snapshot.mayorDeudor
                _recentSales.value = snapshot.recentSales
                _gananciaHoy.value = snapshot.gananciaHoy
                _cantidadVentasHoy.value = snapshot.cantidadVentasHoy
                _productosStockBajo.value = snapshot.productosStockBajo
            }
        }
    }
}

private data class DashboardSnapshot(
    val ventasHoy: Double,
    val ventasAyer: Double,
    val porCobrar: Double,
    val gananciaHoy: Double,
    val cantidadVentasHoy: Int,
    val productosStockBajo: List<String>,
    val mayorDeudor: Pair<String, Double>?,
    val recentSales: List<RecentSale>
)
