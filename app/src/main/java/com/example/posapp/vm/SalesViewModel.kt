package com.example.posapp.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.posapp.data.AppDatabase
import com.example.posapp.data.ActiveBusinessStore
import com.example.posapp.data.entities.Producto
import com.example.posapp.data.repository.SaleLine
import com.example.posapp.data.repository.SalesRepository
import com.example.posapp.data.sync.CloudSyncScheduler
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

data class CartItem(val producto: Producto, val cantidad: Int = 1)
data class SaleReceiptLine(val name: String, val quantity: Int, val unitPriceCents: Long)
data class SaleReceipt(
    val saleId: Long,
    val createdAt: Long,
    val paymentMethod: String,
    val totalCents: Long,
    val lines: List<SaleReceiptLine>
)

class SalesViewModel(application: Application) : AndroidViewModel(application) {
    private val productoDao = AppDatabase.getInstance(application).productoDao()
    private val repository = SalesRepository(AppDatabase.getInstance(application))
    private val activeBusiness = ActiveBusinessStore(application)

    private val _searchResults = MutableStateFlow<List<Producto>>(emptyList())
    val searchResults: StateFlow<List<Producto>> = _searchResults.asStateFlow()

    private val _cart = MutableStateFlow<List<CartItem>>(emptyList())
    val cart: StateFlow<List<CartItem>> = _cart.asStateFlow()

    private var searchJob: Job? = null
    private val checkoutLock = AtomicBoolean(false)
    private val _isCheckoutInProgress = MutableStateFlow(false)
    val isCheckoutInProgress: StateFlow<Boolean> = _isCheckoutInProgress.asStateFlow()
    private val _lastReceipt = MutableStateFlow<SaleReceipt?>(null)
    val lastReceipt: StateFlow<SaleReceipt?> = _lastReceipt.asStateFlow()

    fun receiptForShare(): SaleReceipt? = _lastReceipt.value

    init {
        search("")
    }

    fun search(query: String) {
        val trimmed = query.trim()
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val flow = if (trimmed.isEmpty()) {
                productoDao.getAll()
            } else {
                productoDao.searchProductos(trimmed.uppercase(Locale.ROOT))
            }
            flow.collect { list -> _searchResults.value = list }
        }
    }

    fun addToCart(producto: Producto): Boolean {
        val existing = _cart.value.find { it.producto.id == producto.id }
        if ((existing?.cantidad ?: 0) >= producto.stock) return false
        _cart.value = if (existing == null) {
            _cart.value + CartItem(producto, 1)
        } else {
            _cart.value.map { if (it.producto.id == producto.id) it.copy(cantidad = it.cantidad + 1) else it }
        }
        return true
    }

    suspend fun addScannedBarcode(barcode: String): BarcodeAddResult {
        val product = productoDao.getByBarcode(activeBusiness.businessId(), barcode)
            ?: return BarcodeAddResult.NOT_FOUND
        return if (addToCart(product)) BarcodeAddResult.ADDED else BarcodeAddResult.OUT_OF_STOCK
    }

    fun incQuantity(productId: Long): Boolean {
        val item = _cart.value.find { it.producto.id == productId } ?: return false
        if (item.cantidad >= item.producto.stock) return false
        _cart.value = _cart.value.map { if (it.producto.id == productId) it.copy(cantidad = it.cantidad + 1) else it }
        return true
    }

    fun decQuantity(productId: Long) {
        _cart.value = _cart.value.mapNotNull { item ->
            if (item.producto.id != productId) item
            else item.copy(cantidad = item.cantidad - 1).takeIf { it.cantidad > 0 }
        }
    }

    fun totalCents(): Long = _cart.value.sumOf {
        Math.multiplyExact(it.producto.precio_venta_centavos, it.cantidad.toLong())
    }

    fun checkout(tipoPago: String, clienteId: Long? = null, onComplete: (Boolean, String?) -> Unit) {
        if (!checkoutLock.compareAndSet(false, true)) {
            onComplete(false, "La venta ya se esta procesando")
            return
        }
        _isCheckoutInProgress.value = true
        viewModelScope.launch {
            try {
                val snapshot = _cart.value
                val saleId = repository.checkout(
                    lines = snapshot.map { SaleLine(it.producto, it.cantidad) },
                    tipoPago = tipoPago,
                    clienteId = clienteId
                )
                _lastReceipt.value = SaleReceipt(
                    saleId = saleId,
                    createdAt = System.currentTimeMillis(),
                    paymentMethod = tipoPago,
                    totalCents = snapshot.sumOf {
                        Math.multiplyExact(it.producto.precio_venta_centavos, it.cantidad.toLong())
                    },
                    lines = snapshot.map {
                        SaleReceiptLine(it.producto.nombre, it.cantidad, it.producto.precio_venta_centavos)
                    }
                )
                CloudSyncScheduler.schedule(getApplication<Application>().applicationContext)
                _cart.value = emptyList()
                onComplete(true, null)
            } catch (e: Exception) {
                onComplete(false, e.message)
            } finally {
                _isCheckoutInProgress.value = false
                checkoutLock.set(false)
            }
        }
    }
}

enum class BarcodeAddResult { ADDED, NOT_FOUND, OUT_OF_STOCK }
