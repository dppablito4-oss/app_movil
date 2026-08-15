package com.example.posapp.data.repository

import com.example.posapp.data.dao.ProductoDao
import com.example.posapp.data.entities.Producto
import com.example.posapp.data.entities.SyncStatus
import kotlinx.coroutines.flow.Flow

class ProductRepository(private val productoDao: ProductoDao, private val businessId: String) {
    init { require(businessId.isNotBlank()) { "No hay un negocio activo" } }

    fun getAllProducts(): Flow<List<Producto>> = productoDao.getAll(businessId)

    suspend fun insertProduct(producto: Producto): Long {
        require(producto.business_id == businessId) { "El producto pertenece a otro negocio" }
        return productoDao.insert(producto)
    }
    suspend fun updateProduct(producto: Producto) {
        require(producto.business_id == businessId) { "El producto pertenece a otro negocio" }
        productoDao.update(producto)
    }
    suspend fun deleteProduct(producto: Producto) {
        require(producto.business_id == businessId) { "El producto pertenece a otro negocio" }
        productoDao.update(producto.copy(
            deleted_at = System.currentTimeMillis(),
            updated_at = System.currentTimeMillis(),
            sync_status = SyncStatus.PENDING
        ))
    }
    suspend fun getById(id: Long): Producto? = productoDao.getById(businessId, id)
    fun searchProductos(query: String): Flow<List<Producto>> = productoDao.searchProductos(businessId, query)
}
