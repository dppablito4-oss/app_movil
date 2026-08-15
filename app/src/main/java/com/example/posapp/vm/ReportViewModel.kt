package com.example.posapp.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.posapp.data.AppDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar

enum class ReportPeriod { TODAY, WEEK, MONTH }

data class ReportUiState(
    val period: ReportPeriod = ReportPeriod.TODAY,
    val salesCents: Long = 0,
    val estimatedProfitCents: Long = 0,
    val creditCreatedCents: Long = 0,
    val recoveredCents: Long = 0,
    val saleCount: Int = 0,
    val paymentMethods: List<Pair<String, Long>> = emptyList(),
    val topProducts: List<Pair<String, Int>> = emptyList()
)

class ReportViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val period = MutableStateFlow(ReportPeriod.TODAY)

    val state: StateFlow<ReportUiState> = combine(
        period,
        database.ventaDao().observeAllVentas(),
        database.ventaDao().observeAllDetalles(),
        database.ventaDao().observeAllPagos(),
        database.productoDao().getAll()
    ) { selectedPeriod, sales, details, payments, products ->
        val start = periodStart(selectedPeriod)
        val selectedSales = sales.filter { it.estado != "ANULADO" && it.fecha_hora >= start }
        val saleIds = selectedSales.mapTo(mutableSetOf()) { it.id }
        val selectedDetails = details.filter { it.ventaId in saleIds }
        val productNames = products.associate { it.id to it.nombre }
        ReportUiState(
            period = selectedPeriod,
            salesCents = selectedSales.sumOf { it.total_centavos },
            estimatedProfitCents = selectedDetails.sumOf {
                Math.multiplyExact(it.precio_unitario_centavos - it.costo_unitario_centavos, it.cantidad.toLong())
            },
            creditCreatedCents = selectedSales.filter { it.tipo_pago == "FIADO" }.sumOf { it.total_centavos },
            recoveredCents = payments.filter { it.fecha_hora >= start }.sumOf { it.monto_centavos },
            saleCount = selectedSales.size,
            paymentMethods = selectedSales.groupBy { it.tipo_pago }
                .mapValues { (_, rows) -> rows.sumOf { it.total_centavos } }
                .toList().sortedByDescending { it.second },
            topProducts = selectedDetails.groupBy { it.productoId }
                .map { (id, rows) -> (productNames[id] ?: "Producto") to rows.sumOf { it.cantidad } }
                .sortedByDescending { it.second }.take(5)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportUiState())

    fun setPeriod(value: ReportPeriod) { period.value = value }

    private fun periodStart(value: ReportPeriod): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        when (value) {
            ReportPeriod.TODAY -> Unit
            ReportPeriod.WEEK -> set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
            ReportPeriod.MONTH -> set(Calendar.DAY_OF_MONTH, 1)
        }
    }.timeInMillis
}
