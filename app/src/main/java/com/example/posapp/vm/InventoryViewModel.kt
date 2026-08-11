package com.example.posapp.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.posapp.data.AppDatabase
import com.example.posapp.data.entities.Producto
import com.example.posapp.data.repository.ProductRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Locale

class InventoryViewModel(application: Application) : AndroidViewModel(application) {
    private val productoDao = AppDatabase.getInstance(application).productoDao()
    private val repository = ProductRepository(productoDao)

    // raw products from DB
    private val _productos = MutableStateFlow<List<Producto>>(emptyList())

    // UI state: search query and low-stock filter
    private val _searchQuery = MutableStateFlow("")
    private val _lowStockOnly = MutableStateFlow(false)

    // expose filtered products (debounce search)
    val filteredProductos: StateFlow<List<Producto>> = combine(
        _productos,
        _searchQuery.debounce(300),
        _lowStockOnly
    ) { list, query, lowOnly ->
        var filtered = list
        if (query.isNotBlank()) {
            val q = query.trim().uppercase()
            filtered = filtered.filter { it.busqueda_normalizada.contains(q) || it.nombre.uppercase().contains(q) }
        }
        if (lowOnly) {
            filtered = filtered.filter { it.stock <= 5 }
        }
        filtered
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        viewModelScope.launch {
            repository.getAllProducts().collect { list ->
                _productos.value = list
            }
        }
    }

    fun setSearchQuery(q: String) {
        _searchQuery.value = q
    }

    fun toggleLowStock() {
        _lowStockOnly.value = !_lowStockOnly.value
    }

    fun addProduct(nombre: String, precio: Double, stock: Int, rutaImagen: String?, onComplete: (String?) -> Unit = {}) {
        viewModelScope.launch {
            val cleanName = nombre.trim()
            if (cleanName.isBlank() || precio <= 0.0 || !precio.isFinite() || stock < 0) {
                onComplete("Revisa el nombre, el precio y el stock")
                return@launch
            }
            val prod = Producto(
                nombre = cleanName,
                precio_costo = 0.0,
                precio_venta = precio,
                stock = stock,
                ruta_imagen = rutaImagen,
                busqueda_normalizada = cleanName.uppercase(Locale.ROOT)
            )
            val error = runCatching { repository.insertProduct(prod) }.exceptionOrNull()?.message
            onComplete(error)
        }
    }

    fun addStock(productId: Long, delta: Int, newPrice: Double? = null, onComplete: (String?) -> Unit = {}) {
        viewModelScope.launch {
            if (newPrice != null && (newPrice <= 0.0 || !newPrice.isFinite())) {
                onComplete("El precio debe ser mayor que cero")
                return@launch
            }
            repository.getById(productId)?.let { current ->
                val updated = current.copy(
                    stock = (current.stock + delta).coerceAtLeast(0),
                    precio_venta = newPrice ?: current.precio_venta
                )
                repository.updateProduct(updated)
                onComplete(null)
            } ?: run {
                onComplete("El producto ya no existe")
            }
        }
    }
}
