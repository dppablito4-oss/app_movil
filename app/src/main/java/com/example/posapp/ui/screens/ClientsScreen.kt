package com.example.posapp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.posapp.data.entities.Cliente
import com.example.posapp.vm.ClienteViewModel

@Composable
fun ClientsScreen(navController: NavController, clienteViewModel: ClienteViewModel = viewModel()) {
    val clients by clienteViewModel.clientes.collectAsState()
    var query by remember { mutableStateOf("") }
    val scaffoldState = rememberScaffoldState()
    val scope = rememberCoroutineScope()

    var editingCliente by remember { mutableStateOf<Cliente?>(null) }
    var showConfirmDelete by remember { mutableStateOf<Cliente?>(null) }

    val filtered = remember(clients, query) {
        if (query.isBlank()) clients else clients.filter { it.nombre.contains(query, ignoreCase = true) }
    }

    Scaffold(scaffoldState = scaffoldState, topBar = {
        TopAppBar(title = { Text("Clientes") }, backgroundColor = MaterialTheme.colors.surface, contentColor = MaterialTheme.colors.onSurface)
    }) { padding ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)) {

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text("Buscar clientes...") }
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No hay clientes", color = MaterialTheme.colors.onBackground)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filtered) { cliente ->
                        Card(modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)) {
                            Row(modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(cliente.nombre, style = MaterialTheme.typography.subtitle1, color = MaterialTheme.colors.onBackground)
                                    cliente.telefono?.let { if (it.isNotBlank()) Text(it, style = MaterialTheme.typography.body2, color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f)) }
                                }
                                Text("S/ ${String.format("%.2f", cliente.deuda_total ?: 0.0)}", modifier = Modifier.padding(end = 12.dp), color = MaterialTheme.colors.primary)
                                IconButton(onClick = { editingCliente = cliente }) { Icon(Icons.Default.Edit, contentDescription = "Editar") }
                                IconButton(onClick = { showConfirmDelete = cliente }) { Icon(Icons.Default.Delete, contentDescription = "Eliminar") }
                            }
                        }
                    }
                }
            }
        }
    }

    // Edit dialog
    editingCliente?.let { c ->
        var name by remember { mutableStateOf(c.nombre) }
        var phone by remember { mutableStateOf(c.telefono ?: "") }
        AlertDialog(
            onDismissRequest = { editingCliente = null },
            title = { Text("Editar Cliente") },
            text = {
                Column {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre") })
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Teléfono") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val updated = c.copy(nombre = name.trim(), telefono = phone.trim())
                    clienteViewModel.updateCliente(updated) { editingCliente = null }
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { editingCliente = null }) { Text("Cancelar") } }
        )
    }

    // Delete confirmation
    showConfirmDelete?.let { c ->
        AlertDialog(
            onDismissRequest = { showConfirmDelete = null },
            title = { Text("Eliminar cliente") },
            text = { Text("¿Eliminar a ${c.nombre}? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    clienteViewModel.deleteCliente(c) { showConfirmDelete = null }
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { showConfirmDelete = null }) { Text("Cancelar") } }
        )
    }
}
