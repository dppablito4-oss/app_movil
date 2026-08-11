package com.example.posapp.ui.screens

import android.Manifest
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.posapp.vm.DashboardViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.posapp.utils.ImageUtils
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun DashboardScreen(
    navController: NavController,
    dashboardViewModel: DashboardViewModel = viewModel(),
    userName: String = "Usuario",
    logoPath: String? = null
) {
    val context = LocalContext.current
    val ventasHoy by dashboardViewModel.ventasHoy.collectAsState()
    val ventasAyer by dashboardViewModel.ventasAyer.collectAsState()
    val porCobrar by dashboardViewModel.porCobrar.collectAsState()
    val recentSales by dashboardViewModel.recentSales.collectAsState()
    val mayorDeudor by dashboardViewModel.mayorDeudor.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    var capturedPath by remember { mutableStateOf<String?>(null) }

    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) Toast.makeText(context, "Permiso de cámara denegado", Toast.LENGTH_SHORT).show()
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp: Bitmap? ->
        if (bmp != null) {
            coroutineScope.launch {
                val path = withContext(Dispatchers.IO) { ImageUtils.saveBitmap(context, bmp) }
                capturedPath = path
                Toast.makeText(context, "Imagen guardada: $path", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF111827)),
                contentAlignment = Alignment.Center
            ) {
                when {
                    !logoPath.isNullOrBlank() -> AsyncImage(model = logoPath, contentDescription = "Logo", modifier = Modifier.fillMaxSize().clip(CircleShape))
                    userName.isNotBlank() -> Text(userName.first().uppercase(), color = Color(0xFF00E5FF), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    else -> Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color(0xFF00E5FF))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Hola, $userName", style = MaterialTheme.typography.h5, color = MaterialTheme.colors.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = "Listo para vender en modo neon", color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f), fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // KPIs con meta diaria
        Card(
            shape = RoundedCornerShape(16.dp),
            backgroundColor = Color(0xFF0F172A),
            border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.3f)),
            elevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            val metaDia = 500.0
            val progreso = (ventasHoy / metaDia).coerceIn(0.0, 1.0)
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Meta diaria: S/ ${String.format("%.2f", metaDia)}", color = Color.White, fontWeight = FontWeight.Bold)
                Text("Hoy: S/ ${String.format("%.2f", ventasHoy)}", color = Color.White.copy(alpha = 0.8f))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF1F2937))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progreso.toFloat())
                            .fillMaxHeight()
                            .background(Brush.horizontalGradient(listOf(Color(0xFF00E5FF), Color(0xFFFF2D92))))
                    )
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Ventas de hoy
            Card(modifier = Modifier.weight(1f).height(140.dp), shape = RoundedCornerShape(16.dp), elevation = 4.dp) {
                Box(modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.horizontalGradient(colors = listOf(Color(0xFF00C853), Color(0xFF69F0AE))),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(12.dp)
                ) {
                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                        Text("Ventas de Hoy", color = Color.White)
                        Text("S/ ${String.format("%.2f", ventasHoy)}", style = MaterialTheme.typography.h4.copy(color = Color.White))
                    }
                }
            }

            // Por cobrar
            Card(modifier = Modifier.weight(1f).height(140.dp), shape = RoundedCornerShape(16.dp), elevation = 4.dp) {
                Box(modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.horizontalGradient(colors = listOf(Color(0xFFFF7043), Color(0xFFFF5252))),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(12.dp)
                ) {
                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                        Text("Por Cobrar", color = Color.White)
                        Text("S/ ${String.format("%.2f", porCobrar)}", style = MaterialTheme.typography.h4.copy(color = Color.White))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Action buttons labeled
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FloatingActionButton(onClick = { navController.navigate("sales") }, backgroundColor = Color(0xFF00B0FF)) {
                    Icon(Icons.Default.Add, contentDescription = "Nueva Venta", tint = Color.White)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text("Nueva Venta", color = Color.White, fontSize = 12.sp)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FloatingActionButton(onClick = {
                    requestPermission.launch(Manifest.permission.CAMERA)
                    takePicture.launch(null)
                }, backgroundColor = Color(0xFFFF4081)) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "Escanear", tint = Color.White)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text("Escanear", color = Color.White, fontSize = 12.sp)
            }

            // thumbnail preview
            capturedPath?.let { path ->
                AsyncImage(model = path, contentDescription = "scan", modifier = Modifier.size(64.dp))
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Últimos Movimientos",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
        )

        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (recentSales.isEmpty()) {
                items(3) { _ ->
                    PlaceholderMovementCard()
                }
            } else {
                items(recentSales) { sale ->
                    val title = "Venta de ${sale.productName}"
                    MovementCard(title = title, subtitle = relativeTimeText(sale.fechaMillis), amount = sale.total)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Rotating ticker summary
        val tickerMessages = remember(ventasHoy, ventasAyer, porCobrar, recentSales, mayorDeudor) {
            val lastSale = recentSales.firstOrNull()?.total
            val base = mutableListOf<String>()
            base.add("Hoy vendiste S/ ${String.format("%.2f", ventasHoy)}")
            base.add("Ayer vendiste S/ ${String.format("%.2f", ventasAyer)}")
            lastSale?.let { base.add("Última venta S/ ${String.format("%.2f", it)}") }
            mayorDeudor?.let { (nombre, monto) -> base.add("Recordatorio: ${nombre} debe S/ ${String.format("%.2f", monto)}") }
            base.add("Pendiente por cobrar: S/ ${String.format("%.2f", porCobrar)}")
            if (base.isEmpty()) base.add("Listo para empezar a vender")
            base
        }
        var tickerIndex by remember { mutableStateOf(0) }

        LaunchedEffect(tickerMessages) {
            tickerIndex = 0
            while (true) {
                delay(3000)
                tickerIndex = (tickerIndex + 1) % tickerMessages.size
            }
        }

        Card(
            backgroundColor = Color(0xFF0F172A),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f)),
            elevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF2D92))
                )
                AnimatedContent(
                    targetState = tickerMessages[tickerIndex],
                    transitionSpec = {
                        slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
                    },
                    label = "ticker"
                ) { msg ->
                    Text(
                        text = msg,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaceholderMovementCard() {
    Card(backgroundColor = Color(0xFF1E1E1E), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(40.dp).background(Color.Gray.copy(alpha = 0.3f), shape = CircleShape))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Box(modifier = Modifier.height(12.dp).fillMaxWidth(0.4f).background(Color.Gray.copy(alpha = 0.25f), shape = RoundedCornerShape(4.dp)))
                Spacer(modifier = Modifier.height(6.dp))
                Box(modifier = Modifier.height(10.dp).fillMaxWidth(0.25f).background(Color.Gray.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)))
            }
            Box(modifier = Modifier.width(64.dp).height(14.dp).background(Color(0xFF00E676).copy(alpha = 0.2f), shape = RoundedCornerShape(6.dp)))
        }
    }
}

@Composable
private fun MovementCard(title: String, subtitle: String, amount: Double) {
    Card(backgroundColor = Color(0xFF1E1E1E), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(40.dp).background(Color.Gray, shape = CircleShape))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold)
                Text(subtitle, color = Color.Gray, fontSize = 12.sp)
            }
            Text("+ S/ ${String.format("%.2f", amount)}", color = Color(0xFF00E676), fontWeight = FontWeight.Bold)
        }
    }
}

private fun relativeTimeText(epochMillis: Long): String {
    val now = System.currentTimeMillis()
    val minutes = ((now - epochMillis).coerceAtLeast(0) / TimeUnit.MINUTES.toMillis(1)).toInt()
    return when {
        minutes < 1 -> "Hace unos segundos"
        minutes < 60 -> "Hace ${minutes} min"
        minutes < 60 * 24 -> "Hace ${minutes / 60} h"
        else -> "Hace ${minutes / (60 * 24)} d"
    }
}
