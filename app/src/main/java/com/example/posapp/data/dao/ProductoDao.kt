package com.example.posapp.data.dao

import androidx.room.*
import com.example.posapp.data.entities.Producto
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductoDao {
    @Query("SELECT * FROM producto WHERE id = :id")
    suspend fun getById(id: Long): Producto?

    @Query("SELECT * FROM producto ORDER BY nombre")
    fun getAll(): Flow<List<Producto>>

    @Insert
    suspend fun insert(producto: Producto): Long

    @Update
    suspend fun update(producto: Producto)

    @Delete
    suspend fun delete(producto: Producto)

    // Decrement stock atomically; returns number of rows affected (0 => not enough stock)
    @Query("UPDATE producto SET stock = stock - :cantidad WHERE id = :id AND stock >= :cantidad")
    suspend fun decreaseStockIfEnough(id: Long, cantidad: Int): Int

    @Query("UPDATE producto SET stock = stock + :cantidad WHERE id = :id")
    suspend fun increaseStock(id: Long, cantidad: Int)

    @Query("SELECT * FROM producto WHERE busqueda_normalizada LIKE :query || '%' ORDER BY nombre")
    fun searchProductos(query: String): Flow<List<Producto>>
}
