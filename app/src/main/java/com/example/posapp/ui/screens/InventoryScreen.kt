package com.example.posapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.AlertDialog
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Inventory2
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.posapp.data.entities.Producto
import com.example.posapp.ui.components.SpaceSaleCard
import com.example.posapp.ui.components.SpaceSaleEmptyState
import com.example.posapp.ui.components.SpaceSaleInlineMessage
import com.example.posapp.ui.components.SpaceSalePrimaryButton
import com.example.posapp.ui.components.SpaceSaleScreenHeader
import com.example.posapp.ui.components.SpaceSaleSearchField
import com.example.posapp.ui.components.SpaceSaleStatusPill
import com.example.posapp.ui.components.spaceSaleTextFieldColors
import com.example.posapp.ui.theme.SpaceSaleColors
import com.example.posapp.ui.theme.SpaceSaleRadii
import com.example.posapp.ui.theme.SpaceSaleSizes
import com.example.posapp.ui.theme.SpaceSaleSpacing
import com.example.posapp.utils.formatPen
import com.example.posapp.utils.parseLocalizedDecimal
import com.example.posapp.vm.InventoryViewModel

@Composable
fun InventoryScreen(
    navController: NavController,
    onMenuClick: (() -> Unit)? = null,
    viewModel: InventoryViewModel = viewModel()
) {
    val products by viewModel.filteredProductos.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var lowStockOnly by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Producto?>(null) }
    var stockDelta by rememberSaveable { mutableStateOf("0") }
    var newPrice by rememberSaveable { mutableStateOf("") }
    var movementType by rememberSaveable { mutableStateOf("PURCHASE") }
    var movementReason by rememberSaveable { mutableStateOf("") }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(query) { viewModel.setSearchQuery(query) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = SpaceSaleSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(SpaceSaleSpacing.Md)
    ) {
        SpaceSaleScreenHeader(
            title = "Inventario",
            subtitle = "${products.size} productos visibles",
            onMenu = onMenuClick
        )
        SpaceSalePrimaryButton(
            onClick = { navController.navigate("add_product") },
            modifier = Modifier.fillMaxWidth(),
            containerColor = SpaceSaleColors.Success,
            contentColor = SpaceSaleColors.OnSuccess,
            disabledContainerColor = SpaceSaleColors.SuccessContainer
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(SpaceSaleSpacing.Sm))
            Text("Agregar producto")
        }
        SpaceSaleSearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Buscar por nombre o codigo"
        )
        OutlinedButton(
            onClick = {
                lowStockOnly = !lowStockOnly
                viewModel.toggleLowStock()
            },
            modifier = Modifier.heightIn(min = SpaceSaleSizes.TouchTarget),
            shape = RoundedCornerShape(SpaceSaleRadii.Medium),
            colors = ButtonDefaults.outlinedButtonColors(
                backgroundColor = if (lowStockOnly) SpaceSaleColors.WarningContainer else SpaceSaleColors.Surface,
                contentColor = if (lowStockOnly) SpaceSaleColors.Warning else SpaceSaleColors.TextSecondary
            )
        ) {
            Icon(Icons.Default.FilterList, contentDescription = null)
            Spacer(Modifier.width(SpaceSaleSpacing.Sm))
            Text(if (lowStockOnly) "Mostrando stock bajo" else "Filtrar stock bajo")
        }
        errorMessage?.let { SpaceSaleInlineMessage(it) }

        if (products.isEmpty()) {
            SpaceSaleEmptyState(
                icon = Icons.Default.Inventory2,
                title = if (query.isBlank() && !lowStockOnly) "Tu inventario esta vacio" else "Sin resultados",
                description = if (query.isBlank() && !lowStockOnly) {
                    "Agrega tu primer producto para comenzar a vender."
                } else {
                    "Prueba otra busqueda o quita el filtro."
                },
                modifier = Modifier.weight(1f),
                action = if (query.isBlank() && !lowStockOnly) ({
                    SpaceSalePrimaryButton(onClick = { navController.navigate("add_product") }) {
                        Text("Crear producto")
                    }
                }) else null
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(SpaceSaleSpacing.Sm)
            ) {
                items(products, key = { it.id }) { product ->
                    ProductCard(product) {
                        selected = it
                        stockDelta = "0"
                        newPrice = ""
                        movementType = "PURCHASE"
                        movementReason = ""
                        errorMessage = null
                    }
                }
                item { Spacer(Modifier.size(SpaceSaleSpacing.Lg)) }
            }
        }
    }

    selected?.let { product ->
        StockDialog(
            product = product,
            stockDelta = stockDelta,
            onDeltaChange = { stockDelta = it; errorMessage = null },
            newPrice = newPrice,
            onPriceChange = { newPrice = it; errorMessage = null },
            movementType = movementType,
            onMovementTypeChange = { movementType = it; errorMessage = null },
            reason = movementReason,
            onReasonChange = { movementReason = it; errorMessage = null },
            errorMessage = errorMessage,
            onConfirm = {
                val delta = stockDelta.trim().toIntOrNull()
                val price = newPrice.takeIf(String::isNotBlank)?.let(::parseLocalizedDecimal)
                when {
                    delta == null -> errorMessage = "Ingresa una cantidad valida"
                    newPrice.isNotBlank() && price == null -> errorMessage = "Ingresa un precio valido"
                    price != null && price <= 0.0 -> errorMessage = "El precio debe ser mayor que cero"
                    movementType == "PURCHASE" && delta <= 0 -> errorMessage = "Una compra debe aumentar el stock"
                    movementType in setOf("LOSS", "DAMAGE", "EXPIRED") && delta >= 0 -> errorMessage = "Este movimiento debe reducir el stock"
                    movementType != "PURCHASE" && movementReason.isBlank() -> errorMessage = "Explica el motivo del ajuste"
                    else -> viewModel.addStock(product.id, delta, price, movementType, movementReason) { error ->
                        errorMessage = error
                        if (error == null) selected = null
                    }
                }
            },
            onDismiss = { selected = null; errorMessage = null }
        )
    }
}

@Composable
fun ProductCard(producto: Producto, onClick: (Producto) -> Unit = {}) {
    SpaceSaleCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(producto) }
    ) {
        Row(
            modifier = Modifier.padding(SpaceSaleSpacing.Md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!producto.ruta_imagen.isNullOrBlank()) {
                AsyncImage(
                    model = producto.ruta_imagen,
                    contentDescription = "Foto de ${producto.nombre}",
                    modifier = Modifier
                        .size(SpaceSaleSizes.TouchTarget)
                        .clip(RoundedCornerShape(SpaceSaleRadii.Medium))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(SpaceSaleSizes.TouchTarget)
                        .clip(RoundedCornerShape(SpaceSaleRadii.Medium))
                        .background(SpaceSaleColors.CyanContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Inventory2, contentDescription = null, tint = SpaceSaleColors.Cyan)
                }
            }
            Spacer(Modifier.width(SpaceSaleSpacing.Md))
            Column(modifier = Modifier.weight(1f)) {
                Text(producto.nombre, style = MaterialTheme.typography.subtitle1, color = SpaceSaleColors.TextPrimary)
                Text(
                    producto.precio_venta_centavos.formatPen(),
                    style = MaterialTheme.typography.body1,
                    color = SpaceSaleColors.Cyan,
                    fontWeight = FontWeight.SemiBold
                )
            }
            SpaceSaleStatusPill(
                text = "Stock ${producto.stock}",
                icon = Icons.Default.Inventory2,
                foreground = if (producto.stock <= producto.stock_minimo) SpaceSaleColors.Warning else SpaceSaleColors.TextSecondary,
                background = if (producto.stock <= producto.stock_minimo) SpaceSaleColors.WarningContainer else SpaceSaleColors.SurfaceRaised
            )
        }
    }
}

@Composable
private fun StockDialog(
    product: Producto,
    stockDelta: String,
    onDeltaChange: (String) -> Unit,
    newPrice: String,
    onPriceChange: (String) -> Unit,
    movementType: String,
    onMovementTypeChange: (String) -> Unit,
    reason: String,
    onReasonChange: (String) -> Unit,
    errorMessage: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        backgroundColor = SpaceSaleColors.SurfaceRaised,
        shape = RoundedCornerShape(SpaceSaleRadii.Large),
        title = { Text("Actualizar inventario", color = SpaceSaleColors.TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(SpaceSaleSpacing.Md)) {
                Text(product.nombre, style = MaterialTheme.typography.subtitle1, color = SpaceSaleColors.Cyan)
                Row(horizontalArrangement = Arrangement.spacedBy(SpaceSaleSpacing.Xs)) {
                    listOf("PURCHASE" to "Compra", "ADJUSTMENT" to "Ajuste", "LOSS" to "Perdida").forEach { (value, label) ->
                        TextButton(onClick = { onMovementTypeChange(value) }) {
                            Text(label, color = if (movementType == value) SpaceSaleColors.Cyan else SpaceSaleColors.TextSecondary)
                        }
                    }
                }
                OutlinedTextField(
                    value = stockDelta,
                    onValueChange = onDeltaChange,
                    label = { Text("Cantidad: usa - para quitar") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = spaceSaleTextFieldColors()
                )
                OutlinedTextField(
                    value = newPrice,
                    onValueChange = onPriceChange,
                    label = { Text("Nuevo precio (opcional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = spaceSaleTextFieldColors()
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = onReasonChange,
                    label = { Text(if (movementType == "PURCHASE") "Proveedor o nota (opcional)" else "Motivo") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = spaceSaleTextFieldColors()
                )
                errorMessage?.let { SpaceSaleInlineMessage(it) }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.heightIn(min = SpaceSaleSizes.TouchTarget)) {
                Text("Guardar", color = SpaceSaleColors.Cyan)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = SpaceSaleSizes.TouchTarget)) {
                Text("Cancelar", color = SpaceSaleColors.TextSecondary)
            }
        }
    )
}
