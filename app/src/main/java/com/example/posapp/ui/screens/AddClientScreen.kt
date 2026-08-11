package com.example.posapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.posapp.vm.ClienteViewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction

@Composable
fun AddClientScreen(navController: NavController, clienteViewModel: ClienteViewModel = viewModel()) {
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    val scaffoldState = rememberScaffoldState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    Scaffold(scaffoldState = scaffoldState, topBar = {
        TopAppBar(title = { Text("Agregar Cliente") }, backgroundColor = MaterialTheme.colors.surface, contentColor = MaterialTheme.colors.onSurface, elevation = 4.dp)
    }) { padding ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)) {
            Text(text = "Detalles del cliente", style = MaterialTheme.typography.h6, color = MaterialTheme.colors.onBackground)
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Teléfono") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Notas (opcional)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = {
                if (name.isBlank()) {
                    scope.launch { scaffoldState.snackbarHostState.showSnackbar("El nombre es obligatorio") }
                    return@Button
                }
                clienteViewModel.addCliente(nombre = name.trim(), telefono = phone.trim()) { id ->
                    scope.launch { scaffoldState.snackbarHostState.showSnackbar("Cliente guardado") }
                    navController.popBackStack()
                }
            }, modifier = Modifier.fillMaxWidth(), enabled = name.isNotBlank()) {
                Text("Guardar")
            }
        }
    }
}
