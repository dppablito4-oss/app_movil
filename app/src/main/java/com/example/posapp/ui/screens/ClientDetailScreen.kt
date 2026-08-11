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
import kotlinx.coroutines.launch

@Composable
fun ClientDetailScreen(clientId: Long, navController: NavController) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var ventas by remember { mutableStateOf<List<Venta>>(emptyList()) }
    var detalles by remember { mutableStateOf<List<DetalleVenta>>(emptyList()) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var totalSelected by remember { mutableStateOf(0.0) }

    LaunchedEffect(clientId) {
        val db = AppDatabase.getInstance(ctx.applicationContext)
        ventas = db.ventaDao().getAllVentas().filter { it.clienteId == clientId && it.estado == "PENDIENTE" }
        detalles = db.ventaDao().getAllDetalles()
    }

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Text("Detalle de deudas", style = MaterialTheme.typography.h6)
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn {
            items(ventas) { v ->
                val itemsForVenta = detalles.filter { it.ventaId == v.id }
                Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                    Text("Venta ${v.id} - S/ ${String.format("%.2f", v.total)}")
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
                    // perform payment: delete selected detalles, update venta totals and cliente deuda
                    scope.launch {
                        val db = AppDatabase.getInstance(ctx.applicationContext)
                        val ventaDao = db.ventaDao()
                        val clienteDao = db.clienteDao()

                        try {
                            // load detalles to delete
                            val detallesToDelete = detalles.filter { selectedIds.contains(it.id) }
                            val ventaIds = detallesToDelete.map { it.ventaId }.distinct()
                            // delete selected detalle rows
                            ventaDao.deleteDetallesByIds(detallesToDelete.map { it.id })

                            // for each affected venta, recalc remaining total and update estado
                            for (vid in ventaIds) {
                                val remaining = ventaDao.getDetallesForVenta(vid)
                                val newTotal = remaining.sumOf { it.cantidad * it.precio_unitario_historico }
                                val venta = ventaDao.getById(vid)
                                if (venta != null) {
                                    val updatedVenta = venta.copy(total = newTotal, estado = if (newTotal <= 0.0) "CERRADO" else "PENDIENTE")
                                    ventaDao.updateVenta(updatedVenta)
                                }
                            }

                            // recalc cliente deuda and update
                            val newDeuda = clienteDao.calcularDeuda(clientId)
                            val cliente = clienteDao.getById(clientId)
                            if (cliente != null) {
                                clienteDao.update(cliente.copy(deuda_total = newDeuda))
                            }

                            // refresh lists
                            ventas = ventaDao.getAllVentas().filter { it.clienteId == clientId && it.estado == "PENDIENTE" }
                            detalles = ventaDao.getAllDetalles()
                            selectedIds = emptySet()
                            totalSelected = 0.0
                        } catch (e: Exception) {
                            // optionally show toast (can't import Toast easily here), ignore
                        }
                    }
                }) {
                    Text("PAGAR SELECCIONADO")
                }
            }
        }
    }
}
