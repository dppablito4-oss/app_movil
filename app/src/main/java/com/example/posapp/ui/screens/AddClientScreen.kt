package com.example.posapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.posapp.ui.components.SpaceSaleInlineMessage
import com.example.posapp.ui.components.SpaceSalePrimaryButton
import com.example.posapp.ui.components.SpaceSaleScreenHeader
import com.example.posapp.ui.components.spaceSaleTextFieldColors
import com.example.posapp.ui.theme.SpaceSaleColors
import com.example.posapp.ui.theme.SpaceSaleSpacing
import com.example.posapp.vm.ClienteViewModel
import kotlinx.coroutines.launch

@Composable
fun AddClientScreen(navController: NavController, clienteViewModel: ClienteViewModel = viewModel()) {
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val scaffoldState = androidx.compose.material.rememberScaffoldState()
    val scope = rememberCoroutineScope()

    fun save() {
        if (name.isBlank()) {
            errorMessage = "El nombre es obligatorio"
            return
        }
        clienteViewModel.addCliente(name.trim(), phone.trim(), note.trim()) {
            scope.launch { scaffoldState.snackbarHostState.showSnackbar("Cliente guardado") }
            navController.popBackStack()
        }
    }

    Scaffold(scaffoldState = scaffoldState, backgroundColor = SpaceSaleColors.Background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SpaceSaleSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(SpaceSaleSpacing.Md)
        ) {
            SpaceSaleScreenHeader(
                title = "Nuevo cliente",
                subtitle = "Los campos opcionales pueden completarse despues",
                onBack = { navController.popBackStack() }
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; errorMessage = null },
                label = { Text("Nombre *") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                isError = errorMessage != null && name.isBlank(),
                modifier = Modifier.fillMaxWidth(),
                colors = spaceSaleTextFieldColors()
            )
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Telefono") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
                colors = spaceSaleTextFieldColors()
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Notas") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                modifier = Modifier.fillMaxWidth(),
                colors = spaceSaleTextFieldColors()
            )
            errorMessage?.let { SpaceSaleInlineMessage(it) }
            SpaceSalePrimaryButton(
                onClick = ::save,
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null)
                Spacer(Modifier.padding(SpaceSaleSpacing.Xs))
                Text("Guardar cliente")
            }
            Spacer(Modifier.padding(SpaceSaleSpacing.Lg))
        }
    }
}
