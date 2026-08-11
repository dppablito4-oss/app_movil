package com.example.posapp.ui.screens

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.posapp.data.entities.Producto
import com.example.posapp.vm.SalesViewModel
import com.example.posapp.vm.ClienteViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SalesScreen(navController: NavController? = null, viewModel: SalesViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var scanningAllowed by remember { mutableStateOf(false) }
    val searchResults by viewModel.searchResults.collectAsState()
    val cart by viewModel.cart.collectAsState()
    val clienteVm: ClienteViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val clientes by clienteVm.clientes.collectAsState()

    // debounce search
    LaunchedEffect(query) {
        delay(300)
        viewModel.search(query)
    }

    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        scanningAllowed = granted
        if (!granted) Toast.makeText(ctx, "Permiso de cámara denegado", Toast.LENGTH_SHORT).show()
    }

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.weight(1f), placeholder = { Text("Buscar producto o escanear...") })
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = { requestPermission.launch(Manifest.permission.CAMERA) }) { Text("📷") }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // search results
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(searchResults) { p ->
                val qty = cart.find { it.producto.id == p.id }?.cantidad ?: 0
                ProductRowForSale(producto = p, cantidadInicial = qty,
                    onAdd = {
                        viewModel.addToCart(p)
                        Toast.makeText(ctx, "Agregado: ${p.nombre}", Toast.LENGTH_SHORT).show()
                    },
                    onInc = { viewModel.incQuantity(p.id) },
                    onDec = { viewModel.decQuantity(p.id) }
                )
            }
        }

        // Cart
        Divider()
        Text("Carrito", style = MaterialTheme.typography.h6)
        LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
            items(cart) { item ->
                Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${item.cantidad}x ${item.producto.nombre} - S/ ${String.format("%.2f", item.producto.precio_venta * item.cantidad)}", modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.decQuantity(item.producto.id) }) { Text("-") }
                    IconButton(onClick = { viewModel.incQuantity(item.producto.id) }) { Text("+") }
                }
            }
        }

        // Bottom summary
        Surface(elevation = 8.dp) {
            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("TOTAL: S/ ${String.format("%.2f", viewModel.total())}", style = MaterialTheme.typography.h6)
                var showDialog by remember { mutableStateOf(false) }
                if (showDialog) {
                    PaymentDialog(
                        clientes = clientes,
                        onDismiss = { showDialog = false },
                        onConfirm = { tipoPago, clienteId ->
                            viewModel.checkout(tipoPago, clienteId) { success, err ->
                                if (success) Toast.makeText(ctx, "Venta registrada", Toast.LENGTH_SHORT).show() else Toast.makeText(ctx, "Error: $err", Toast.LENGTH_SHORT).show()
                            }
                            showDialog = false
                        },
                        onAddClient = { name, onAdded ->
                            if (name.isNotBlank()) {
                                clienteVm.addCliente(nombre = name.trim()) { id -> onAdded(id) }
                            }
                        }
                    )
                }

                Button(onClick = { showDialog = true }, enabled = cart.isNotEmpty()) { Text("COBRAR") }
            }
        }
    }
}

@Composable
fun ProductRowForSale(producto: Producto, cantidadInicial: Int = 0, onAdd: () -> Unit, onInc: () -> Unit = {}, onDec: () -> Unit = {}) {
    var cantidad by remember { mutableStateOf(cantidadInicial) }
    LaunchedEffect(cantidadInicial) { cantidad = cantidadInicial }

    Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (!producto.ruta_imagen.isNullOrEmpty()) AsyncImage(model = producto.ruta_imagen, contentDescription = producto.nombre, modifier = Modifier.size(48.dp)) else Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) { Text("IMG") }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(producto.nombre)
            Text("S/ ${String.format("%.2f", producto.precio_venta)}")
        }
        if (cantidad <= 0) {
            Button(onClick = {
                cantidad = 1
                onAdd()
            }, colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF00E5FF))) {
                Text("Agregar", color = Color.Black)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.background(Color(0xFF00E5FF), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                IconButton(onClick = {
                    if (cantidad > 0) {
                        cantidad -= 1
                        onDec()
                    }
                }) { Icon(Icons.Default.Remove, contentDescription = "-", tint = Color.Black) }
                Text("$cantidad", color = Color.Black, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
                IconButton(onClick = {
                    cantidad += 1
                    onInc()
                }) { Icon(Icons.Default.Add, contentDescription = "+", tint = Color.Black) }
            }
        }
    }
}

@Composable
fun PaymentDialog(
    clientes: List<com.example.posapp.data.entities.Cliente>,
    onDismiss: () -> Unit,
    onConfirm: (String, Long?) -> Unit,
    onAddClient: (String, (Long) -> Unit) -> Unit
) {
    var selected by remember { mutableStateOf("EFECTIVO") }
    var expanded by remember { mutableStateOf(false) }
    var selectedClienteId by remember { mutableStateOf<Long?>(null) }
    var newClientName by remember { mutableStateOf("") }
    val ctx = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Seleccionar método de pago") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selected == "EFECTIVO", onClick = { selected = "EFECTIVO" })
                    Text("Efectivo")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selected == "YAPE", onClick = { selected = "YAPE" })
                    Text("Yape/Plin")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selected == "FIADO", onClick = { selected = "FIADO" })
                    Text("Fiado")
                }

                if (selected == "FIADO") {
                    Spacer(modifier = Modifier.height(8.dp))
                    // simple dropdown to pick client
                    Box {
                        OutlinedButton(onClick = { expanded = true }) {
                            Text(if (selectedClienteId == null) "Seleccionar vecino..." else clientes.find { it.id == selectedClienteId }?.nombre ?: "Seleccionar")
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            clientes.forEach { c ->
                                DropdownMenuItem(onClick = { selectedClienteId = c.id; expanded = false }) {
                                    Text(c.nombre)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newClientName,
                        onValueChange = { newClientName = it },
                        label = { Text("Agregar vecino rápido") },
                        placeholder = { Text("Nombre") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val name = newClientName.trim()
                            if (name.isNotEmpty()) {
                                onAddClient(name) { newId ->
                                    selectedClienteId = newId
                                    newClientName = ""
                                }
                            }
                        },
                        enabled = newClientName.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Agregar y seleccionar")
                    }

                    if (selectedClienteId == null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Para fiar, primero selecciona o crea un vecino.", color = Color.Red, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selected == "FIADO" && selectedClienteId == null) {
                        Toast.makeText(ctx, "Selecciona o agrega un vecino para fiar", Toast.LENGTH_SHORT).show()
                    } else {
                        onConfirm(selected, if (selected == "FIADO") selectedClienteId else null)
                    }
                },
                enabled = selected != "FIADO" || selectedClienteId != null
            ) { Text("Confirmar") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

