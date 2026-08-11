package com.example.posapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.posapp.data.entities.Producto
import com.example.posapp.vm.InventoryViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController

@Composable
fun InventoryScreen(navController: NavController, viewModel: InventoryViewModel = viewModel()) {
    val productos by viewModel.filteredProductos.collectAsState()
    var query by remember { mutableStateOf("") }
    val lowStockOnly by remember { derivedStateOf { /* read from viewModel via side-effect */ false } }
    var selected by remember { mutableStateOf<com.example.posapp.data.entities.Producto?>(null) }
    var deltaStock by remember { mutableStateOf("0") }
    var newPrice by remember { mutableStateOf("") }

    // update viewModel search query (ViewModel debounces)
    LaunchedEffect(query) { viewModel.setSearchQuery(query) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Inventario", style = MaterialTheme.typography.h6)
                IconButton(onClick = { navController.navigate("add_product") }) {
                    Icon(Icons.Default.Add, contentDescription = "Nuevo Producto")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Buscar producto...") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Filter row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                var active by remember { mutableStateOf(false) }
                IconButton(onClick = { active = !active; viewModel.toggleLowStock() }) {
                    Icon(Icons.Default.FilterList, contentDescription = "Menos Stock", tint = if (active) Color.Cyan else Color.White)
                }
                Text(text = "Menos Stock", modifier = Modifier.align(Alignment.CenterVertically))
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (productos.isEmpty()) {
                // Empty state
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📦", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No hay productos", color = Color.Gray)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(productos) { producto ->
                        ProductCard(producto) { p ->
                            selected = p
                            deltaStock = "0"
                            newPrice = ""
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { navController.navigate("add_product") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Crear")
        }
    }

    selected?.let { prod ->
        StockDialog(
            producto = prod,
            deltaStock = deltaStock,
            onDeltaChange = { deltaStock = it },
            newPrice = newPrice,
            onPriceChange = { newPrice = it },
            onConfirm = {
                val delta = deltaStock.toIntOrNull() ?: 0
                val price = newPrice.toDoubleOrNull()
                viewModel.addStock(prod.id, delta, price)
                selected = null
            },
            onDismiss = { selected = null }
        )
    }
}

@Composable
fun ProductCard(producto: Producto, onClick: (Producto) -> Unit = {}) {
    Card(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp)
        .clickable { onClick(producto) }) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val ctx = LocalContext.current
            if (!producto.ruta_imagen.isNullOrEmpty()) {
                AsyncImage(model = producto.ruta_imagen, contentDescription = producto.nombre, modifier = Modifier.size(48.dp).clip(CircleShape))
            } else {
                // placeholder circle
                Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.DarkGray), contentAlignment = Alignment.Center) {
                    Text("P", color = Color.White)
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(producto.nombre, style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.Bold)
                Text("S/ ${String.format("%.2f", producto.precio_venta)}")
            }

            val stockColor = if (producto.stock <= 5) Color.Red else Color.White
            Text("Stock: ${producto.stock}", color = stockColor)
        }
    }
}

@Composable
private fun StockDialog(
    producto: Producto,
    deltaStock: String,
    onDeltaChange: (String) -> Unit,
    newPrice: String,
    onPriceChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Actualizar stock") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(producto.nombre, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = deltaStock,
                    onValueChange = onDeltaChange,
                    label = { Text("Cantidad a agregar (+) o quitar (-)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = newPrice,
                    onValueChange = onPriceChange,
                    label = { Text("Nuevo precio (opcional)") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text("Guardar") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
