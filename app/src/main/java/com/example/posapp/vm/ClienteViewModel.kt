package com.example.posapp.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.posapp.data.AppDatabase
import com.example.posapp.data.ActiveBusinessStore
import com.example.posapp.data.entities.Cliente
import com.example.posapp.data.entities.SyncStatus
import com.example.posapp.data.entities.Venta
import com.example.posapp.data.repository.SalesRepository
import com.example.posapp.data.sync.CloudSyncScheduler
import com.example.posapp.utils.toCents
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class ClienteViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val clienteDao = database.clienteDao()
    private val ventaDao = database.ventaDao()
    private val productoDao = database.productoDao()
    private val salesRepository = SalesRepository(database)
    private val activeBusiness = ActiveBusinessStore(application)

    private val _clientes = MutableStateFlow<List<Cliente>>(emptyList())
    val clientes: StateFlow<List<Cliente>> = _clientes.asStateFlow()

    private val _deudores = MutableStateFlow<List<Cliente>>(emptyList())
    val deudores: StateFlow<List<Cliente>> = _deudores.asStateFlow()

    private val _fiadosState = MutableStateFlow(FiadosUiState())
    val fiadosState: StateFlow<FiadosUiState> = _fiadosState.asStateFlow()

    private val _debtDetailState = MutableStateFlow(DebtDetailUiState())
    val debtDetailState: StateFlow<DebtDetailUiState> = _debtDetailState.asStateFlow()

    init {
        viewModelScope.launch {
            clienteDao.getAll().collect { list -> _clientes.value = list }
        }
        viewModelScope.launch {
            clienteDao.obtenerDeudores().collect { list -> _deudores.value = list }
        }
        viewModelScope.launch {
            combine(
                ventaDao.observeAllVentas(),
                ventaDao.observeAllDetalles(),
                productoDao.getAll(),
                clienteDao.getAll()
            ) { sales, details, products, clients ->
                val productsById = products.associateBy { it.id }
                val clientsById = clients.associateBy { it.id }
                val recent = sales.asSequence()
                    .filter { it.tipo_pago == "FIADO" && it.estado != "ANULADO" }
                    .flatMap { sale ->
                        details.asSequence().filter { it.ventaId == sale.id }.map { detail ->
                            RecentCreditMove(
                                id = "${sale.sync_id ?: sale.id}-${detail.sync_id ?: detail.id}",
                                timestamp = sale.fecha_hora,
                                clientName = clientsById[sale.clienteId]?.nombre ?: "Cliente",
                                productName = productsById[detail.productoId]?.nombre ?: "Producto",
                                amountCents = Math.multiplyExact(
                                    detail.precio_unitario_centavos,
                                    detail.cantidad.toLong()
                                )
                            )
                        }
                    }
                    .sortedByDescending { it.timestamp }
                    .take(50)
                    .toList()
                FiadosUiState(isLoading = false, recentMoves = recent)
            }.collect { _fiadosState.value = it }
        }
    }

    fun addCliente(nombre: String, telefono: String = "", nota: String = "", deudaInicial: Double = 0.0, onComplete: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val id = clienteDao.insert(
                Cliente(
                    nombre = nombre,
                    telefono = telefono,
                    deuda_total = 0.0,
                    deuda_total_centavos = deudaInicial.toCents(),
                    nota = nota,
                    business_id = activeBusiness.businessId(),
                    created_at = now,
                    updated_at = now,
                    sync_status = SyncStatus.PENDING
                )
            )
            CloudSyncScheduler.schedule(getApplication<Application>().applicationContext)
            onComplete(id)
        }
    }

    fun updateCliente(cliente: com.example.posapp.data.entities.Cliente, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            clienteDao.update(
                cliente.copy(
                    updated_at = System.currentTimeMillis(),
                    sync_status = SyncStatus.PENDING
                )
            )
            CloudSyncScheduler.schedule(getApplication<Application>().applicationContext)
            onComplete()
        }
    }

    fun deleteCliente(cliente: Cliente, onComplete: (String?) -> Unit = {}) {
        viewModelScope.launch {
            val error = runCatching {
                salesRepository.deleteClient(cliente)
                CloudSyncScheduler.schedule(getApplication<Application>().applicationContext)
            }.exceptionOrNull()?.message
            onComplete(error)
        }
    }

    fun loadDebtDetails(clientId: Long) {
        viewModelScope.launch {
            _debtDetailState.value = DebtDetailUiState(clientId = clientId, isLoading = true)
            runCatching {
                val client = clienteDao.getById(clientId)
                    ?: throw IllegalStateException("El cliente ya no existe")
                val pendingSales = ventaDao.getAllVentas()
                    .filter { it.clienteId == clientId && it.estado == "PENDIENTE" }
                val products = productoDao.getAllForSync().associateBy { it.id }
                val details = ventaDao.getDetallesPendientesCliente(clientId)
                val allDetails = ventaDao.getAllDetalles().associateBy { it.id }
                val payments = ventaDao.getPaymentsForClient(clientId).map { payment ->
                    val detail = payment.detalleId?.let(allDetails::get)
                    DebtPaymentHistory(
                        id = payment.sync_id ?: payment.id.toString(),
                        paidAt = payment.fecha_hora,
                        amountCents = payment.monto_centavos,
                        method = payment.metodo_pago,
                        note = payment.nota,
                        productName = detail?.let { products[it.productoId]?.nombre }
                    )
                }
                val groups = pendingSales.mapNotNull { sale ->
                    val lines = details.filter { it.ventaId == sale.id }.map { detail ->
                        val lineTotal = Math.multiplyExact(
                            detail.precio_unitario_centavos,
                            detail.cantidad.toLong()
                        )
                        val paid = ventaDao.getTotalPagadoDetalle(detail.id)
                        DebtLine(
                            detailId = detail.id,
                            productName = products[detail.productoId]?.nombre ?: "Producto",
                            quantity = detail.cantidad,
                            pendingCents = (lineTotal - paid).coerceAtLeast(0)
                        )
                    }.filter { it.pendingCents > 0 }
                    if (lines.isEmpty()) null else DebtSaleGroup(
                        saleId = sale.id,
                        soldAt = sale.fecha_hora,
                        lines = lines,
                        pendingCents = lines.sumOf { it.pendingCents }
                    )
                }
                DebtDetailUiState(
                    clientId = clientId,
                    clientName = client.nombre,
                    groups = groups,
                    payments = payments,
                    isLoading = false
                )
            }.onSuccess { _debtDetailState.value = it }
                .onFailure { error ->
                    _debtDetailState.value = DebtDetailUiState(
                        clientId = clientId,
                        isLoading = false,
                        errorMessage = error.message ?: "No se pudo cargar la deuda"
                    )
                }
        }
    }

    fun payDebtLines(clientId: Long, detailIds: Set<Long>, onComplete: (String?) -> Unit = {}) {
        viewModelScope.launch {
            val error = runCatching {
                salesRepository.payDetails(clientId, detailIds)
                CloudSyncScheduler.schedule(getApplication<Application>().applicationContext)
                loadDebtDetails(clientId)
            }.exceptionOrNull()?.message
            onComplete(error)
        }
    }

    fun payDebtAmount(
        clientId: Long,
        detailIds: Set<Long>,
        amount: Double,
        method: String,
        note: String,
        onComplete: (String?) -> Unit = {}
    ) {
        viewModelScope.launch {
            val error = runCatching {
                salesRepository.payAmount(clientId, detailIds, amount.toCents(), method, note)
                CloudSyncScheduler.schedule(getApplication<Application>().applicationContext)
                loadDebtDetails(clientId)
            }.exceptionOrNull()?.message
            onComplete(error)
        }
    }
}

data class RecentCreditMove(
    val id: String,
    val timestamp: Long,
    val clientName: String,
    val productName: String,
    val amountCents: Long
)

data class FiadosUiState(
    val isLoading: Boolean = true,
    val recentMoves: List<RecentCreditMove> = emptyList(),
    val errorMessage: String? = null
)

data class DebtLine(
    val detailId: Long,
    val productName: String,
    val quantity: Int,
    val pendingCents: Long
)

data class DebtSaleGroup(
    val saleId: Long,
    val soldAt: Long,
    val lines: List<DebtLine>,
    val pendingCents: Long
)

data class DebtDetailUiState(
    val clientId: Long? = null,
    val clientName: String = "",
    val groups: List<DebtSaleGroup> = emptyList(),
    val payments: List<DebtPaymentHistory> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

data class DebtPaymentHistory(
    val id: String,
    val paidAt: Long,
    val amountCents: Long,
    val method: String,
    val note: String,
    val productName: String?
)
