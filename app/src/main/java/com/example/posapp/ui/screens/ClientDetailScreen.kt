package com.example.posapp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.AlertDialog
import androidx.compose.material.Checkbox
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.posapp.ui.components.SpaceSaleCard
import com.example.posapp.ui.components.SpaceSaleEmptyState
import com.example.posapp.ui.components.SpaceSaleInlineMessage
import com.example.posapp.ui.components.SpaceSalePrimaryButton
import com.example.posapp.ui.components.SpaceSaleScreenHeader
import com.example.posapp.ui.components.spaceSaleTextFieldColors
import com.example.posapp.ui.theme.SpaceSaleColors
import com.example.posapp.ui.theme.SpaceSaleSpacing
import com.example.posapp.utils.formatPen
import com.example.posapp.utils.parseLocalizedDecimal
import com.example.posapp.vm.ClienteViewModel
import java.text.DateFormat
import java.util.Date

@Composable
fun ClientDetailScreen(
    clientId: Long,
    navController: NavController,
    viewModel: ClienteViewModel = viewModel()
) {
    val state by viewModel.debtDetailState.collectAsState()
    var selectedIds by rememberSaveable(clientId) { mutableStateOf(emptySet<Long>()) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var showPaymentDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(clientId) { viewModel.loadDebtDetails(clientId) }

    val lines = state.groups.flatMap { it.lines }
    val selectedTotal = lines.filter { it.detailId in selectedIds }.sumOf { it.pendingCents }

    Scaffold(
        backgroundColor = SpaceSaleColors.Background,
        bottomBar = {
            if (selectedIds.isNotEmpty()) {
                SpaceSaleCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SpaceSaleSpacing.Lg, vertical = SpaceSaleSpacing.Sm),
                    containerColor = SpaceSaleColors.SurfaceRaised
                ) {
                    Column(
                        modifier = Modifier.padding(SpaceSaleSpacing.Md),
                        verticalArrangement = Arrangement.spacedBy(SpaceSaleSpacing.Sm)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Seleccionado", color = SpaceSaleColors.TextSecondary)
                            Text(selectedTotal.formatPen(), style = MaterialTheme.typography.h6, color = SpaceSaleColors.TextPrimary)
                        }
                        SpaceSalePrimaryButton(
                            onClick = { showPaymentDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = SpaceSaleColors.Success,
                            contentColor = SpaceSaleColors.OnSuccess,
                            disabledContainerColor = SpaceSaleColors.SuccessContainer
                        ) {
                            Icon(Icons.Default.Payments, contentDescription = null)
                            Spacer(Modifier.width(SpaceSaleSpacing.Sm))
                            Text("Registrar pago")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = SpaceSaleSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(SpaceSaleSpacing.Md)
        ) {
            SpaceSaleScreenHeader(
                title = state.clientName.ifBlank { "Detalle de deuda" },
                subtitle = "Selecciona los productos que pagara",
                onBack = { navController.popBackStack() }
            )
            errorMessage?.let { SpaceSaleInlineMessage(it) }
            state.errorMessage?.let { SpaceSaleInlineMessage(it) }

            when {
                state.isLoading -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(SpaceSaleSpacing.Xl),
                        horizontalArrangement = Arrangement.Center
                    ) { CircularProgressIndicator(color = SpaceSaleColors.Cyan) }
                }
                state.groups.isEmpty() -> {
                    SpaceSaleEmptyState(
                        icon = Icons.Default.CheckCircle,
                        title = "No hay deuda pendiente",
                        description = "Todos los productos de este cliente estan pagados.",
                        modifier = Modifier.weight(1f)
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(SpaceSaleSpacing.Md)
                    ) {
                        items(state.groups, key = { it.saleId }) { group ->
                            SpaceSaleCard(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier.padding(SpaceSaleSpacing.Md),
                                    verticalArrangement = Arrangement.spacedBy(SpaceSaleSpacing.Sm)
                                ) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = SpaceSaleColors.Cyan)
                                            Spacer(Modifier.width(SpaceSaleSpacing.Sm))
                                            Text("Venta ${group.saleId}", style = MaterialTheme.typography.subtitle1)
                                        }
                                        Text(group.pendingCents.formatPen(), color = SpaceSaleColors.Warning)
                                    }
                                    group.lines.forEach { line ->
                                        val selected = line.detailId in selectedIds
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    selectedIds = if (selected) selectedIds - line.detailId else selectedIds + line.detailId
                                                }
                                                .padding(vertical = SpaceSaleSpacing.Xs),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = selected,
                                                onCheckedChange = { checked ->
                                                    selectedIds = if (checked) selectedIds + line.detailId else selectedIds - line.detailId
                                                }
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(line.productName, color = SpaceSaleColors.TextPrimary)
                                                Text("Cantidad ${line.quantity}", style = MaterialTheme.typography.caption, color = SpaceSaleColors.TextSecondary)
                                            }
                                            Text(line.pendingCents.formatPen(), color = SpaceSaleColors.TextPrimary)
                                        }
                                    }
                                }
                            }
                        }
                        if (state.payments.isNotEmpty()) {
                            item {
                                Text("Historial de abonos", style = MaterialTheme.typography.subtitle1, color = SpaceSaleColors.TextPrimary)
                            }
                            items(state.payments, key = { "payment-${it.id}" }) { payment ->
                                SpaceSaleCard(modifier = Modifier.fillMaxWidth(), containerColor = SpaceSaleColors.SurfaceRaised) {
                                    Row(
                                        modifier = Modifier.padding(SpaceSaleSpacing.Md),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Payments, contentDescription = null, tint = SpaceSaleColors.Success)
                                        Spacer(Modifier.width(SpaceSaleSpacing.Md))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(payment.productName ?: "Abono", color = SpaceSaleColors.TextPrimary)
                                            Text(
                                                "${payment.method} · ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(payment.paidAt))}",
                                                style = MaterialTheme.typography.caption,
                                                color = SpaceSaleColors.TextSecondary
                                            )
                                            if (payment.note.isNotBlank()) Text(payment.note, style = MaterialTheme.typography.caption)
                                        }
                                        Text(payment.amountCents.formatPen(), color = SpaceSaleColors.Success)
                                    }
                                }
                            }
                        }
                        item { Spacer(Modifier.size(SpaceSaleSpacing.Xl)) }
                    }
                }
            }
        }
    }

    if (showPaymentDialog) {
        PartialPaymentDialog(
            maximumCents = selectedTotal,
            onDismiss = { showPaymentDialog = false },
            onConfirm = { amount, method, note ->
                viewModel.payDebtAmount(clientId, selectedIds, amount, method, note) { error ->
                    errorMessage = error
                    if (error == null) {
                        selectedIds = emptySet()
                        showPaymentDialog = false
                    }
                }
            }
        )
    }
}

@Composable
private fun PartialPaymentDialog(
    maximumCents: Long,
    onDismiss: () -> Unit,
    onConfirm: (Double, String, String) -> Unit
) {
    var amount by rememberSaveable(maximumCents) { mutableStateOf((maximumCents / 100.0).toString()) }
    var method by rememberSaveable { mutableStateOf("EFECTIVO") }
    var note by rememberSaveable { mutableStateOf("") }
    val parsed = parseLocalizedDecimal(amount)
    val valid = parsed != null && parsed > 0.0 && parsed <= maximumCents / 100.0

    AlertDialog(
        onDismissRequest = onDismiss,
        backgroundColor = SpaceSaleColors.SurfaceRaised,
        title = { Text("Registrar abono", color = SpaceSaleColors.TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(SpaceSaleSpacing.Sm)) {
                Text("Saldo seleccionado: ${maximumCents.formatPen()}", color = SpaceSaleColors.TextSecondary)
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Monto") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = spaceSaleTextFieldColors()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(SpaceSaleSpacing.Xs)) {
                    listOf("EFECTIVO", "YAPE", "PLIN").forEach { value ->
                        TextButton(onClick = { method = value }) {
                            Text(value, color = if (method == value) SpaceSaleColors.Cyan else SpaceSaleColors.TextSecondary)
                        }
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(180) },
                    label = { Text("Nota opcional") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = spaceSaleTextFieldColors()
                )
                if (!valid) SpaceSaleInlineMessage("El monto debe estar entre S/ 0.01 y ${maximumCents.formatPen()}")
            }
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let { onConfirm(it, method, note) } }, enabled = valid) {
                Text("Guardar", color = SpaceSaleColors.Success)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
