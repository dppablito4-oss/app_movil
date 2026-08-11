package com.example.posapp.data.repository

import com.example.posapp.data.dao.ProductoDao
import com.example.posapp.data.entities.Producto
import kotlinx.coroutines.flow.Flow

class ProductRepository(private val productoDao: ProductoDao) {
    fun getAllProducts(): Flow<List<Producto>> = productoDao.getAll()

    suspend fun insertProduct(producto: Producto): Long = productoDao.insert(producto)
    suspend fun updateProduct(producto: Producto) = productoDao.update(producto)
    suspend fun deleteProduct(producto: Producto) = productoDao.delete(producto)
    suspend fun getById(id: Long): Producto? = productoDao.getById(id)
    fun searchProductos(query: String): Flow<List<Producto>> = productoDao.searchProductos(query)
}
