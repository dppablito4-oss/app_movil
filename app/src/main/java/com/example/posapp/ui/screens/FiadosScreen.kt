package com.example.posapp.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material.AlertDialog
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.posapp.data.entities.Cliente
import com.example.posapp.ui.components.SpaceSaleCard
import com.example.posapp.ui.components.SpaceSaleEmptyState
import com.example.posapp.ui.components.SpaceSaleInlineMessage
import com.example.posapp.ui.components.SpaceSaleScreenHeader
import com.example.posapp.ui.theme.SpaceSaleColors
import com.example.posapp.ui.theme.SpaceSaleRadii
import com.example.posapp.ui.theme.SpaceSaleSizes
import com.example.posapp.ui.theme.SpaceSaleSpacing
import com.example.posapp.utils.formatPen
import com.example.posapp.vm.ClienteViewModel

@Composable
fun FiadosScreen(
    navController: NavController,
    onMenuClick: (() -> Unit)? = null,
    viewModel: ClienteViewModel = viewModel()
) {
    val debtors by viewModel.deudores.collectAsState()
    val overview by viewModel.fiadosState.collectAsState()
    val detail by viewModel.debtDetailState.collectAsState()
    var selectedDebtor by remember { mutableStateOf<Cliente?>(null) }
    val totalDebt = debtors.sumOf { it.deuda_total_centavos }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = SpaceSaleSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(SpaceSaleSpacing.Md)
    ) {
        item {
            SpaceSaleScreenHeader(
                title = "Fiados",
                subtitle = if (debtors.isEmpty()) "Sin deuda pendiente" else "Por cobrar: ${totalDebt.formatPen()}",
                onMenu = onMenuClick
            )
        }

        if (debtors.isEmpty()) {
            item {
                SpaceSaleEmptyState(
                    icon = Icons.Default.People,
                    title = "Todo esta al dia",
                    description = "Los clientes con ventas fiadas pendientes apareceran aqui.",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            item { Text("Clientes con deuda", style = MaterialTheme.typography.subtitle1, color = SpaceSaleColors.TextPrimary) }
            items(debtors, key = Cliente::id) { client ->
                SpaceSaleCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedDebtor = client
                            viewModel.loadDebtDetails(client.id)
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(SpaceSaleSpacing.Md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(client.nombre, style = MaterialTheme.typography.subtitle1, color = SpaceSaleColors.TextPrimary)
                            if (!client.telefono.isNullOrBlank()) {
                                Text(client.telefono, style = MaterialTheme.typography.body2, color = SpaceSaleColors.TextSecondary)
                            }
                        }
                        Text(
                            client.deuda_total_centavos.formatPen(),
                            style = MaterialTheme.typography.h6,
                            color = SpaceSaleColors.Warning
                        )
                        Spacer(Modifier.width(SpaceSaleSpacing.Xs))
                        Icon(Icons.Default.ChevronRight, contentDescription = "Ver deuda de ${client.nombre}")
                    }
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.History, contentDescription = null, tint = SpaceSaleColors.Cyan)
                Spacer(Modifier.width(SpaceSaleSpacing.Sm))
                Text("Movimientos recientes", style = MaterialTheme.typography.subtitle1, color = SpaceSaleColors.TextPrimary)
            }
        }
        if (overview.isLoading) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(SpaceSaleSpacing.Xl),
                    horizontalArrangement = Arrangement.Center
                ) { CircularProgressIndicator(color = SpaceSaleColors.Cyan, modifier = Modifier.size(SpaceSaleSizes.IconLarge)) }
            }
        } else if (overview.recentMoves.isEmpty()) {
            item {
                SpaceSaleEmptyState(
                    icon = Icons.Default.ReceiptLong,
                    title = "Sin movimientos",
                    description = "Aqui veras los productos vendidos al fiado.",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            items(overview.recentMoves, key = { it.id }) { move ->
                SpaceSaleCard(modifier = Modifier.fillMaxWidth(), containerColor = SpaceSaleColors.SurfaceRaised) {
                    Row(
                        modifier = Modifier.padding(SpaceSaleSpacing.Md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = SpaceSaleColors.TextMuted)
                        Spacer(Modifier.width(SpaceSaleSpacing.Md))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${move.clientName} llevo ${move.productName}",
                                style = MaterialTheme.typography.body2,
                                color = SpaceSaleColors.TextPrimary
                            )
                            Text(relativeTime(move.timestamp), style = MaterialTheme.typography.caption, color = SpaceSaleColors.TextMuted)
                        }
                        Text(move.amountCents.formatPen(), color = SpaceSaleColors.Warning)
                    }
                }
            }
        }
        overview.errorMessage?.let { item { SpaceSaleInlineMessage(it) } }
        item { Spacer(Modifier.size(SpaceSaleSpacing.Xl)) }
    }

    selectedDebtor?.let { client ->
        AlertDialog(
            onDismissRequest = { selectedDebtor = null },
            backgroundColor = SpaceSaleColors.SurfaceRaised,
            shape = RoundedCornerShape(SpaceSaleRadii.Large),
            title = { Text("Fiado de ${client.nombre}", color = SpaceSaleColors.TextPrimary) },
            text = {
                when {
                    detail.isLoading -> CircularProgressIndicator(color = SpaceSaleColors.Cyan)
                    detail.errorMessage != null -> SpaceSaleInlineMessage(detail.errorMessage)
                    detail.groups.isEmpty() -> Text("No quedan productos pendientes", color = SpaceSaleColors.TextSecondary)
                    else -> Column(verticalArrangement = Arrangement.spacedBy(SpaceSaleSpacing.Sm)) {
                        detail.groups.flatMap { it.lines }.forEach { line ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${line.productName} x${line.quantity}", modifier = Modifier.weight(1f))
                                Text(line.pendingCents.formatPen(), color = SpaceSaleColors.Warning)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        navController.navigate("fiado/${client.id}")
                        selectedDebtor = null
                    },
                    modifier = Modifier.heightIn(min = SpaceSaleSizes.TouchTarget)
                ) { Text("Ver y abonar", color = SpaceSaleColors.Cyan) }
            },
            dismissButton = {
                TextButton(
                    onClick = { selectedDebtor = null },
                    modifier = Modifier.heightIn(min = SpaceSaleSizes.TouchTarget)
                ) { Text("Cerrar", color = SpaceSaleColors.TextSecondary) }
            }
        )
    }
}

private fun relativeTime(timestamp: Long): String {
    val minutes = ((System.currentTimeMillis() - timestamp) / 60_000).coerceAtLeast(0)
    return when {
        minutes < 1 -> "Hace unos segundos"
        minutes < 60 -> "Hace $minutes min"
        minutes < 1_440 -> "Hace ${minutes / 60} h"
        else -> "Hace ${minutes / 1_440} dias"
    }
}
