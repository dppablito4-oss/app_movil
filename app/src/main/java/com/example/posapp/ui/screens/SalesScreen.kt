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
import androidx.compose.material.AlertDialog
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.RadioButton
import androidx.compose.material.Scaffold
import androidx.compose.material.SnackbarResult
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.posapp.data.entities.Cliente
import com.example.posapp.data.entities.Producto
import com.example.posapp.ui.components.SpaceSaleCard
import com.example.posapp.ui.components.SpaceSaleEmptyState
import com.example.posapp.ui.components.SpaceSaleInlineMessage
import com.example.posapp.ui.components.SpaceSalePrimaryButton
import com.example.posapp.ui.components.SpaceSaleScreenHeader
import com.example.posapp.ui.components.SpaceSaleSearchField
import com.example.posapp.ui.components.spaceSaleTextFieldColors
import com.example.posapp.ui.theme.SpaceSaleColors
import com.example.posapp.ui.theme.SpaceSaleRadii
import com.example.posapp.ui.theme.SpaceSaleSizes
import com.example.posapp.ui.theme.SpaceSaleSpacing
import com.example.posapp.utils.formatPen
import com.example.posapp.utils.BarcodeDraftStore
import com.example.posapp.utils.BarcodeScanBus
import com.example.posapp.utils.ReceiptShare
import com.example.posapp.vm.BarcodeAddResult
import com.example.posapp.vm.ClienteViewModel
import com.example.posapp.vm.SalesViewModel
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SalesScreen(
    navController: NavController? = null,
    businessName: String = "SpaceSale",
    onMenuClick: (() -> Unit)? = null,
    viewModel: SalesViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val searchResults by viewModel.searchResults.collectAsState()
    val cart by viewModel.cart.collectAsState()
    val isCheckoutInProgress by viewModel.isCheckoutInProgress.collectAsState()
    val context = LocalContext.current
    val clientViewModel: ClienteViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val clients by clientViewModel.clientes.collectAsState()
    val scaffoldState = androidx.compose.material.rememberScaffoldState()
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var showPaymentDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(query) {
        delay(300)
        viewModel.search(query)
    }

    LaunchedEffect(Unit) {
        BarcodeScanBus.scans.collect { code ->
            query = code
            when (viewModel.addScannedBarcode(code)) {
                BarcodeAddResult.ADDED -> scaffoldState.snackbarHostState.showSnackbar("Producto agregado al carrito")
                BarcodeAddResult.OUT_OF_STOCK -> scaffoldState.snackbarHostState.showSnackbar("El producto no tiene stock disponible")
                BarcodeAddResult.NOT_FOUND -> {
                    val result = scaffoldState.snackbarHostState.showSnackbar(
                        message = "El codigo $code no existe",
                        actionLabel = "Crear"
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        BarcodeDraftStore.set(code)
                        navController?.navigate("add_product")
                    }
                }
            }
        }
    }

    Scaffold(
        scaffoldState = scaffoldState,
        backgroundColor = SpaceSaleColors.Background,
        bottomBar = {
            if (cart.isNotEmpty()) {
                SpaceSaleCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SpaceSaleSpacing.Lg, vertical = SpaceSaleSpacing.Sm),
                    containerColor = SpaceSaleColors.SurfaceRaised
                ) {
                    Row(
                        modifier = Modifier.padding(SpaceSaleSpacing.Md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(SpaceSaleSpacing.Md)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Total", style = MaterialTheme.typography.caption, color = SpaceSaleColors.TextSecondary)
                            Text(
                                viewModel.totalCents().formatPen(),
                                style = MaterialTheme.typography.h6,
                                color = SpaceSaleColors.TextPrimary
                            )
                        }
                        SpaceSalePrimaryButton(
                            onClick = { showPaymentDialog = true },
                            containerColor = SpaceSaleColors.Success,
                            contentColor = SpaceSaleColors.OnSuccess,
                            disabledContainerColor = SpaceSaleColors.SuccessContainer
                        ) {
                            Text("Cobrar")
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = SpaceSaleSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(SpaceSaleSpacing.Md)
        ) {
            SpaceSaleScreenHeader(
                title = "Nueva venta",
                subtitle = if (cart.isEmpty()) "Selecciona productos" else "${cart.sumOf { it.cantidad }} articulos en el carrito",
                onMenu = onMenuClick
            )
            SpaceSaleSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Buscar producto"
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(SpaceSaleSpacing.Sm)
            ) {
                if (cart.isNotEmpty()) {
                    item {
                        Text("Carrito", style = MaterialTheme.typography.subtitle1, color = SpaceSaleColors.TextPrimary)
                    }
                    items(cart, key = { "cart-${it.producto.id}" }) { item ->
                        CartLine(
                            name = item.producto.nombre,
                            quantity = item.cantidad,
                            amount = Math.multiplyExact(
                                item.producto.precio_venta_centavos,
                                item.cantidad.toLong()
                            ).formatPen(),
                            onDecrease = { viewModel.decQuantity(item.producto.id) },
                            onIncrease = {
                                if (!viewModel.incQuantity(item.producto.id)) {
                                    scope.launch { scaffoldState.snackbarHostState.showSnackbar("No hay mas stock disponible") }
                                }
                            }
                        )
                    }
                    item {
                        Text("Productos", style = MaterialTheme.typography.subtitle1, color = SpaceSaleColors.TextPrimary)
                    }
                }

                if (searchResults.isEmpty()) {
                    item {
                        SpaceSaleEmptyState(
                            icon = Icons.Default.Inventory2,
                            title = if (query.isBlank()) "No hay productos disponibles" else "No encontramos productos",
                            description = if (query.isBlank()) {
                                "Agrega productos desde Inventario antes de registrar una venta."
                            } else {
                                "Prueba con otro nombre o revisa el inventario."
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    items(searchResults, key = { "product-${it.id}" }) { product ->
                        val quantity = cart.firstOrNull { it.producto.id == product.id }?.cantidad ?: 0
                        ProductRowForSale(
                            producto = product,
                            cantidadInicial = quantity,
                            onImageNeeded = { viewModel.ensureImageCached(product.id) },
                            onAdd = {
                                if (!viewModel.addToCart(product)) {
                                    scope.launch { scaffoldState.snackbarHostState.showSnackbar("No hay stock disponible") }
                                }
                            },
                            onInc = {
                                if (!viewModel.incQuantity(product.id)) {
                                    scope.launch { scaffoldState.snackbarHostState.showSnackbar("No hay mas stock disponible") }
                                }
                            },
                            onDec = { viewModel.decQuantity(product.id) }
                        )
                    }
                }
                item { Spacer(Modifier.size(SpaceSaleSpacing.Lg)) }
            }
        }
    }

    if (showPaymentDialog) {
        PaymentDialog(
            clientes = clients,
            isSubmitting = isCheckoutInProgress,
            onDismiss = { showPaymentDialog = false },
            onConfirm = { paymentType, clientId ->
                viewModel.checkout(paymentType, clientId) { success, error ->
                    scope.launch {
                        val result = scaffoldState.snackbarHostState.showSnackbar(
                            message = if (success) "Venta registrada" else error ?: "No se pudo registrar la venta",
                            actionLabel = if (success) "Compartir" else null
                        )
                        if (success && result == SnackbarResult.ActionPerformed) {
                            viewModel.receiptForShare()?.let { ReceiptShare.sharePdf(context, businessName, it) }
                        }
                    }
                }
                if (!isCheckoutInProgress) showPaymentDialog = false
            },
            onAddClient = { name, onAdded ->
                clientViewModel.addCliente(nombre = name.trim(), onComplete = onAdded)
            }
        )
    }
}

@Composable
private fun CartLine(
    name: String,
    quantity: Int,
    amount: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit
) {
    SpaceSaleCard(modifier = Modifier.fillMaxWidth(), containerColor = SpaceSaleColors.SurfaceRaised) {
        Row(
            modifier = Modifier.padding(SpaceSaleSpacing.Md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.subtitle2, color = SpaceSaleColors.TextPrimary)
                Text(amount, style = MaterialTheme.typography.body2, color = SpaceSaleColors.Cyan)
            }
            QuantityControl(quantity, onDecrease, onIncrease)
        }
    }
}

@Composable
fun ProductRowForSale(
    producto: Producto,
    cantidadInicial: Int = 0,
    onImageNeeded: () -> Unit = {},
    onAdd: () -> Unit,
    onInc: () -> Unit = {},
    onDec: () -> Unit = {}
) {
    LaunchedEffect(producto.id, producto.storage_path, producto.ruta_imagen) {
        if (!producto.storage_path.isNullOrBlank() && producto.ruta_imagen?.let(::File)?.isFile != true) {
            onImageNeeded()
        }
    }
    SpaceSaleCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = cantidadInicial == 0 && producto.stock > 0, onClick = onAdd)
                .padding(SpaceSaleSpacing.Md),
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
                Text(producto.precio_venta_centavos.formatPen(), color = SpaceSaleColors.Cyan, fontWeight = FontWeight.SemiBold)
                Text(
                    if (producto.stock > 0) "${producto.stock} disponibles" else "Sin stock",
                    style = MaterialTheme.typography.caption,
                    color = if (producto.stock > 0) SpaceSaleColors.TextSecondary else SpaceSaleColors.Error
                )
            }
            if (cantidadInicial <= 0) {
                IconButton(
                    onClick = onAdd,
                    enabled = producto.stock > 0,
                    modifier = Modifier.size(SpaceSaleSizes.TouchTarget)
                ) {
                    Icon(Icons.Default.AddShoppingCart, contentDescription = "Agregar ${producto.nombre}")
                }
            } else {
                QuantityControl(cantidadInicial, onDec, onInc)
            }
        }
    }
}

@Composable
private fun QuantityControl(quantity: Int, onDecrease: () -> Unit, onIncrease: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(SpaceSaleRadii.Medium))
            .background(SpaceSaleColors.CyanContainer)
    ) {
        IconButton(onClick = onDecrease, modifier = Modifier.size(SpaceSaleSizes.TouchTarget)) {
            Icon(Icons.Default.Remove, contentDescription = "Quitar uno", tint = SpaceSaleColors.Cyan)
        }
        Text(quantity.toString(), color = SpaceSaleColors.TextPrimary, fontWeight = FontWeight.Bold)
        IconButton(onClick = onIncrease, modifier = Modifier.size(SpaceSaleSizes.TouchTarget)) {
            Icon(Icons.Default.Add, contentDescription = "Agregar uno", tint = SpaceSaleColors.Cyan)
        }
    }
}

@Composable
fun PaymentDialog(
    clientes: List<Cliente>,
    isSubmitting: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (String, Long?) -> Unit,
    onAddClient: (String, (Long) -> Unit) -> Unit
) {
    var selectedMethod by rememberSaveable { mutableStateOf("EFECTIVO") }
    var clientsExpanded by remember { mutableStateOf(false) }
    var selectedClientId by rememberSaveable { mutableStateOf<Long?>(null) }
    var newClientName by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        backgroundColor = SpaceSaleColors.SurfaceRaised,
        shape = RoundedCornerShape(SpaceSaleRadii.Large),
        title = { Text("Como pagara?", color = SpaceSaleColors.TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(SpaceSaleSpacing.Sm)) {
                listOf("EFECTIVO" to "Efectivo", "YAPE" to "Yape o Plin", "FIADO" to "Fiado").forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = SpaceSaleSizes.TouchTarget)
                            .clickable { selectedMethod = value },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedMethod == value, onClick = { selectedMethod = value })
                        Text(label, color = SpaceSaleColors.TextPrimary)
                    }
                }

                if (selectedMethod == "FIADO") {
                    Box {
                        OutlinedButton(
                            onClick = { clientsExpanded = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = SpaceSaleSizes.TouchTarget)
                        ) {
                            Text(
                                selectedClientId?.let { id -> clientes.firstOrNull { it.id == id }?.nombre }
                                    ?: "Seleccionar cliente"
                            )
                        }
                        DropdownMenu(expanded = clientsExpanded, onDismissRequest = { clientsExpanded = false }) {
                            clientes.forEach { client ->
                                DropdownMenuItem(onClick = {
                                    selectedClientId = client.id
                                    clientsExpanded = false
                                }) { Text(client.nombre) }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = newClientName,
                        onValueChange = { newClientName = it },
                        label = { Text("Crear cliente rapido") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = spaceSaleTextFieldColors()
                    )
                    OutlinedButton(
                        onClick = {
                            val name = newClientName.trim()
                            if (name.isNotEmpty()) {
                                onAddClient(name) { newId ->
                                    selectedClientId = newId
                                    newClientName = ""
                                }
                            }
                        },
                        enabled = newClientName.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = SpaceSaleSizes.TouchTarget)
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null)
                        Spacer(Modifier.width(SpaceSaleSpacing.Sm))
                        Text("Crear y seleccionar")
                    }
                    if (selectedClientId == null) {
                        SpaceSaleInlineMessage("Selecciona o crea un cliente para registrar el fiado.")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedMethod, selectedClientId.takeIf { selectedMethod == "FIADO" }) },
                enabled = !isSubmitting && (selectedMethod != "FIADO" || selectedClientId != null),
                modifier = Modifier.heightIn(min = SpaceSaleSizes.TouchTarget)
            ) {
                Icon(Icons.Default.ShoppingCart, contentDescription = null)
                Spacer(Modifier.width(SpaceSaleSpacing.Xs))
                Text(if (isSubmitting) "Procesando..." else "Confirmar", color = SpaceSaleColors.Success)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = SpaceSaleSizes.TouchTarget)) {
                Text("Cancelar", color = SpaceSaleColors.TextSecondary)
            }
        }
    )
}
