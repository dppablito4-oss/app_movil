package com.example.posapp.ui.screens

import android.Manifest
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.posapp.ui.components.SpaceSaleCard
import com.example.posapp.ui.components.SpaceSaleInlineMessage
import com.example.posapp.ui.components.SpaceSalePrimaryButton
import com.example.posapp.ui.components.SpaceSaleScreenHeader
import com.example.posapp.ui.components.SpaceSaleSecondaryButton
import com.example.posapp.ui.components.spaceSaleTextFieldColors
import com.example.posapp.ui.theme.SpaceSaleColors
import com.example.posapp.ui.theme.SpaceSaleRadii
import com.example.posapp.ui.theme.SpaceSaleSpacing
import com.example.posapp.utils.ImageUtils
import com.example.posapp.utils.BarcodeDraftStore
import com.example.posapp.utils.parseLocalizedDecimal
import com.example.posapp.vm.InventoryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AddProductScreen(navController: NavController, viewModel: InventoryViewModel = viewModel()) {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf("") }
    var priceText by rememberSaveable { mutableStateOf("") }
    var stockText by rememberSaveable { mutableStateOf("") }
    var minimumStockText by rememberSaveable { mutableStateOf("5") }
    var barcode by rememberSaveable { mutableStateOf(BarcodeDraftStore.take()) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var imagePath by rememberSaveable { mutableStateOf<String?>(null) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) selectedUri = uri
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) capturedBitmap = bitmap
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) cameraLauncher.launch(null) else errorMessage = "Necesitamos permiso de camara para tomar la foto"
    }

    LaunchedEffect(selectedUri) {
        selectedUri?.let { uri ->
            runCatching { withContext(Dispatchers.IO) { ImageUtils.saveOptimizedImage(context, uri) } }
                .onSuccess { imagePath = it; errorMessage = null }
                .onFailure { errorMessage = it.message ?: "No se pudo guardar la imagen" }
        }
    }
    LaunchedEffect(capturedBitmap) {
        capturedBitmap?.let { bitmap ->
            runCatching { withContext(Dispatchers.IO) { ImageUtils.saveBitmap(context, bitmap) } }
                .onSuccess { imagePath = it; errorMessage = null }
                .onFailure { errorMessage = it.message ?: "No se pudo guardar la foto" }
            capturedBitmap = null
        }
    }

    fun save() {
        val price = parseLocalizedDecimal(priceText)
        val stock = stockText.trim().toIntOrNull()
        val minimumStock = minimumStockText.trim().toIntOrNull()
        when {
            name.isBlank() -> errorMessage = "El nombre es obligatorio"
            price == null || price <= 0.0 -> errorMessage = "Ingresa un precio valido mayor que cero"
            stock == null || stock < 0 -> errorMessage = "Ingresa una cantidad de stock valida"
            minimumStock == null || minimumStock < 0 -> errorMessage = "Ingresa un stock minimo valido"
            else -> viewModel.addProduct(name, price, stock, imagePath, barcode, minimumStock) { error ->
                errorMessage = error
                if (error == null) navController.popBackStack()
            }
        }
    }

    Scaffold(backgroundColor = SpaceSaleColors.Background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SpaceSaleSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(SpaceSaleSpacing.Md)
        ) {
            SpaceSaleScreenHeader(
                title = "Nuevo producto",
                subtitle = "La foto es opcional y se comprime automaticamente",
                onBack = { navController.popBackStack() }
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; errorMessage = null },
                label = { Text("Nombre *") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
                colors = spaceSaleTextFieldColors()
            )
            OutlinedTextField(
                value = priceText,
                onValueChange = { priceText = it; errorMessage = null },
                label = { Text("Precio de venta *") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
                colors = spaceSaleTextFieldColors()
            )
            OutlinedTextField(
                value = stockText,
                onValueChange = { stockText = it; errorMessage = null },
                label = { Text("Stock inicial *") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
                colors = spaceSaleTextFieldColors()
            )
            OutlinedTextField(
                value = barcode,
                onValueChange = { barcode = it.trim(); errorMessage = null },
                label = { Text("Codigo de barras") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
                colors = spaceSaleTextFieldColors()
            )
            OutlinedTextField(
                value = minimumStockText,
                onValueChange = { minimumStockText = it; errorMessage = null },
                label = { Text("Avisar cuando queden") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
                colors = spaceSaleTextFieldColors()
            )

            SpaceSaleCard(modifier = Modifier.fillMaxWidth(), containerColor = SpaceSaleColors.SurfaceRaised) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .padding(SpaceSaleSpacing.Sm),
                    contentAlignment = Alignment.Center
                ) {
                    if (imagePath != null) {
                        AsyncImage(
                            model = imagePath,
                            contentDescription = "Vista previa del producto",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(SpaceSaleRadii.Medium))
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Inventory2, contentDescription = null, tint = SpaceSaleColors.TextMuted)
                            Text("Sin foto", color = SpaceSaleColors.TextSecondary)
                        }
                    }
                }
            }
            SpaceSaleSecondaryButton(
                onClick = { galleryLauncher.launch("image/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                Spacer(Modifier.padding(SpaceSaleSpacing.Xs))
                Text("Elegir de la galeria")
            }
            SpaceSaleSecondaryButton(
                onClick = {
                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (granted) cameraLauncher.launch(null) else permissionLauncher.launch(Manifest.permission.CAMERA)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null)
                Spacer(Modifier.padding(SpaceSaleSpacing.Xs))
                Text("Tomar foto")
            }
            errorMessage?.let { SpaceSaleInlineMessage(it) }
            SpaceSalePrimaryButton(
                onClick = ::save,
                modifier = Modifier.fillMaxWidth(),
                containerColor = SpaceSaleColors.Success,
                contentColor = SpaceSaleColors.OnSuccess,
                disabledContainerColor = SpaceSaleColors.SuccessContainer
            ) {
                Text("Guardar producto")
            }
            Spacer(Modifier.padding(SpaceSaleSpacing.Lg))
        }
    }
}
