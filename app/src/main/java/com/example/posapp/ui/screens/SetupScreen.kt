package com.example.posapp.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.posapp.data.UserProfile
import com.example.posapp.utils.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalContext

@Composable
fun SetupScreen(
    existingProfile: UserProfile? = null,
    onDone: (UserProfile) -> Unit,
    onCancel: (() -> Unit)? = null
) {
    var name = rememberSaveable { mutableStateOf("") }
    var business = rememberSaveable { mutableStateOf("") }
    var address = rememberSaveable { mutableStateOf("") }
    var logo = rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current

    LaunchedEffect(existingProfile) {
        existingProfile?.let {
            name.value = it.userName
            business.value = it.businessName
            address.value = it.address
            logo.value = it.logoPath.orEmpty()
        }
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        logo.value = uri?.toString().orEmpty()
    }
    val scope = rememberCoroutineScope()
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        if (bmp != null) {
            scope.launch {
                val path = withContext(Dispatchers.IO) { ImageUtils.saveBitmap(context, bmp) }
                logo.value = path.orEmpty()
            }
        }
    }

    val neon = Color(0xFF00E5FF)
    val magenta = Color(0xFFFF2D92)
    val bgGradient = Brush.verticalGradient(colors = listOf(Color(0xFF0A0E1A), Color(0xFF0F172A)))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient)
            .padding(16.dp)
    ) {
        TopAppBar(
            title = { Text(text = if (existingProfile == null) "Configura tu espacio" else "Editar perfil", color = Color.White) },
            backgroundColor = Color.Transparent,
            contentColor = Color.White,
            elevation = 0.dp
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Arma tu identidad ciberpunk",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Nombre, negocio, dirección y tu logo neón",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, neon.copy(alpha = 0.4f)),
            backgroundColor = Color(0xFF0F172A),
            elevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Avatar/logo preview
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF111827)),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            logo.value.isNotBlank() -> {
                                AsyncImage(
                                    model = logo.value,
                                    contentDescription = "Logo",
                                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                                )
                            }
                            name.value.isNotBlank() -> {
                                Text(name.value.first().uppercase(), color = neon, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            }
                            else -> {
                                Icon(Icons.Default.Image, contentDescription = null, tint = neon)
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(if (name.value.isBlank()) "Tu nombre" else name.value, color = Color.White, fontWeight = FontWeight.Bold)
                        Text(if (business.value.isBlank()) "Negocio" else business.value, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    }

                    IconButton(onClick = { logo.value = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Borrar logo", tint = Color.White.copy(alpha = 0.6f))
                    }
                }

                OutlinedTextField(
                    value = name.value,
                    onValueChange = { name.value = it },
                    label = { Text("Tu nombre", color = Color.White) },
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = Color.White,
                        cursorColor = neon,
                        focusedBorderColor = neon,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.4f),
                        focusedLabelColor = neon,
                        unfocusedLabelColor = Color.White.copy(alpha = 0.7f)
                    )
                )
                OutlinedTextField(
                    value = business.value,
                    onValueChange = { business.value = it },
                    label = { Text("Nombre de la tienda/negocio", color = Color.White) },
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = Color.White,
                        cursorColor = neon,
                        focusedBorderColor = neon,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.4f),
                        focusedLabelColor = neon,
                        unfocusedLabelColor = Color.White.copy(alpha = 0.7f)
                    )
                )
                OutlinedTextField(
                    value = address.value,
                    onValueChange = { address.value = it },
                    label = { Text("Dirección (opcional)", color = Color.White) },
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = Color.White,
                        cursorColor = neon,
                        focusedBorderColor = neon,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.4f),
                        focusedLabelColor = neon,
                        unfocusedLabelColor = Color.White.copy(alpha = 0.7f)
                    )
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { pickImage.launch("image/*") },
                        border = BorderStroke(1.dp, neon),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = neon)
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("Elegir logo", color = neon)
                    }

                    OutlinedButton(
                        onClick = { takePhoto.launch(null) },
                        border = BorderStroke(1.dp, magenta),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = magenta)
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("Tomar foto", color = magenta)
                    }

                    OutlinedButton(
                        onClick = { logo.value = "" },
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = Color.White.copy(alpha = 0.7f))
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("Sin logo", color = Color.White)
                    }
                }

                Button(
                    onClick = {
                        if (name.value.isNotBlank() && business.value.isNotBlank()) {
                            onDone(
                                UserProfile(
                                    userName = name.value.trim(),
                                    businessName = business.value.trim(),
                                    address = address.value.trim(),
                                    logoPath = logo.value.trim().ifBlank { null }
                                )
                            )
                        }
                    },
                    enabled = name.value.isNotBlank() && business.value.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(backgroundColor = magenta),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (existingProfile == null) "Guardar y entrar" else "Guardar cambios", color = Color.White, fontWeight = FontWeight.Bold)
                }

                onCancel?.let {
                    OutlinedButton(
                        onClick = it,
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f))
                    ) {
                        Text("Cancelar", color = Color.White)
                    }
                }
            }
        }
    }
}
