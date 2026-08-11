package com.example.posapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Checkbox
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.Button
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.posapp.data.entities.DetalleVenta
import com.example.posapp.data.entities.Venta
import com.example.posapp.data.AppDatabase
import com.example.posapp.data.repository.SalesRepository
import kotlinx.coroutines.launch

@Composable
fun ClientDetailScreen(clientId: Long, navController: NavController) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var ventas by remember { mutableStateOf<List<Venta>>(emptyList()) }
    var detalles by remember { mutableStateOf<List<DetalleVenta>>(emptyList()) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var totalSelected by remember { mutableStateOf(0.0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(clientId) {
        val db = AppDatabase.getInstance(ctx.applicationContext)
        ventas = db.ventaDao().getAllVentas().filter { it.clienteId == clientId && it.estado == "PENDIENTE" }
        detalles = db.ventaDao().getDetallesPendientesCliente(clientId)
    }

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { navController.popBackStack() }) { Text("Volver") }
            Spacer(modifier = Modifier.width(8.dp))
            Text("Detalle de deudas", style = MaterialTheme.typography.h6)
        }
        errorMessage?.let { Text(it, color = MaterialTheme.colors.error) }
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(ventas) { v ->
                val itemsForVenta = detalles.filter { it.ventaId == v.id }
                Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                    val pendingTotal = itemsForVenta.sumOf { it.cantidad * it.precio_unitario_historico }
                    Text("Venta ${v.id} - Pendiente S/ ${String.format("%.2f", pendingTotal)}")
                    Spacer(modifier = Modifier.height(4.dp))
                    for (d in itemsForVenta) {
                        // remember checked state keyed by detalle id
                        var checked by remember(d.id) { mutableStateOf(selectedIds.contains(d.id)) }
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = checked, onCheckedChange = { ch ->
                                checked = ch
                                val mutable = selectedIds.toMutableSet()
                                if (ch) mutable.add(d.id) else mutable.remove(d.id)
                                selectedIds = mutable
                                // recalc total
                                val sum = detalles.filter { selectedIds.contains(it.id) }.sumOf { it.cantidad * it.precio_unitario_historico }
                                totalSelected = sum
                            })
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("${d.cantidad} x Producto#${d.productoId}")
                                Text("S/ ${String.format("%.2f", d.precio_unitario_historico)}")
                            }
                        }
                        Divider()
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        // Bottom action bar
        if (selectedIds.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Pagar seleccionado: S/ ${String.format("%.2f", totalSelected)}", style = MaterialTheme.typography.subtitle1)
                Button(onClick = {
                    scope.launch {
                        val db = AppDatabase.getInstance(ctx.applicationContext)
                        val ventaDao = db.ventaDao()

                        try {
                            SalesRepository(db).payDetails(clientId, selectedIds)
                            ventas = ventaDao.getAllVentas().filter { it.clienteId == clientId && it.estado == "PENDIENTE" }
                            detalles = ventaDao.getDetallesPendientesCliente(clientId)
                            selectedIds = emptySet()
                            totalSelected = 0.0
                            errorMessage = null
                        } catch (e: Exception) {
                            errorMessage = e.message ?: "No se pudo registrar el pago"
                        }
                    }
                }) {
                    Text("PAGAR SELECCIONADO")
                }
            }
        }
    }
}
