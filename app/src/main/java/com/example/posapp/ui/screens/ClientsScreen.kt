package com.example.posapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.AlertDialog
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.People
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.posapp.data.entities.Cliente
import com.example.posapp.ui.components.SpaceSaleCard
import com.example.posapp.ui.components.SpaceSaleEmptyState
import com.example.posapp.ui.components.SpaceSalePrimaryButton
import com.example.posapp.ui.components.SpaceSaleScreenHeader
import com.example.posapp.ui.components.SpaceSaleSearchField
import com.example.posapp.ui.components.spaceSaleTextFieldColors
import com.example.posapp.ui.theme.SpaceSaleColors
import com.example.posapp.ui.theme.SpaceSaleRadii
import com.example.posapp.ui.theme.SpaceSaleSizes
import com.example.posapp.ui.theme.SpaceSaleSpacing
import com.example.posapp.utils.formatPen
import com.example.posapp.vm.ClienteViewModel
import kotlinx.coroutines.launch

@Composable
fun ClientsScreen(navController: NavController, clienteViewModel: ClienteViewModel = viewModel()) {
    val clients by clienteViewModel.clientes.collectAsState()
    val scaffoldState = androidx.compose.material.rememberScaffoldState()
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var editingClient by remember { mutableStateOf<Cliente?>(null) }
    var deletingClient by remember { mutableStateOf<Cliente?>(null) }

    val filtered = remember(clients, query) {
        clients.filter {
            query.isBlank() || it.nombre.contains(query, ignoreCase = true) ||
                it.telefono.orEmpty().contains(query)
        }
    }

    Scaffold(scaffoldState = scaffoldState, backgroundColor = SpaceSaleColors.Background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = SpaceSaleSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(SpaceSaleSpacing.Md)
        ) {
            SpaceSaleScreenHeader(
                title = "Clientes",
                subtitle = "${clients.size} registrados",
                onBack = { navController.popBackStack() }
            )
            SpaceSalePrimaryButton(
                onClick = { navController.navigate("add_client") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(SpaceSaleSpacing.Sm))
                Text("Agregar cliente")
            }
            SpaceSaleSearchField(query, { query = it }, "Buscar por nombre o telefono")

            if (filtered.isEmpty()) {
                SpaceSaleEmptyState(
                    icon = Icons.Default.People,
                    title = if (query.isBlank()) "Aun no tienes clientes" else "Sin resultados",
                    description = if (query.isBlank()) {
                        "Crea clientes para registrar fiados y consultar sus pagos."
                    } else {
                        "Revisa el nombre o el telefono ingresado."
                    },
                    modifier = Modifier.weight(1f),
                    action = if (query.isBlank()) ({
                        SpaceSalePrimaryButton(onClick = { navController.navigate("add_client") }) {
                            Text("Crear cliente")
                        }
                    }) else null
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(SpaceSaleSpacing.Sm)
                ) {
                    items(filtered, key = Cliente::id) { client ->
                        SpaceSaleCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.padding(SpaceSaleSpacing.Md),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(client.nombre, style = MaterialTheme.typography.subtitle1, color = SpaceSaleColors.TextPrimary)
                                    if (!client.telefono.isNullOrBlank()) {
                                        Text(client.telefono, style = MaterialTheme.typography.body2, color = SpaceSaleColors.TextSecondary)
                                    }
                                    Text(
                                        if (client.deuda_total_centavos > 0) {
                                            "Debe ${client.deuda_total_centavos.formatPen()}"
                                        } else {
                                            "Sin deuda pendiente"
                                        },
                                        style = MaterialTheme.typography.body2,
                                        color = if (client.deuda_total_centavos > 0) SpaceSaleColors.Warning else SpaceSaleColors.Success
                                    )
                                }
                                IconButton(
                                    onClick = { editingClient = client },
                                    modifier = Modifier.size(SpaceSaleSizes.TouchTarget)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Editar ${client.nombre}")
                                }
                                IconButton(
                                    onClick = { deletingClient = client },
                                    modifier = Modifier.size(SpaceSaleSizes.TouchTarget)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Eliminar ${client.nombre}", tint = SpaceSaleColors.Error)
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.size(SpaceSaleSpacing.Lg)) }
                }
            }
        }
    }

    editingClient?.let { client ->
        EditClientDialog(
            client = client,
            onDismiss = { editingClient = null },
            onSave = { updated -> clienteViewModel.updateCliente(updated) { editingClient = null } }
        )
    }

    deletingClient?.let { client ->
        AlertDialog(
            onDismissRequest = { deletingClient = null },
            backgroundColor = SpaceSaleColors.SurfaceRaised,
            shape = RoundedCornerShape(SpaceSaleRadii.Large),
            title = { Text("Eliminar cliente", color = SpaceSaleColors.TextPrimary) },
            text = { Text("Se ocultara a ${client.nombre}. Su historial de ventas se conservara.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        clienteViewModel.deleteCliente(client) { error ->
                            deletingClient = null
                            if (error != null) scope.launch { scaffoldState.snackbarHostState.showSnackbar(error) }
                        }
                    },
                    modifier = Modifier.heightIn(min = SpaceSaleSizes.TouchTarget)
                ) { Text("Eliminar", color = SpaceSaleColors.Error) }
            },
            dismissButton = {
                TextButton(
                    onClick = { deletingClient = null },
                    modifier = Modifier.heightIn(min = SpaceSaleSizes.TouchTarget)
                ) { Text("Cancelar", color = SpaceSaleColors.TextSecondary) }
            }
        )
    }
}

@Composable
private fun EditClientDialog(client: Cliente, onDismiss: () -> Unit, onSave: (Cliente) -> Unit) {
    var name by rememberSaveable(client.id) { mutableStateOf(client.nombre) }
    var phone by rememberSaveable(client.id) { mutableStateOf(client.telefono.orEmpty()) }
    var note by rememberSaveable(client.id) { mutableStateOf(client.nota) }

    AlertDialog(
        onDismissRequest = onDismiss,
        backgroundColor = SpaceSaleColors.SurfaceRaised,
        shape = RoundedCornerShape(SpaceSaleRadii.Large),
        title = { Text("Editar cliente", color = SpaceSaleColors.TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(SpaceSaleSpacing.Md)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = spaceSaleTextFieldColors()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Telefono") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                    colors = spaceSaleTextFieldColors()
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Notas") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = spaceSaleTextFieldColors()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(client.copy(nombre = name.trim(), telefono = phone.trim(), nota = note.trim())) },
                enabled = name.isNotBlank(),
                modifier = Modifier.heightIn(min = SpaceSaleSizes.TouchTarget)
            ) { Text("Guardar", color = SpaceSaleColors.Cyan) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = SpaceSaleSizes.TouchTarget)) {
                Text("Cancelar", color = SpaceSaleColors.TextSecondary)
            }
        }
    )
}
