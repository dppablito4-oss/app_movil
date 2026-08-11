package com.example.posapp.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.core.content.ContextCompat
import android.Manifest
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.OutlinedTextField
import androidx.compose.ui.text.input.*
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.posapp.utils.ImageUtils
import com.example.posapp.vm.InventoryViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AddProductScreen(navController: NavController, viewModel: InventoryViewModel = viewModel()) {
    var nombre by remember { mutableStateOf("") }
    var precioText by remember { mutableStateOf("") }
    var stockText by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var imagePath by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    var hasCameraPermission by remember { mutableStateOf(
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
    ) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) imageUri = uri
    }

    val requestPermissionLauncher = rememberLauncherForActivityResult(RequestPermission()) { granted: Boolean ->
        hasCameraPermission = granted
    }

    val takePicturePreviewLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp: Bitmap? ->
        if (bmp != null) capturedBitmap = bmp
    }

    LaunchedEffect(imageUri) {
        imageUri?.let { uri ->
            val path = withContext(Dispatchers.IO) {
                ImageUtils.saveOptimizedImage(context, uri)
            }
            imagePath = path
        }
    }

    LaunchedEffect(capturedBitmap) {
        capturedBitmap?.let { bmp ->
            val path = withContext(Dispatchers.IO) {
                ImageUtils.saveBitmap(context, bmp)
            }
            imagePath = path
            capturedBitmap = null
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(value = nombre, onValueChange = { nombre = it }, label = { Text("Nombre") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = precioText, onValueChange = { precioText = it }, label = { Text("Precio") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = stockText, onValueChange = { stockText = it }, label = { Text("Stock") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(12.dp))

        if (imagePath != null) {
            AsyncImage(model = imagePath, contentDescription = "imagen", modifier = Modifier.size(120.dp))
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = { pickLauncher.launch("image/*") }) { Text("Seleccionar imagen") }
            Button(onClick = {
                if (!hasCameraPermission) {
                    requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                } else {
                    takePicturePreviewLauncher.launch(null)
                }
            }) { Text("Tomar foto (cámara)") }
            Button(onClick = {
                val precio = precioText.toDoubleOrNull() ?: 0.0
                val stock = stockText.toIntOrNull() ?: 0
                viewModel.addProduct(nombre, precio, stock, imagePath)
                navController.popBackStack()
            }) { Text("Guardar") }
        }
    }
}
