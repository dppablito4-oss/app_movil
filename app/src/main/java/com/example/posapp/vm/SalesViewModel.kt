package com.example.posapp.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.posapp.data.AppDatabase
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

data class CartItem(val producto: Producto, val cantidad: Int = 1)

class SalesViewModel(application: Application) : AndroidViewModel(application) {
    private val productoDao = AppDatabase.getInstance(application).productoDao()
    private val repository = SalesRepository(AppDatabase.getInstance(application))

    private val _searchResults = MutableStateFlow<List<Producto>>(emptyList())
    val searchResults: StateFlow<List<Producto>> = _searchResults.asStateFlow()

    private val _cart = MutableStateFlow<List<CartItem>>(emptyList())
    val cart: StateFlow<List<CartItem>> = _cart.asStateFlow()

    private var searchJob: Job? = null

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

    fun total(): Double = _cart.value.sumOf { it.producto.precio_venta * it.cantidad }

    fun checkout(tipoPago: String, clienteId: Long? = null, onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                repository.checkout(
                    lines = _cart.value.map { SaleLine(it.producto, it.cantidad) },
                    tipoPago = tipoPago,
                    clienteId = clienteId
                )
                CloudSyncScheduler.schedule(getApplication<Application>().applicationContext)
                _cart.value = emptyList()
                onComplete(true, null)
            } catch (e: Exception) {
                onComplete(false, e.message)
            }
        }
    }
}
