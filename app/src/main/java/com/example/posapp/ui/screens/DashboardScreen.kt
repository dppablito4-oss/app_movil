package com.example.posapp.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.posapp.ui.components.SpaceSaleCard
import com.example.posapp.ui.components.SpaceSalePrimaryButton
import com.example.posapp.ui.components.SpaceSaleSecondaryButton
import com.example.posapp.ui.components.SpaceSaleStatusPill
import com.example.posapp.ui.theme.SpaceSaleColors
import com.example.posapp.ui.theme.SpaceSaleRadii
import com.example.posapp.ui.theme.SpaceSaleSizes
import com.example.posapp.ui.theme.SpaceSaleSpacing
import com.example.posapp.ui.theme.SpaceSaleTheme
import com.example.posapp.utils.BarcodeScanBus
import com.example.posapp.utils.SpaceSaleBarcodeScanner
import com.example.posapp.vm.DashboardViewModel
import com.example.posapp.vm.RecentSale
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

private const val DAILY_GOAL = 500.0

@Composable
fun DashboardScreen(
    navController: NavController,
    dashboardViewModel: DashboardViewModel = viewModel(),
    userName: String = "Usuario",
    businessName: String = "SpaceSale",
    logoPath: String? = null,
    onMenuClick: () -> Unit = {},
    onProfileClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val ventasHoy by dashboardViewModel.ventasHoy.collectAsState()
    val ventasAyer by dashboardViewModel.ventasAyer.collectAsState()
    val porCobrar by dashboardViewModel.porCobrar.collectAsState()
    val gananciaHoy by dashboardViewModel.gananciaHoy.collectAsState()
    val cantidadVentasHoy by dashboardViewModel.cantidadVentasHoy.collectAsState()
    val productosStockBajo by dashboardViewModel.productosStockBajo.collectAsState()
    val recentSales by dashboardViewModel.recentSales.collectAsState()

    val barcodeScanner = remember(context) {
        (context as? android.app.Activity)?.let(::SpaceSaleBarcodeScanner)
    }

    DashboardContent(
        userName = userName,
        businessName = businessName,
        logoPath = logoPath,
        ventasHoy = ventasHoy,
        ventasAyer = ventasAyer,
        porCobrar = porCobrar,
        gananciaHoy = gananciaHoy,
        cantidadVentasHoy = cantidadVentasHoy,
        productosStockBajo = productosStockBajo,
        recentSales = recentSales,
        onMenuClick = onMenuClick,
        onProfileClick = onProfileClick,
        onNewSale = { navController.navigate("sales") { launchSingleTop = true } },
        onScan = {
            barcodeScanner?.start(
                onResult = { code ->
                    BarcodeScanBus.publish(code)
                    navController.navigate("sales") { launchSingleTop = true }
                },
                onError = { message -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
            ) ?: Toast.makeText(context, "No se pudo iniciar el escaner", Toast.LENGTH_SHORT).show()
        },
        onAddProduct = { navController.navigate("add_product") },
        onAddClient = { navController.navigate("add_client") },
        onOpenInventory = { navController.navigate("inventory") { launchSingleTop = true } }
    )
}

@Composable
private fun DashboardContent(
    userName: String,
    businessName: String,
    logoPath: String?,
    ventasHoy: Double,
    ventasAyer: Double,
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
    onAddClient: () -> Unit,
    onOpenInventory: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(SpaceSaleColors.Background)
    ) {
        val fontScale = LocalDensity.current.fontScale
        val compactLayout = maxWidth < 400.dp || fontScale >= 1.3f
        val horizontalPadding = if (maxWidth >= 600.dp) SpaceSaleSpacing.Xl else SpaceSaleSpacing.Lg

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = horizontalPadding,
                top = SpaceSaleSpacing.Md,
                end = horizontalPadding,
                bottom = SpaceSaleSpacing.Xl
            ),
            verticalArrangement = Arrangement.spacedBy(SpaceSaleSpacing.Xl)
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
            item {
                DailySalesCard(
                    ventasHoy = ventasHoy,
                    ventasAyer = ventasAyer,
                    goal = DAILY_GOAL,
                    salesCount = cantidadVentasHoy,
                    estimatedProfit = gananciaHoy,
                    compactLayout = compactLayout
                )
            }
            item {
                SummarySection(
                    porCobrar = porCobrar,
                    stockBajo = productosStockBajo.size,
                    compactLayout = compactLayout
                )
            }
            item {
                QuickActionsSection(
                    compactLayout = compactLayout,
                    onNewSale = onNewSale,
                    onAddProduct = onAddProduct,
                    onAddClient = onAddClient,
                    onScan = onScan
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
}

@Composable
private fun DashboardHeader(
    userName: String,
    businessName: String,
    logoPath: String?,
    onMenuClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(SpaceSaleSpacing.Sm)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = SpaceSaleSizes.TouchTarget),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier.size(SpaceSaleSizes.TouchTarget)
            ) {
                if (!logoPath.isNullOrBlank()) {
                    AsyncImage(
                        model = logoPath,
                        contentDescription = "Abrir menú",
                        modifier = Modifier
                            .size(SpaceSaleSizes.Logo)
                            .clip(RoundedCornerShape(SpaceSaleRadii.Medium))
                    )
                } else {
                    Surface(
                        color = SpaceSaleColors.CyanContainer,
                        shape = RoundedCornerShape(SpaceSaleRadii.Medium),
                        modifier = Modifier.size(SpaceSaleSizes.Logo)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Storefront,
                                contentDescription = "Abrir menú",
                                tint = SpaceSaleColors.Cyan,
                                modifier = Modifier.size(SpaceSaleSizes.IconMedium)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(SpaceSaleSpacing.Md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Hola, ${userName.ifBlank { "Usuario" }}",
                    style = MaterialTheme.typography.body2,
                    color = SpaceSaleColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = businessName.ifBlank { "SpaceSale" },
                    style = MaterialTheme.typography.subtitle1,
                    color = SpaceSaleColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(SpaceSaleSpacing.Sm))
            IconButton(
                onClick = onProfileClick,
                modifier = Modifier
                    .size(SpaceSaleSizes.TouchTarget)
                    .semantics { contentDescription = "Abrir perfil de $userName" }
            ) {
                Surface(
                    color = SpaceSaleColors.VioletContainer,
                    border = BorderStroke(1.dp, SpaceSaleColors.Violet.copy(alpha = 0.55f)),
                    shape = CircleShape,
                    modifier = Modifier.size(SpaceSaleSizes.Logo)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (userName.isNotBlank()) {
                            Text(
                                userName.first().uppercase(),
                                style = MaterialTheme.typography.subtitle1,
                                color = SpaceSaleColors.TextPrimary
                            )
                        } else {
                            Icon(Icons.Default.Person, contentDescription = null, tint = SpaceSaleColors.TextPrimary)
                        }
                    }
                }
            }
        }
        SpaceSaleStatusPill(
            text = "Datos en este dispositivo",
            icon = Icons.Default.PhoneAndroid
        )
    }
}

@Composable
private fun DailySalesCard(
    ventasHoy: Double,
    ventasAyer: Double,
    goal: Double,
    salesCount: Int,
    estimatedProfit: Double,
    compactLayout: Boolean
) {
    val progress = if (goal > 0) (ventasHoy / goal).coerceIn(0.0, 1.0).toFloat() else 0f
    val percent = (progress * 100).toInt()
    val comparison = salesComparison(ventasHoy, ventasAyer)

    SpaceSaleCard(
        borderColor = SpaceSaleColors.Violet.copy(alpha = 0.55f),
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
    ) {
        Column(
            modifier = Modifier.padding(SpaceSaleSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(SpaceSaleSpacing.Md)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "VENTA TOTAL DE HOY",
                    style = MaterialTheme.typography.overline,
                    color = SpaceSaleColors.TextSecondary,
                    modifier = Modifier.weight(1f)
                )
                SpaceSaleStatusPill(
                    text = "$percent% de la meta",
                    icon = Icons.Default.TrendingUp,
                    foreground = SpaceSaleColors.VioletContent,
                    background = SpaceSaleColors.VioletContainer
                )
            }
            Text(
                currency(ventasHoy),
                style = MaterialTheme.typography.h4,
                color = SpaceSaleColors.TextPrimary
            )
            if (compactLayout) {
                Column(verticalArrangement = Arrangement.spacedBy(SpaceSaleSpacing.Sm)) {
                    ComparisonLine(comparison)
                    SupportingMetric(
                        label = if (salesCount == 1) "1 venta" else "$salesCount ventas",
                        value = "Ganancia est. ${currency(estimatedProfit)}",
                        valueColor = if (estimatedProfit >= 0) SpaceSaleColors.Success else SpaceSaleColors.Warning
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ComparisonLine(comparison)
                    SupportingMetric(
                        label = if (salesCount == 1) "1 venta" else "$salesCount ventas",
                        value = "Ganancia est. ${currency(estimatedProfit)}",
                        valueColor = if (estimatedProfit >= 0) SpaceSaleColors.Success else SpaceSaleColors.Warning,
                        alignEnd = true
                    )
                }
            }
            LinearProgressIndicator(
                progress = progress,
                color = SpaceSaleColors.Violet,
                backgroundColor = SpaceSaleColors.SurfaceRaised,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(SpaceSaleRadii.Small))
                    .semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f) }
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Meta diaria", style = MaterialTheme.typography.body2, color = SpaceSaleColors.TextSecondary)
                Text(currency(goal), style = MaterialTheme.typography.subtitle2, color = SpaceSaleColors.TextPrimary)
            }
        }
    }
}

private data class SalesComparison(
    val label: String,
    val icon: ImageVector,
    val color: Color
)

private fun salesComparison(today: Double, yesterday: Double): SalesComparison {
    if (yesterday <= 0.0) {
        return SalesComparison(
            label = "Ayer: ${currency(yesterday)}",
            icon = Icons.Default.Remove,
            color = SpaceSaleColors.TextSecondary
        )
    }
    val percentage = ((today - yesterday) / yesterday) * 100
    return when {
        percentage > 0.05 -> SalesComparison(
            label = "+${abs(percentage).toInt()}% vs. ayer",
            icon = Icons.Default.TrendingUp,
            color = SpaceSaleColors.Success
        )
        percentage < -0.05 -> SalesComparison(
            label = "-${abs(percentage).toInt()}% vs. ayer",
            icon = Icons.Default.TrendingDown,
            color = SpaceSaleColors.TextSecondary
        )
        else -> SalesComparison(
            label = "Sin cambios vs. ayer",
            icon = Icons.Default.Remove,
            color = SpaceSaleColors.TextSecondary
        )
    }
}

@Composable
private fun ComparisonLine(comparison: SalesComparison) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(SpaceSaleSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            comparison.icon,
            contentDescription = null,
            tint = comparison.color,
            modifier = Modifier.size(SpaceSaleSizes.IconSmall)
        )
        Text(comparison.label, style = MaterialTheme.typography.body2, color = comparison.color)
    }
}

@Composable
private fun SupportingMetric(
    label: String,
    value: String,
    valueColor: Color,
    alignEnd: Boolean = false
) {
    Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(label, style = MaterialTheme.typography.caption, color = SpaceSaleColors.TextSecondary)
        Text(value, style = MaterialTheme.typography.subtitle2, color = valueColor)
    }
}

@Composable
private fun SummarySection(porCobrar: Double, stockBajo: Int, compactLayout: Boolean) {
    Section(title = "Lo importante") {
        SpaceSaleCard(modifier = Modifier.fillMaxWidth()) {
            if (compactLayout) {
                Column {
                    SummaryMetric(
                        label = "Total por cobrar",
                        value = currency(porCobrar),
                        icon = Icons.Default.AccountBalanceWallet,
                        accent = if (porCobrar > 0) SpaceSaleColors.Warning else SpaceSaleColors.Success,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Divider(color = SpaceSaleColors.Border)
                    SummaryMetric(
                        label = "Productos con stock bajo",
                        value = stockBajo.toString(),
                        icon = Icons.Default.Inventory2,
                        accent = if (stockBajo > 0) SpaceSaleColors.Warning else SpaceSaleColors.Success,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth()) {
                    SummaryMetric(
                        label = "Total por cobrar",
                        value = currency(porCobrar),
                        icon = Icons.Default.AccountBalanceWallet,
                        accent = if (porCobrar > 0) SpaceSaleColors.Warning else SpaceSaleColors.Success,
                        modifier = Modifier.weight(1f)
                    )
                    Divider(
                        color = SpaceSaleColors.Border,
                        modifier = Modifier
                            .width(1.dp)
                            .height(88.dp)
                    )
                    SummaryMetric(
                        label = "Productos con stock bajo",
                        value = stockBajo.toString(),
                        icon = Icons.Default.Inventory2,
                        accent = if (stockBajo > 0) SpaceSaleColors.Warning else SpaceSaleColors.Success,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryMetric(
    label: String,
    value: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .heightIn(min = 88.dp)
            .padding(SpaceSaleSpacing.Md)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(SpaceSaleSpacing.Md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = accent.copy(alpha = 0.14f),
            shape = RoundedCornerShape(SpaceSaleRadii.Medium),
            modifier = Modifier.size(SpaceSaleSizes.Logo)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(SpaceSaleSizes.IconSmall))
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(value, style = MaterialTheme.typography.h6, color = SpaceSaleColors.TextPrimary)
            Text(label, style = MaterialTheme.typography.body2, color = SpaceSaleColors.TextSecondary)
        }
    }
}

@Composable
private fun QuickActionsSection(
    compactLayout: Boolean,
    onNewSale: () -> Unit,
    onAddProduct: () -> Unit,
    onAddClient: () -> Unit,
    onScan: () -> Unit
) {
    Section(title = "Acciones rápidas") {
        SpaceSalePrimaryButton(
            onClick = onNewSale,
            modifier = Modifier.fillMaxWidth(),
            containerColor = SpaceSaleColors.Success,
            contentColor = SpaceSaleColors.OnSuccess,
            disabledContainerColor = SpaceSaleColors.SuccessContainer
        ) {
            Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(SpaceSaleSizes.IconMedium))
            Spacer(Modifier.width(SpaceSaleSpacing.Sm))
            Text("Nueva venta")
        }
        if (compactLayout) {
            Column(verticalArrangement = Arrangement.spacedBy(SpaceSaleSpacing.Sm)) {
                SecondaryAction("Agregar producto", Icons.Default.AddBox, onAddProduct, Modifier.fillMaxWidth())
                SecondaryAction("Agregar cliente", Icons.Default.PersonAdd, onAddClient, Modifier.fillMaxWidth())
                SecondaryAction("Escanear código", Icons.Default.QrCodeScanner, onScan, Modifier.fillMaxWidth())
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(SpaceSaleSpacing.Sm)) {
                SecondaryAction("Agregar producto", Icons.Default.AddBox, onAddProduct, Modifier.weight(1f))
                SecondaryAction("Agregar cliente", Icons.Default.PersonAdd, onAddClient, Modifier.weight(1f))
                SecondaryAction("Escanear", Icons.Default.QrCodeScanner, onScan, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SecondaryAction(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier) {
    SpaceSaleSecondaryButton(onClick = onClick, modifier = modifier) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(SpaceSaleSizes.IconSmall), tint = SpaceSaleColors.Cyan)
        Spacer(Modifier.width(SpaceSaleSpacing.Xs))
        Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@Composable
private fun LowStockAlert(productNames: List<String>, onClick: () -> Unit) {
    val preview = productNames.take(3).joinToString(", ")
    Surface(
        color = SpaceSaleColors.WarningContainer,
        border = BorderStroke(1.dp, SpaceSaleColors.Warning.copy(alpha = 0.55f)),
        shape = RoundedCornerShape(SpaceSaleRadii.Medium),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SpaceSaleSizes.TouchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "${productNames.size} productos con stock bajo. Abrir inventario"
            }
    ) {
        Row(
            modifier = Modifier.padding(SpaceSaleSpacing.Md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.WarningAmber, contentDescription = null, tint = SpaceSaleColors.Warning)
            Spacer(Modifier.width(SpaceSaleSpacing.Md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Revisa ${productNames.size} productos con stock bajo",
                    style = MaterialTheme.typography.subtitle2,
                    color = SpaceSaleColors.TextPrimary
                )
                Text(
                    preview,
                    style = MaterialTheme.typography.body2,
                    color = SpaceSaleColors.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = SpaceSaleColors.Warning)
        }
    }
}

@Composable
private fun RecentSalesSection(recentSales: List<RecentSale>) {
    Section(title = "Ventas recientes") {
        SpaceSaleCard(modifier = Modifier.fillMaxWidth()) {
            if (recentSales.isEmpty()) {
                EmptySalesState()
            } else {
                Column {
                    recentSales.forEachIndexed { index, sale ->
                        RecentSaleRow(sale)
                        if (index < recentSales.lastIndex) {
                            Divider(
                                color = SpaceSaleColors.Border,
                                modifier = Modifier.padding(horizontal = SpaceSaleSpacing.Md)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentSaleRow(sale: RecentSale) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .padding(SpaceSaleSpacing.Md)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = SpaceSaleColors.CyanContainer,
            shape = CircleShape,
            modifier = Modifier.size(SpaceSaleSizes.Logo)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    paymentIcon(sale.paymentMethod),
                    contentDescription = null,
                    tint = SpaceSaleColors.Cyan,
                    modifier = Modifier.size(SpaceSaleSizes.IconSmall)
                )
            }
        }
        Spacer(Modifier.width(SpaceSaleSpacing.Md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                sale.productName,
                style = MaterialTheme.typography.subtitle2,
                color = SpaceSaleColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${paymentLabel(sale.paymentMethod)} · ${relativeTimeText(sale.fechaMillis)}",
                style = MaterialTheme.typography.caption,
                color = SpaceSaleColors.TextSecondary,
                maxLines = 2
            )
        }
        Spacer(Modifier.width(SpaceSaleSpacing.Sm))
        Text(
            currency(sale.total),
            style = MaterialTheme.typography.subtitle1,
            color = if (sale.paymentMethod.equals("FIADO", ignoreCase = true)) {
                SpaceSaleColors.Warning
            } else {
                SpaceSaleColors.Success
            },
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun EmptySalesState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(SpaceSaleSpacing.Xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SpaceSaleSpacing.Sm)
    ) {
        Surface(
            color = SpaceSaleColors.SurfaceRaised,
            shape = CircleShape,
            modifier = Modifier.size(SpaceSaleSizes.TouchTarget)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.ReceiptLong,
                    contentDescription = null,
                    tint = SpaceSaleColors.TextSecondary,
                    modifier = Modifier.size(SpaceSaleSizes.IconMedium)
                )
            }
        }
        Text("Aún no hay ventas", style = MaterialTheme.typography.subtitle1, color = SpaceSaleColors.TextPrimary)
        Text(
            "Las ventas recientes aparecerán aquí.",
            style = MaterialTheme.typography.body2,
            color = SpaceSaleColors.TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SpaceSaleSpacing.Md)) {
        Text(title, style = MaterialTheme.typography.h6, color = SpaceSaleColors.TextPrimary)
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
    RecentSale(2, 42.00, System.currentTimeMillis() - 3_600_000, "Arroz extra", "YAPE"),
    RecentSale(3, 25.00, System.currentTimeMillis() - 7_200_000, "Aceite vegetal", "FIADO")
)

@Preview(name = "Teléfono pequeño", widthDp = 320, heightDp = 640, showBackground = true)
@Composable
private fun DashboardSmallPreview() {
    SpaceSaleTheme {
        DashboardContent(
            userName = "Pablo",
            businessName = "Bodega San Martín",
            logoPath = null,
            ventasHoy = 326.50,
            ventasAyer = 284.00,
            porCobrar = 84.00,
            gananciaHoy = 91.20,
            cantidadVentasHoy = 17,
            productosStockBajo = listOf("Leche", "Aceite", "Azúcar"),
            recentSales = previewSales,
            onMenuClick = {},
            onProfileClick = {},
            onNewSale = {},
            onScan = {},
            onAddProduct = {},
            onAddClient = {},
            onOpenInventory = {}
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
