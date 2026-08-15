package com.example.posapp.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.example.posapp.data.AppDatabase
import com.example.posapp.data.ActiveBusinessStore
import com.example.posapp.data.entities.Producto
import com.example.posapp.data.entities.SyncStatus
import com.example.posapp.data.entities.StockMovement
import com.example.posapp.data.repository.ProductRepository
import com.example.posapp.data.sync.CloudSyncScheduler
import com.example.posapp.utils.toCents
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

@OptIn(kotlinx.coroutines.FlowPreview::class)
class InventoryViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val productoDao = database.productoDao()
    private val movementDao = database.stockMovementDao()
    private val repository = ProductRepository(productoDao)
    private val activeBusiness = ActiveBusinessStore(application)

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

    fun addProduct(
        nombre: String,
        precio: Double,
        stock: Int,
        rutaImagen: String?,
        codigoBarras: String? = null,
        stockMinimo: Int = 5,
        onComplete: (String?) -> Unit = {}
    ) {
        viewModelScope.launch {
            val cleanName = nombre.trim()
            if (cleanName.isBlank() || precio <= 0.0 || !precio.isFinite() || stock < 0 || stockMinimo < 0) {
                onComplete("Revisa el nombre, el precio y el stock")
                return@launch
            }
            val now = System.currentTimeMillis()
            val syncId = UUID.randomUUID().toString()
            val prod = Producto(
                nombre = cleanName,
                precio_costo = 0.0,
                precio_venta = 0.0,
                precio_costo_centavos = 0,
                precio_venta_centavos = precio.toCents(),
                stock = stock,
                ruta_imagen = rutaImagen,
                image_sync_status = if (rutaImagen == null) SyncStatus.SYNCED else SyncStatus.PENDING,
                busqueda_normalizada = cleanName.uppercase(Locale.ROOT),
                codigo_barras = codigoBarras?.trim()?.ifBlank { null },
                stock_minimo = stockMinimo,
                sync_id = syncId,
                business_id = activeBusiness.businessId(),
                created_at = now,
                updated_at = now,
                sync_status = SyncStatus.PENDING
            )
            val error = runCatching {
                database.withTransaction {
                    val productId = repository.insertProduct(prod)
                    if (stock > 0) {
                        movementDao.insert(
                            StockMovement(
                                productId = productId,
                                type = "INITIAL",
                                quantity_delta = stock,
                                notes = "Stock inicial",
                                sync_id = UUID.randomUUID().toString(),
                                business_id = prod.business_id,
                                created_at = now
                            )
                        )
                    }
                }
                CloudSyncScheduler.schedule(getApplication<Application>().applicationContext)
            }.exceptionOrNull()?.message
            onComplete(error)
        }
    }

    fun addStock(
        productId: Long,
        delta: Int,
        newPrice: Double? = null,
        movementType: String = if (delta >= 0) "PURCHASE" else "ADJUSTMENT",
        reason: String = "",
        onComplete: (String?) -> Unit = {}
    ) {
        viewModelScope.launch {
            if (newPrice != null && (newPrice <= 0.0 || !newPrice.isFinite())) {
                onComplete("El precio debe ser mayor que cero")
                return@launch
            }
            repository.getById(productId)?.let { current ->
                val newStock = (current.stock + delta).coerceAtLeast(0)
                val actualDelta = newStock - current.stock
                val now = System.currentTimeMillis()
                val updated = current.copy(
                    stock = newStock,
                    precio_venta = 0.0,
                    precio_venta_centavos = newPrice?.toCents() ?: current.precio_venta_centavos,
                    updated_at = now,
                    sync_status = SyncStatus.PENDING
                )
                database.withTransaction {
                    repository.updateProduct(updated)
                    if (actualDelta != 0) {
                        movementDao.insert(
                            StockMovement(
                                productId = current.id,
                                type = movementType,
                                quantity_delta = actualDelta,
                                notes = reason.trim(),
                                sync_id = UUID.randomUUID().toString(),
                                business_id = current.business_id,
                                created_at = now
                            )
                        )
                    }
                }
                CloudSyncScheduler.schedule(getApplication<Application>().applicationContext)
                onComplete(null)
            } ?: run {
                onComplete("El producto ya no existe")
            }
        }
    }
}
