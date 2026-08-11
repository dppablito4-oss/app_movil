package com.example.posapp.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.posapp.data.AppDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import java.util.*
import kotlin.math.round

data class RecentSale(val id: Long, val total: Double, val fechaMillis: Long, val productName: String)

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

    init {
        viewModelScope.launch {
            try {
                val ventas: List<com.example.posapp.data.entities.Venta> = ventaDao.getAllVentas()
                // Sum ventas of today (fecha_hora stored as epoch millis)
                val cal = Calendar.getInstance()
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
                for (v in ventas) {
                    if (v.fecha_hora in todayStart..todayEnd) sumHoy += v.total
                    if (v.fecha_hora in ayerStart..ayerEnd) sumAyer += v.total
                }
                _ventasHoy.value = round(sumHoy * 100) / 100.0
                _ventasAyer.value = round(sumAyer * 100) / 100.0

                val clientes: List<com.example.posapp.data.entities.Cliente> = clienteDao.getAll().first()
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
                _porCobrar.value = round(deuda * 100) / 100.0
                _mayorDeudor.value = if (topDeuda > 0) topNombre to round(topDeuda * 100) / 100.0 else null

                val detalles = ventaDao.getAllDetalles()
                val recientes = ventas.sortedByDescending { it.fecha_hora }
                    .take(8)
                    .map { v ->
                        val firstDet = detalles.firstOrNull { it.ventaId == v.id }
                        val pName = firstDet?.let { d -> productoDao.getById(d.productoId)?.nombre } ?: "Venta"
                        RecentSale(id = v.id, total = v.total, fechaMillis = v.fecha_hora, productName = pName)
                    }
                _recentSales.value = recientes
            } catch (e: Exception) {
                _ventasHoy.value = 0.0
                _porCobrar.value = 0.0
                _recentSales.value = emptyList()
                _ventasAyer.value = 0.0
                _mayorDeudor.value = null
            }
        }
    }
}
