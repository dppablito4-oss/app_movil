package com.example.posapp.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.posapp.ui.components.SpaceSaleCard
import com.example.posapp.ui.components.SpaceSalePrimaryButton
import com.example.posapp.ui.components.SpaceSaleScreenHeader
import com.example.posapp.ui.theme.SpaceSaleColors
import com.example.posapp.ui.theme.SpaceSaleSpacing
import com.example.posapp.utils.CsvExportUtils
import com.example.posapp.utils.formatPen
import com.example.posapp.vm.ReportPeriod
import com.example.posapp.vm.ReportViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ReportScreen(
    onMenuClick: (() -> Unit)? = null,
    viewModel: ReportViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) scope.launch {
            val error = runCatching {
                withContext(Dispatchers.IO) { CsvExportUtils.exportSales(context, uri) }
            }.exceptionOrNull()?.message
            Toast.makeText(context, error ?: "Reporte CSV guardado", Toast.LENGTH_LONG).show()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = SpaceSaleSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(SpaceSaleSpacing.Md)
    ) {
        SpaceSaleScreenHeader(title = "Reportes", subtitle = "Resumen con importes exactos", onMenu = onMenuClick)
        Row(horizontalArrangement = Arrangement.spacedBy(SpaceSaleSpacing.Xs)) {
            listOf(
                ReportPeriod.TODAY to "Hoy",
                ReportPeriod.WEEK to "Semana",
                ReportPeriod.MONTH to "Mes"
            ).forEach { (period, label) ->
                TextButton(onClick = { viewModel.setPeriod(period) }) {
                    Text(label, color = if (state.period == period) SpaceSaleColors.Cyan else SpaceSaleColors.TextSecondary)
                }
            }
        }
        ReportMetric("Ventas", state.salesCents.formatPen(), "${state.saleCount} operaciones")
        ReportMetric("Ganancia estimada", state.estimatedProfitCents.formatPen(), "Basada en el costo historico")
        ReportMetric("Fiado creado", state.creditCreatedCents.formatPen(), "Recuperado: ${state.recoveredCents.formatPen()}")

        SpaceSaleCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(SpaceSaleSpacing.Lg), verticalArrangement = Arrangement.spacedBy(SpaceSaleSpacing.Sm)) {
                Text("Metodos de pago", style = MaterialTheme.typography.subtitle1, color = SpaceSaleColors.TextPrimary)
                if (state.paymentMethods.isEmpty()) Text("Sin ventas en este periodo", color = SpaceSaleColors.TextSecondary)
                state.paymentMethods.forEach { (method, amount) ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(method, color = SpaceSaleColors.TextSecondary)
                        Text(amount.formatPen(), color = SpaceSaleColors.TextPrimary)
                    }
                }
            }
        }
        SpaceSaleCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(SpaceSaleSpacing.Lg), verticalArrangement = Arrangement.spacedBy(SpaceSaleSpacing.Sm)) {
                Text("Productos mas vendidos", style = MaterialTheme.typography.subtitle1, color = SpaceSaleColors.TextPrimary)
                if (state.topProducts.isEmpty()) Text("Sin productos vendidos", color = SpaceSaleColors.TextSecondary)
                state.topProducts.forEach { (name, quantity) ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(name, color = SpaceSaleColors.TextSecondary)
                        Text(quantity.toString(), color = SpaceSaleColors.Cyan)
                    }
                }
            }
        }
        SpaceSalePrimaryButton(
            onClick = { exportLauncher.launch("spacesale_ventas.csv") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.FileDownload, contentDescription = null)
            Spacer(Modifier.size(SpaceSaleSpacing.Sm))
            Text("Exportar ventas CSV")
        }
        Spacer(Modifier.size(SpaceSaleSpacing.Xl))
    }
}

@Composable
private fun ReportMetric(label: String, value: String, supporting: String) {
    SpaceSaleCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(SpaceSaleSpacing.Lg)) {
            Text(label, color = SpaceSaleColors.TextSecondary)
            Text(value, style = MaterialTheme.typography.h5, color = SpaceSaleColors.TextPrimary)
            Text(supporting, style = MaterialTheme.typography.caption, color = SpaceSaleColors.TextMuted)
        }
    }
}
