package com.example.posapp.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.posapp.data.AppDatabase
import com.example.posapp.data.entities.Cliente
import com.example.posapp.data.repository.SalesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ClienteViewModel(application: Application) : AndroidViewModel(application) {
    private val clienteDao = AppDatabase.getInstance(application).clienteDao()
    private val salesRepository = SalesRepository(AppDatabase.getInstance(application))

    private val _clientes = MutableStateFlow<List<Cliente>>(emptyList())
    val clientes: StateFlow<List<Cliente>> = _clientes.asStateFlow()

    private val _deudores = MutableStateFlow<List<Cliente>>(emptyList())
    val deudores: StateFlow<List<Cliente>> = _deudores.asStateFlow()

    init {
        viewModelScope.launch {
            clienteDao.getAll().collect { list -> _clientes.value = list }
        }
        viewModelScope.launch {
            clienteDao.obtenerDeudores().collect { list -> _deudores.value = list }
        }
    }

    fun addCliente(nombre: String, telefono: String = "", nota: String = "", deudaInicial: Double = 0.0, onComplete: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = clienteDao.insert(Cliente(nombre = nombre, telefono = telefono, deuda_total = deudaInicial, nota = nota))
            onComplete(id)
        }
    }

    fun updateCliente(cliente: com.example.posapp.data.entities.Cliente, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            clienteDao.update(cliente)
            onComplete()
        }
    }

    fun deleteCliente(cliente: Cliente, onComplete: (String?) -> Unit = {}) {
        viewModelScope.launch {
            val error = runCatching { salesRepository.deleteClient(cliente) }.exceptionOrNull()?.message
            onComplete(error)
        }
    }
}
