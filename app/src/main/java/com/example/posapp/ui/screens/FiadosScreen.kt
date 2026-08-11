package com.example.posapp.ui.screens

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.posapp.data.AppDatabase
import com.example.posapp.data.entities.Cliente
import com.example.posapp.vm.ClienteViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RecentMove(val timestamp: Long, val clienteName: String, val productoName: String, val precio: Double)
data class FiadoDetalle(val producto: String, val cantidad: Int, val total: Double)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FiadosScreen(navController: NavController) {
    val vm: ClienteViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val deudores by vm.deudores.collectAsState(initial = emptyList())
    val context = LocalContext.current
    val db = remember(context.applicationContext) { AppDatabase.getInstance(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var detalleCliente by remember { mutableStateOf<Cliente?>(null) }
    var detalleItems by remember { mutableStateOf<List<FiadoDetalle>>(emptyList()) }

    var loadingRecent by remember { mutableStateOf(true) }

    val recent = remember { mutableStateListOf<RecentMove>() }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        loadingRecent = true
        val latestMoves = runCatching {
            withContext(Dispatchers.IO) {
                val ventas = db.ventaDao().getAllVentas().filter { it.tipo_pago == "FIADO" }
                val detalles = db.ventaDao().getAllDetalles()
                val productos = db.productoDao().getAll().first().associateBy { it.id }
                val clientes = db.clienteDao().getAll().first().associateBy { it.id }

                val moves = mutableListOf<RecentMove>()
                for (v in ventas) {
                    val vDetalles = detalles.filter { it.ventaId == v.id }
                    val clienteNombre = clientes[v.clienteId]?.nombre ?: "Vecino"
                    for (d in vDetalles) {
                        val pname = productos[d.productoId]?.nombre ?: "Producto #${d.productoId}"
                        moves.add(
                            RecentMove(
                                timestamp = v.fecha_hora,
                                clienteName = clienteNombre,
                                productoName = pname,
                                precio = d.precio_unitario_historico
                            )
                        )
                    }
                }
                moves.sortByDescending { it.timestamp }
                moves.take(50)
            }
        }.getOrElse { e ->
            Log.e("Fiados", "Error cargando movimientos", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error cargando movimientos", Toast.LENGTH_SHORT).show()
            }
            emptyList()
        }
        recent.clear()
        recent.addAll(latestMoves)
        loadingRecent = false
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp), state = listState) {
        // Header title
        item {
            Text("Fiados — Deudores", style = MaterialTheme.typography.h6, modifier = Modifier.padding(vertical = 8.dp))
        }

        // Deudores list
        items(deudores) { c: Cliente ->
            Card(modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .padding(vertical = 6.dp)
                .shadow(4.dp, RoundedCornerShape(8.dp)),
                backgroundColor = MaterialTheme.colors.surface,
                shape = RoundedCornerShape(8.dp)) {
                Row(modifier = Modifier
                    .fillMaxSize()
                    .clickable {
                        scope.launch {
                            val details = runCatching {
                                withContext(Dispatchers.IO) {
                                    val ventas = db.ventaDao().getAllVentas().filter { it.tipo_pago == "FIADO" && it.clienteId == c.id }
                                    val allDetalles = db.ventaDao().getAllDetalles()
                                    val productos = db.productoDao().getAll().first().associateBy { it.id }
                                    val list = mutableListOf<FiadoDetalle>()
                                    ventas.forEach { v ->
                                        allDetalles.filter { it.ventaId == v.id }.forEach { d ->
                                            val pname = productos[d.productoId]?.nombre ?: "Producto"
                                            list.add(FiadoDetalle(producto = pname, cantidad = d.cantidad, total = d.cantidad * d.precio_unitario_historico))
                                        }
                                    }
                                    list
                                }
                            }.getOrElse { e ->
                                Log.e("Fiados", "Error cargando detalles", e)
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Error cargando detalles", Toast.LENGTH_SHORT).show()
                                }
                                emptyList()
                            }
                            detalleItems = details
                            detalleCliente = c
                        }
                    }
                    .padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(c.nombre, fontSize = 18.sp, color = MaterialTheme.colors.onSurface)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(c.telefono ?: "", fontSize = 12.sp, color = Color.Gray)
                    }
                    Text("S/ ${String.format("%.2f", c.deuda_total ?: 0.0)}", fontSize = 20.sp, color = Color(0xFFFF6B6B))
                }
            }
        }

        // Divider
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Divider()
        }

        // Header for recent moves
        item {
            Box(modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colors.background)
                .padding(vertical = 8.dp)) {
                Text("⏱️ Últimos Movimientos", style = MaterialTheme.typography.subtitle1, modifier = Modifier.padding(8.dp))
            }

        }

        // Recent moves feed
        items(recent, key = { "${it.timestamp}-${it.clienteName}-${it.productoName}" }) { move ->
            Row(modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .animateItemPlacement()
                , verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .padding(end = 8.dp)
                        .background(Color.Gray.copy(alpha = 0.4f), shape = CircleShape)
                )
                Column {
                    val now = System.currentTimeMillis()
                    val minutes = ((now - move.timestamp) / 60000).coerceAtLeast(0)
                    val timeText = when {
                        minutes < 1 -> "Hace unos segundos"
                        minutes < 60 -> "Hace ${minutes} min"
                        else -> "Hace ${minutes/60} h"
                    }
                    Text("$timeText: ${move.clienteName} llevó ${move.productoName} por S/ ${String.format("%.2f", move.precio)}", fontSize = 12.sp, color = Color.LightGray)
                }
            }
            Divider()
        }

        if (loadingRecent) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    androidx.compose.material.CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            }
        }

        // Bottom spacer to fill remaining space if list is short
        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }

    detalleCliente?.let { c ->
        AlertDialog(
            onDismissRequest = { detalleCliente = null },
            title = { Text("Fiado de ${c.nombre}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    detalleItems.forEach { d ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${d.producto} x${d.cantidad}")
                            Text("S/ ${String.format("%.2f", d.total)}")
                        }
                    }
                    if (detalleItems.isEmpty()) {
                        Text("Sin detalles disponibles")
                    }
                }
            },
            confirmButton = {
                Button(onClick = { navController.navigate("fiado/${c.id}"); detalleCliente = null }) { Text("Ver completo") }
            },
            dismissButton = {
                OutlinedButton(onClick = { detalleCliente = null }) { Text("Cerrar") }
            }
        )
    }

}
