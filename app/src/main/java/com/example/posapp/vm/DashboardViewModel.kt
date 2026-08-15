package com.example.posapp.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.posapp.data.AppDatabase
import com.example.posapp.data.ActiveBusinessStore
import com.example.posapp.data.entities.BusinessSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import java.util.*
import com.example.posapp.utils.toMoneyDouble

data class RecentSale(
    val id: Long,
    val total: Double,
    val fechaMillis: Long,
    val productName: String,
    val paymentMethod: String
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val ventaDao = database.ventaDao()
    private val clienteDao = database.clienteDao()
    private val productoDao = database.productoDao()
    private val syncDao = database.syncDao()
    private val businessId = ActiveBusinessStore(application).businessId()

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

    private val _dailyGoalCents = MutableStateFlow(50_000L)
    val dailyGoalCents: StateFlow<Long> = _dailyGoalCents.asStateFlow()

    init {
        viewModelScope.launch {
            if (businessId.isNotBlank()) {
                syncDao.insertBusinessSettingsIfMissing(
                    BusinessSettings(business_id = businessId, sync_status = com.example.posapp.data.entities.SyncStatus.SYNCED)
                )
                syncDao.observeBusinessSettings(businessId).collect { settings ->
                    _dailyGoalCents.value = settings?.daily_goal_cents ?: 50_000L
                }
            }
        }
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

                var sumHoy = 0L
                var sumAyer = 0L
                val validSales = ventas.filter { it.estado != "ANULADO" }
                val todaySales = validSales.filter { it.fecha_hora in todayStart..todayEnd }
                for (v in validSales) {
                    if (v.fecha_hora in todayStart..todayEnd) sumHoy += v.total_centavos
                    if (v.fecha_hora in ayerStart..ayerEnd) sumAyer += v.total_centavos
                }
                var deuda = 0L
                var topDeuda = 0L
                var topNombre = ""
                for (c in clientes) {
                    val monto = c.deuda_total_centavos
                    deuda += monto
                    if (monto > topDeuda) {
                        topDeuda = monto
                        topNombre = c.nombre
                    }
                }
                val productNames = productos.associate { it.id to it.nombre }
                val productCosts = productos.associate { it.id to it.precio_costo_centavos }
                val todaySaleIds = todaySales.mapTo(mutableSetOf()) { it.id }
                val estimatedProfit = detalles.asSequence()
                    .filter { it.ventaId in todaySaleIds }
                    .sumOf { detail ->
                        val cost = productCosts[detail.productoId] ?: 0L
                        Math.multiplyExact(detail.precio_unitario_centavos - cost, detail.cantidad.toLong())
                    }
                val lowStock = productos
                    .filter { it.stock <= it.stock_minimo }
                    .sortedWith(compareBy({ it.stock }, { it.nombre.lowercase(Locale.getDefault()) }))
                    .map { it.nombre }
                val recientes = validSales.sortedByDescending { it.fecha_hora }
                    .take(8)
                    .map { v ->
                        val firstDet = detalles.firstOrNull { it.ventaId == v.id }
                        val pName = firstDet?.let { productNames[it.productoId] } ?: "Venta"
                        RecentSale(
                            id = v.id,
                            total = v.total_centavos.toMoneyDouble(),
                            fechaMillis = v.fecha_hora,
                            productName = pName,
                            paymentMethod = v.tipo_pago
                        )
                    }
                DashboardSnapshot(
                    ventasHoy = sumHoy.toMoneyDouble(),
                    ventasAyer = sumAyer.toMoneyDouble(),
                    porCobrar = deuda.toMoneyDouble(),
                    gananciaHoy = estimatedProfit.toMoneyDouble(),
                    cantidadVentasHoy = todaySales.size,
                    productosStockBajo = lowStock,
                    mayorDeudor = if (topDeuda > 0) topNombre to topDeuda.toMoneyDouble() else null,
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
