package com.example.posapp.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.posapp.data.AppDatabase
import com.example.posapp.data.entities.DetalleVenta
import com.example.posapp.data.entities.Producto
import com.example.posapp.data.entities.Venta
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.*

data class CartItem(val producto: Producto, var cantidad: Int = 1)

class SalesViewModel(application: Application) : AndroidViewModel(application) {
    private val productoDao = AppDatabase.getInstance(application).productoDao()
    private val ventaDao = AppDatabase.getInstance(application).ventaDao()
    private val clienteDao = AppDatabase.getInstance(application).clienteDao()

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

    fun addToCart(producto: Producto) {
        val current = _cart.value.toMutableList()
        val existing = current.find { it.producto.id == producto.id }
        if (existing != null) existing.cantidad += 1 else current.add(CartItem(producto, 1))
        _cart.value = current
    }

    fun incQuantity(productId: Long) {
        val current = _cart.value.toMutableList()
        current.find { it.producto.id == productId }?.let { it.cantidad += 1 }
        _cart.value = current
    }

    fun decQuantity(productId: Long) {
        val current = _cart.value.toMutableList()
        val item = current.find { it.producto.id == productId }
        if (item != null) {
            item.cantidad -= 1
            if (item.cantidad <= 0) current.remove(item)
        }
        _cart.value = current
    }

    fun total(): Double = _cart.value.sumOf { it.producto.precio_venta * it.cantidad }

    fun checkout(tipoPago: String, clienteId: Long? = null, onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val venta = Venta(fecha_hora = now, total = total(), tipo_pago = tipoPago, clienteId = clienteId, estado = if (tipoPago == "FIADO") "PENDIENTE" else "CERRADO")
                val detalles = _cart.value.map { CartItem ->
                    DetalleVenta(ventaId = 0, productoId = CartItem.producto.id, cantidad = CartItem.cantidad, precio_unitario_historico = CartItem.producto.precio_venta)
                }
                val ventaId = ventaDao.insertVentaConDetalles(venta, detalles, productoDao)

                // If FIADO, increment client's deuda_total stored in Cliente table
                if (tipoPago == "FIADO" && clienteId != null) {
                    val cliente = clienteDao.getById(clienteId)
                    if (cliente != null) {
                        val current = cliente.deuda_total ?: 0.0
                        clienteDao.update(cliente.copy(deuda_total = current + venta.total))
                    }
                }

                _cart.value = emptyList()
                onComplete(true, null)
            } catch (e: Exception) {
                onComplete(false, e.message)
            }
        }
    }
}
