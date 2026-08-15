package com.example.posapp.ui.screens

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.posapp.data.UserProfile
import com.example.posapp.ui.components.SpaceSaleCard
import com.example.posapp.ui.components.SpaceSaleInlineMessage
import com.example.posapp.ui.components.SpaceSalePrimaryButton
import com.example.posapp.ui.components.SpaceSaleScreenHeader
import com.example.posapp.ui.components.SpaceSaleSecondaryButton
import com.example.posapp.ui.components.spaceSaleTextFieldColors
import com.example.posapp.ui.theme.SpaceSaleColors
import com.example.posapp.ui.theme.SpaceSaleRadii
import com.example.posapp.ui.theme.SpaceSaleSizes
import com.example.posapp.ui.theme.SpaceSaleSpacing
import com.example.posapp.utils.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SetupScreen(
    existingProfile: UserProfile? = null,
    onDone: (UserProfile) -> Unit,
    onCancel: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    var business by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var logo by rememberSaveable { mutableStateOf("") }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(existingProfile) {
        existingProfile?.let {
            name = it.userName
            business = it.businessName
            address = it.address
            logo = it.logoPath.orEmpty()
        }
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) logo = uri.toString()
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { ImageUtils.saveBitmap(context, bitmap) } }
                    .onSuccess { logo = it; errorMessage = null }
                    .onFailure { errorMessage = it.message ?: "No se pudo guardar la foto" }
            }
        }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) camera.launch(null) else errorMessage = "Necesitamos permiso de camara para tomar la foto"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SpaceSaleColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SpaceSaleSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(SpaceSaleSpacing.Md)
    ) {
        SpaceSaleScreenHeader(
            title = if (existingProfile == null) "Configura tu perfil" else "Editar perfil",
            subtitle = "Space Labs aparece solo como marca creadora",
            onBack = onCancel
        )
        SpaceSaleCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(SpaceSaleSpacing.Lg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(SpaceSaleRadii.Large))
                        .background(SpaceSaleColors.CyanContainer),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        logo.isNotBlank() -> AsyncImage(
                            model = logo,
                            contentDescription = "Logo del negocio",
                            modifier = Modifier.fillMaxSize()
                        )
                        name.isNotBlank() -> Text(
                            name.first().uppercase(),
                            style = MaterialTheme.typography.h5,
                            color = SpaceSaleColors.Cyan
                        )
                        else -> Icon(Icons.Default.Image, contentDescription = null, tint = SpaceSaleColors.Cyan)
                    }
                }
                Spacer(Modifier.size(SpaceSaleSpacing.Md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(name.ifBlank { "Tu nombre" }, style = MaterialTheme.typography.subtitle1)
                    Text(business.ifBlank { "Nombre del negocio" }, color = SpaceSaleColors.TextSecondary)
                }
                if (logo.isNotBlank()) {
                    IconButton(onClick = { logo = "" }, modifier = Modifier.size(SpaceSaleSizes.TouchTarget)) {
                        Icon(Icons.Default.Close, contentDescription = "Quitar logo")
                    }
                }
            }
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; errorMessage = null },
            label = { Text("Tu nombre *") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = spaceSaleTextFieldColors()
        )
        OutlinedTextField(
            value = business,
            onValueChange = { business = it; errorMessage = null },
            label = { Text("Nombre del negocio *") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = spaceSaleTextFieldColors()
        )
        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            label = { Text("Direccion (opcional)") },
            modifier = Modifier.fillMaxWidth(),
            colors = spaceSaleTextFieldColors()
        )
        SpaceSaleSecondaryButton(onClick = { gallery.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.PhotoLibrary, contentDescription = null)
            Spacer(Modifier.size(SpaceSaleSpacing.Sm))
            Text("Elegir logo")
        }
        SpaceSaleSecondaryButton(
            onClick = {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
                if (granted) camera.launch(null) else cameraPermission.launch(Manifest.permission.CAMERA)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.CameraAlt, contentDescription = null)
            Spacer(Modifier.size(SpaceSaleSpacing.Sm))
            Text("Tomar foto")
        }
        errorMessage?.let { SpaceSaleInlineMessage(it) }
        SpaceSalePrimaryButton(
            onClick = {
                if (name.isBlank() || business.isBlank()) {
                    errorMessage = "Completa tu nombre y el nombre del negocio"
                } else {
                    onDone(
                        UserProfile(
                            userName = name.trim(),
                            businessName = business.trim(),
                            address = address.trim(),
                            logoPath = logo.trim().ifBlank { null }
                        )
                    )
                }
            },
            enabled = name.isNotBlank() && business.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (existingProfile == null) "Guardar y continuar" else "Guardar cambios")
        }
        onCancel?.let { cancel ->
            SpaceSaleSecondaryButton(onClick = cancel, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
        }
        Spacer(Modifier.size(SpaceSaleSpacing.Xl))
    }
}
