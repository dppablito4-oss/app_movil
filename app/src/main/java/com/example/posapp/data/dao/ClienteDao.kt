package com.example.posapp.data.dao

import androidx.room.*
import com.example.posapp.data.entities.Cliente
import kotlinx.coroutines.flow.Flow

@Dao
interface ClienteDao {
    @Query("SELECT * FROM cliente WHERE id = :id")
    suspend fun getById(id: Long): Cliente?

    @Query("SELECT * FROM cliente ORDER BY nombre")
    fun getAll(): Flow<List<Cliente>>

    @Insert
    suspend fun insert(cliente: Cliente): Long

    @Update
    suspend fun update(cliente: Cliente)

    @Delete
    suspend fun delete(cliente: Cliente)

    // Example: calculate debt on the fly
    @Query("SELECT IFNULL(SUM(total), 0) FROM venta WHERE clienteId = :clienteId AND estado = 'PENDIENTE'")
    suspend fun calcularDeuda(clienteId: Long): Double

    // Get only clients with positive debt
    @Query("SELECT * FROM cliente WHERE IFNULL(deuda_total,0) > 0 ORDER BY deuda_total DESC")
    fun obtenerDeudores(): kotlinx.coroutines.flow.Flow<List<Cliente>>
}
