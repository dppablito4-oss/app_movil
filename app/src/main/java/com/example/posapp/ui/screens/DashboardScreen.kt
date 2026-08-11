package com.example.posapp.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.posapp.ui.theme.PablitoColors
import com.example.posapp.ui.theme.PablitoRadii
import com.example.posapp.ui.theme.PablitoSizes
import com.example.posapp.ui.theme.PablitoSpacing
import com.example.posapp.ui.theme.PablitoTheme
import com.example.posapp.utils.ImageUtils
import com.example.posapp.vm.DashboardViewModel
import com.example.posapp.vm.RecentSale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val DAILY_GOAL = 500.0

@Composable
fun DashboardScreen(
    navController: NavController,
    dashboardViewModel: DashboardViewModel = viewModel(),
    userName: String = "Usuario",
    businessName: String = "Pablito Fast",
    logoPath: String? = null,
    onMenuClick: () -> Unit = {},
    onProfileClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ventasHoy by dashboardViewModel.ventasHoy.collectAsState()
    val porCobrar by dashboardViewModel.porCobrar.collectAsState()
    val gananciaHoy by dashboardViewModel.gananciaHoy.collectAsState()
    val cantidadVentasHoy by dashboardViewModel.cantidadVentasHoy.collectAsState()
    val productosStockBajo by dashboardViewModel.productosStockBajo.collectAsState()
    val recentSales by dashboardViewModel.recentSales.collectAsState()

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        if (bitmap != null) {
            scope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) { ImageUtils.saveBitmap(context, bitmap) }
                }
                Toast.makeText(
                    context,
                    if (result.isSuccess) "Captura guardada" else "No se pudo abrir la cámara",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    val requestCameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) takePicture.launch(null)
        else Toast.makeText(context, "Permiso de cámara denegado", Toast.LENGTH_SHORT).show()
    }

    DashboardContent(
        userName = userName,
        businessName = businessName,
        logoPath = logoPath,
        ventasHoy = ventasHoy,
        porCobrar = porCobrar,
        gananciaHoy = gananciaHoy,
        cantidadVentasHoy = cantidadVentasHoy,
        productosStockBajo = productosStockBajo,
        recentSales = recentSales,
        onMenuClick = onMenuClick,
        onProfileClick = onProfileClick,
        onNewSale = { navController.navigate("sales") { launchSingleTop = true } },
        onScan = {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                takePicture.launch(null)
            } else {
                requestCameraPermission.launch(Manifest.permission.CAMERA)
            }
        },
        onAddProduct = { navController.navigate("add_product") },
        onOpenInventory = { navController.navigate("inventory") { launchSingleTop = true } }
    )
}

@Composable
private fun DashboardContent(
    userName: String,
    businessName: String,
    logoPath: String?,
    ventasHoy: Double,
    porCobrar: Double,
    gananciaHoy: Double,
    cantidadVentasHoy: Int,
    productosStockBajo: List<String>,
    recentSales: List<RecentSale>,
    onMenuClick: () -> Unit,
    onProfileClick: () -> Unit,
    onNewSale: () -> Unit,
    onScan: () -> Unit,
    onAddProduct: () -> Unit,
    onOpenInventory: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(PablitoColors.Background),
        contentPadding = PaddingValues(
            start = PablitoSpacing.Lg,
            top = PablitoSpacing.Md,
            end = PablitoSpacing.Lg,
            bottom = PablitoSpacing.Xxl
        ),
        verticalArrangement = Arrangement.spacedBy(PablitoSpacing.Xl)
    ) {
        item {
            DashboardHeader(
                userName = userName,
                businessName = businessName,
                logoPath = logoPath,
                onMenuClick = onMenuClick,
                onProfileClick = onProfileClick
            )
        }
        item { DailySalesCard(ventasHoy = ventasHoy, goal = DAILY_GOAL) }
        item {
            MetricsSection(
                porCobrar = porCobrar,
                gananciaHoy = gananciaHoy,
                cantidadVentasHoy = cantidadVentasHoy,
                stockBajo = productosStockBajo.size
            )
        }
        item {
            QuickActionsSection(
                onNewSale = onNewSale,
                onScan = onScan,
                onAddProduct = onAddProduct
            )
        }
        if (productosStockBajo.isNotEmpty()) {
            item {
                LowStockAlert(
                    productNames = productosStockBajo,
                    onClick = onOpenInventory
                )
            }
        }
        item { RecentSalesSection(recentSales) }
    }
}

@Composable
private fun DashboardHeader(
    userName: String,
    businessName: String,
    logoPath: String?,
    onMenuClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = PablitoSizes.TouchTarget),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onMenuClick,
            modifier = Modifier.size(PablitoSizes.TouchTarget)
        ) {
            if (!logoPath.isNullOrBlank()) {
                AsyncImage(
                    model = logoPath,
                    contentDescription = "Abrir menú",
                    modifier = Modifier.size(PablitoSizes.Logo).clip(RoundedCornerShape(PablitoRadii.Medium))
                )
            } else {
                Surface(
                    color = PablitoColors.CyanContainer,
                    shape = RoundedCornerShape(PablitoRadii.Medium),
                    modifier = Modifier.size(PablitoSizes.Logo)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Storefront,
                            contentDescription = "Abrir menú",
                            tint = PablitoColors.Cyan,
                            modifier = Modifier.size(PablitoSizes.IconMedium)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(PablitoSpacing.Md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = businessName.ifBlank { "Pablito Fast" },
                style = MaterialTheme.typography.subtitle1,
                color = PablitoColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Hola, ${userName.ifBlank { "Usuario" }}",
                style = MaterialTheme.typography.body2,
                color = PablitoColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(PablitoSpacing.Sm))
        IconButton(
            onClick = onProfileClick,
            modifier = Modifier.size(PablitoSizes.TouchTarget)
                .semantics { contentDescription = "Editar perfil de $userName" }
        ) {
            Surface(
                color = PablitoColors.SurfaceElevated,
                border = BorderStroke(1.dp, PablitoColors.Border),
                shape = CircleShape,
                modifier = Modifier.size(PablitoSizes.Logo)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (userName.isNotBlank()) {
                        Text(
                            userName.first().uppercase(),
                            style = MaterialTheme.typography.subtitle1,
                            color = PablitoColors.Cyan
                        )
                    } else {
                        Icon(Icons.Default.Person, contentDescription = null, tint = PablitoColors.Cyan)
                    }
                }
            }
        }
    }
}

@Composable
private fun DailySalesCard(ventasHoy: Double, goal: Double) {
    val progress = if (goal > 0) (ventasHoy / goal).coerceIn(0.0, 1.0).toFloat() else 0f
    val percent = (progress * 100).toInt()
    Card(
        backgroundColor = PablitoColors.Surface,
        border = BorderStroke(1.dp, PablitoColors.Border),
        elevation = 0.dp,
        shape = RoundedCornerShape(PablitoRadii.Large),
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}
    ) {
        Column(
            modifier = Modifier.padding(PablitoSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(PablitoSpacing.Md)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "VENTAS DE HOY",
                    style = MaterialTheme.typography.overline,
                    color = PablitoColors.TextSecondary,
                    modifier = Modifier.weight(1f)
                )
                Surface(color = PablitoColors.CyanContainer, shape = RoundedCornerShape(PablitoRadii.Small)) {
                    Text(
                        "$percent% de la meta",
                        style = MaterialTheme.typography.caption,
                        color = PablitoColors.Cyan,
                        modifier = Modifier.padding(horizontal = PablitoSpacing.Sm, vertical = PablitoSpacing.Xs)
                    )
                }
            }
            Text(
                currency(ventasHoy),
                style = MaterialTheme.typography.h4,
                color = PablitoColors.TextPrimary
            )
            LinearProgressIndicator(
                progress = progress,
                color = PablitoColors.Cyan,
                backgroundColor = PablitoColors.SurfaceElevated,
                modifier = Modifier.fillMaxWidth().height(8.dp)
                    .clip(RoundedCornerShape(PablitoRadii.Small))
                    .semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f) }
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Meta diaria", style = MaterialTheme.typography.body2, color = PablitoColors.TextSecondary)
                Text(currency(goal), style = MaterialTheme.typography.subtitle2, color = PablitoColors.TextPrimary)
            }
        }
    }
}

@Composable
private fun MetricsSection(
    porCobrar: Double,
    gananciaHoy: Double,
    cantidadVentasHoy: Int,
    stockBajo: Int
) {
    Section(title = "Resumen") {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val useSingleColumn = maxWidth < 340.dp || LocalDensity.current.fontScale >= 1.35f
            val metrics = listOf(
                MetricData("Por cobrar", currency(porCobrar), Icons.Default.AccountBalanceWallet, PablitoColors.Warning),
                MetricData("Ganancia estimada", currency(gananciaHoy), Icons.Default.TrendingUp, if (gananciaHoy >= 0) PablitoColors.Success else PablitoColors.Error),
                MetricData("Número de ventas", cantidadVentasHoy.toString(), Icons.Default.ReceiptLong, PablitoColors.Cyan),
                MetricData("Stock bajo", stockBajo.toString(), Icons.Default.Inventory2, if (stockBajo > 0) PablitoColors.Error else PablitoColors.Success)
            )
            if (useSingleColumn) {
                Column(verticalArrangement = Arrangement.spacedBy(PablitoSpacing.Sm)) {
                    metrics.forEach { MetricCard(it, Modifier.fillMaxWidth()) }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(PablitoSpacing.Sm)) {
                    metrics.chunked(2).forEach { rowMetrics ->
                        Row(horizontalArrangement = Arrangement.spacedBy(PablitoSpacing.Sm)) {
                            rowMetrics.forEach { MetricCard(it, Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

private data class MetricData(val label: String, val value: String, val icon: ImageVector, val accent: Color)

@Composable
private fun MetricCard(metric: MetricData, modifier: Modifier = Modifier) {
    Card(
        backgroundColor = PablitoColors.Surface,
        border = BorderStroke(1.dp, PablitoColors.Border),
        elevation = 0.dp,
        shape = RoundedCornerShape(PablitoRadii.Medium),
        modifier = modifier.heightIn(min = 96.dp).semantics(mergeDescendants = true) {}
    ) {
        Column(
            modifier = Modifier.padding(PablitoSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(PablitoSpacing.Sm)
        ) {
            Icon(metric.icon, contentDescription = null, tint = metric.accent, modifier = Modifier.size(PablitoSizes.IconSmall))
            Text(metric.value, style = MaterialTheme.typography.h6, color = PablitoColors.TextPrimary)
            Text(metric.label, style = MaterialTheme.typography.body2, color = PablitoColors.TextSecondary)
        }
    }
}

@Composable
private fun QuickActionsSection(onNewSale: () -> Unit, onScan: () -> Unit, onAddProduct: () -> Unit) {
    Section(title = "Acciones rápidas") {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val stackActions = maxWidth < 340.dp || LocalDensity.current.fontScale >= 1.35f
            val actions = listOf(
                QuickActionData("Nueva venta", Icons.Default.ShoppingCart, true, onNewSale),
                QuickActionData("Escanear código", Icons.Default.QrCodeScanner, false, onScan),
                QuickActionData("Agregar producto", Icons.Default.AddBox, false, onAddProduct)
            )
            if (stackActions) {
                Column(verticalArrangement = Arrangement.spacedBy(PablitoSpacing.Sm)) {
                    actions.forEach { QuickAction(it, Modifier.fillMaxWidth(), horizontal = true) }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(PablitoSpacing.Sm)) {
                    actions.forEach { QuickAction(it, Modifier.weight(1f), horizontal = false) }
                }
            }
        }
    }
}

private data class QuickActionData(
    val label: String,
    val icon: ImageVector,
    val highlighted: Boolean,
    val onClick: () -> Unit
)

@Composable
private fun QuickAction(action: QuickActionData, modifier: Modifier, horizontal: Boolean) {
    val background = if (action.highlighted) PablitoColors.Magenta else PablitoColors.Surface
    val foreground = if (action.highlighted) MaterialTheme.colors.onSecondary else PablitoColors.Cyan
    Surface(
        color = background,
        border = if (action.highlighted) null else BorderStroke(1.dp, PablitoColors.Border),
        shape = RoundedCornerShape(PablitoRadii.Medium),
        modifier = modifier.heightIn(min = if (horizontal) PablitoSizes.TouchTarget else 96.dp)
            .clickable(role = Role.Button, onClick = action.onClick)
            .semantics(mergeDescendants = true) { contentDescription = action.label }
    ) {
        if (horizontal) {
            Row(
                modifier = Modifier.padding(horizontal = PablitoSpacing.Lg, vertical = PablitoSpacing.Md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PablitoSpacing.Md)
            ) {
                Icon(action.icon, contentDescription = null, tint = foreground, modifier = Modifier.size(PablitoSizes.IconMedium))
                Text(action.label, style = MaterialTheme.typography.button, color = if (action.highlighted) MaterialTheme.colors.onSecondary else PablitoColors.TextPrimary)
            }
        } else {
            Column(
                modifier = Modifier.padding(PablitoSpacing.Md),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(PablitoSpacing.Sm)
            ) {
                Icon(action.icon, contentDescription = null, tint = foreground, modifier = Modifier.size(PablitoSizes.IconMedium))
                Text(
                    action.label,
                    style = MaterialTheme.typography.button,
                    color = if (action.highlighted) MaterialTheme.colors.onSecondary else PablitoColors.TextPrimary,
                    textAlign = TextAlign.Center,
                    minLines = 2
                )
            }
        }
    }
}

@Composable
private fun LowStockAlert(productNames: List<String>, onClick: () -> Unit) {
    val preview = productNames.take(3).joinToString(", ")
    Surface(
        color = PablitoColors.MagentaContainer,
        border = BorderStroke(1.dp, PablitoColors.Magenta.copy(alpha = 0.55f)),
        shape = RoundedCornerShape(PablitoRadii.Medium),
        modifier = Modifier.fillMaxWidth().sizeIn(minHeight = PablitoSizes.TouchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "${productNames.size} productos con stock bajo. Abrir inventario"
            }
    ) {
        Row(
            modifier = Modifier.padding(PablitoSpacing.Md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.WarningAmber, contentDescription = null, tint = PablitoColors.Magenta)
            Spacer(Modifier.width(PablitoSpacing.Md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${productNames.size} productos con stock bajo",
                    style = MaterialTheme.typography.subtitle2,
                    color = PablitoColors.TextPrimary
                )
                Text(
                    preview,
                    style = MaterialTheme.typography.body2,
                    color = PablitoColors.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(Icons.Default.ArrowForward, contentDescription = null, tint = PablitoColors.TextPrimary)
        }
    }
}

@Composable
private fun RecentSalesSection(recentSales: List<RecentSale>) {
    Section(title = "Últimas ventas") {
        if (recentSales.isEmpty()) {
            EmptySalesState()
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(PablitoSpacing.Sm)) {
                recentSales.forEach { RecentSaleRow(it) }
            }
        }
    }
}

@Composable
private fun RecentSaleRow(sale: RecentSale) {
    Card(
        backgroundColor = PablitoColors.Surface,
        border = BorderStroke(1.dp, PablitoColors.Border),
        elevation = 0.dp,
        shape = RoundedCornerShape(PablitoRadii.Medium),
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}
    ) {
        Row(
            modifier = Modifier.padding(PablitoSpacing.Md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(color = PablitoColors.CyanContainer, shape = CircleShape, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(paymentIcon(sale.paymentMethod), contentDescription = null, tint = PablitoColors.Cyan, modifier = Modifier.size(PablitoSizes.IconSmall))
                }
            }
            Spacer(Modifier.width(PablitoSpacing.Md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    sale.productName,
                    style = MaterialTheme.typography.subtitle2,
                    color = PablitoColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${paymentLabel(sale.paymentMethod)} · ${relativeTimeText(sale.fechaMillis)}",
                    style = MaterialTheme.typography.caption,
                    color = PablitoColors.TextSecondary,
                    maxLines = 2
                )
            }
            Spacer(Modifier.width(PablitoSpacing.Sm))
            Text(
                currency(sale.total),
                style = MaterialTheme.typography.subtitle1,
                color = PablitoColors.Success,
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
private fun EmptySalesState() {
    Surface(
        color = PablitoColors.Surface,
        border = BorderStroke(1.dp, PablitoColors.Border),
        shape = RoundedCornerShape(PablitoRadii.Medium),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(PablitoSpacing.Xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PablitoSpacing.Sm)
        ) {
            Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = PablitoColors.TextSecondary, modifier = Modifier.size(PablitoSizes.IconLarge))
            Text("Aún no hay ventas", style = MaterialTheme.typography.subtitle1, color = PablitoColors.TextPrimary)
            Text("Las ventas recientes aparecerán aquí.", style = MaterialTheme.typography.body2, color = PablitoColors.TextSecondary, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(PablitoSpacing.Md)) {
        Text(title, style = MaterialTheme.typography.h6, color = PablitoColors.TextPrimary)
        content()
    }
}

private fun currency(amount: Double): String = String.format(Locale.getDefault(), "S/ %,.2f", amount)

private fun paymentLabel(method: String): String = when (method.uppercase(Locale.ROOT)) {
    "EFECTIVO" -> "Efectivo"
    "YAPE" -> "Yape / Plin"
    "FIADO" -> "Fiado"
    else -> method.lowercase(Locale.getDefault()).replaceFirstChar { it.titlecase(Locale.getDefault()) }
}

private fun paymentIcon(method: String): ImageVector = when (method.uppercase(Locale.ROOT)) {
    "EFECTIVO" -> Icons.Default.Payments
    "YAPE" -> Icons.Default.AccountBalanceWallet
    "FIADO" -> Icons.Default.ReceiptLong
    else -> Icons.Default.Payments
}

private fun relativeTimeText(epochMillis: Long): String {
    val minutes = ((System.currentTimeMillis() - epochMillis).coerceAtLeast(0) / TimeUnit.MINUTES.toMillis(1)).toInt()
    return when {
        minutes < 1 -> "Ahora"
        minutes < 60 -> "Hace $minutes min"
        minutes < 60 * 24 -> "Hace ${minutes / 60} h"
        else -> "Hace ${minutes / (60 * 24)} d"
    }
}

private val previewSales = listOf(
    RecentSale(1, 18.50, System.currentTimeMillis() - 120_000, "Leche evaporada", "EFECTIVO"),
    RecentSale(2, 42.00, System.currentTimeMillis() - 3_600_000, "Arroz extra", "YAPE")
)

@Preview(name = "Teléfono pequeño", widthDp = 320, heightDp = 640, showBackground = true)
@Composable
private fun DashboardSmallPreview() {
    PablitoTheme {
        DashboardContent(
            userName = "Pablo",
            businessName = "Bodega San Martín",
            logoPath = null,
            ventasHoy = 326.50,
            porCobrar = 84.00,
            gananciaHoy = 91.20,
            cantidadVentasHoy = 17,
            productosStockBajo = listOf("Leche", "Aceite", "Azúcar"),
            recentSales = previewSales,
            onMenuClick = {}, onProfileClick = {}, onNewSale = {}, onScan = {}, onAddProduct = {}, onOpenInventory = {}
        )
    }
}

@Preview(name = "Texto grande", widthDp = 360, heightDp = 800, fontScale = 1.5f, showBackground = true)
@Composable
private fun DashboardLargeTextPreview() {
    DashboardSmallPreview()
}

@Preview(name = "Horizontal", widthDp = 640, heightDp = 360, showBackground = true)
@Composable
private fun DashboardLandscapePreview() {
    DashboardSmallPreview()
}
