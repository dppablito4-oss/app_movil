package com.example.posapp.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Switch
import androidx.compose.material.RadioButton
import androidx.compose.material.RadioButtonDefaults
import androidx.compose.material.TextButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.posapp.data.sync.CloudSyncPhase
import com.example.posapp.data.AppThemeMode
import com.example.posapp.data.sync.CloudSyncRuntime
import com.example.posapp.data.sync.CloudSyncScheduler
import com.example.posapp.ui.components.SpaceSaleCard
import com.example.posapp.ui.components.SpaceSaleScreenHeader
import com.example.posapp.ui.components.SpaceSaleStatusPill
import com.example.posapp.ui.components.SpaceSalePrimaryButton
import com.example.posapp.ui.components.SpaceSaleInlineMessage
import com.example.posapp.ui.components.spaceSaleTextFieldColors
import com.example.posapp.ui.theme.SpaceSaleColors
import com.example.posapp.ui.theme.SpaceSaleRadii
import com.example.posapp.ui.theme.SpaceSaleSizes
import com.example.posapp.ui.theme.SpaceSaleSpacing
import com.example.posapp.utils.parseLocalizedDecimal
import com.example.posapp.vm.BusinessSettingsViewModel
import com.example.posapp.vm.BusinessAccessViewModel
import kotlinx.coroutines.launch

@Composable
fun AccountScreen(
    email: String,
    businessName: String,
    isSigningOut: Boolean,
    onMenuClick: (() -> Unit)? = null,
    onSignOut: () -> Unit,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onBusinessSwitched: () -> Unit = {},
    settingsViewModel: BusinessSettingsViewModel = viewModel(),
    accessViewModel: BusinessAccessViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val syncState by CloudSyncRuntime.state.collectAsState()
    val settings by settingsViewModel.settings.collectAsState()
    val businessAccess by accessViewModel.state.collectAsState()
    var goalText by rememberSaveable { mutableStateOf("") }
    var receiptMessage by rememberSaveable { mutableStateOf("") }
    var lowStockEnabled by rememberSaveable { mutableStateOf(true) }
    var settingsMessage by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(settings?.updated_at) {
        settings?.let {
            goalText = (it.daily_goal_cents / 100.0).toString()
            receiptMessage = it.receipt_message
            lowStockEnabled = it.low_stock_enabled
        }
    }
    val syncVisual = when (syncState.phase) {
        CloudSyncPhase.SYNCED -> Triple("Sincronizado", Icons.Default.CloudDone, SpaceSaleColors.Success)
        CloudSyncPhase.SYNCING -> Triple("Sincronizando", Icons.Default.Sync, SpaceSaleColors.Cyan)
        CloudSyncPhase.PENDING -> Triple("${syncState.pendingChanges} cambios pendientes", Icons.Default.Sync, SpaceSaleColors.Warning)
        CloudSyncPhase.ERROR -> Triple("Error de sincronizacion", Icons.Default.CloudOff, SpaceSaleColors.Error)
        CloudSyncPhase.DISABLED -> Triple("Nube no configurada", Icons.Default.CloudOff, SpaceSaleColors.Warning)
        CloudSyncPhase.IDLE -> Triple("Listo para sincronizar", Icons.Default.CloudDone, SpaceSaleColors.TextSecondary)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SpaceSaleSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(SpaceSaleSpacing.Lg)
    ) {
        SpaceSaleScreenHeader(title = "Cuenta", subtitle = "Seguridad y sincronizacion", onMenu = onMenuClick)
        SpaceSaleCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(SpaceSaleSpacing.Lg),
                verticalArrangement = Arrangement.spacedBy(SpaceSaleSpacing.Md)
            ) {
                Text(businessName, style = MaterialTheme.typography.h6, color = SpaceSaleColors.TextPrimary)
                AccountLine(Icons.Default.Email, email, SpaceSaleColors.Cyan)
                AccountLine(syncVisual.second, syncVisual.first, syncVisual.third)
                SpaceSaleStatusPill(
                    text = "Room local + Supabase privado",
                    icon = Icons.Default.CloudDone,
                    foreground = SpaceSaleColors.Cyan,
                    background = SpaceSaleColors.CyanContainer
                )
            }
        }
        if (businessAccess.businesses.isNotEmpty()) {
            SpaceSaleCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(SpaceSaleSpacing.Lg),
                    verticalArrangement = Arrangement.spacedBy(SpaceSaleSpacing.Sm)
                ) {
                    Text("Negocios disponibles", style = MaterialTheme.typography.subtitle1, color = SpaceSaleColors.TextPrimary)
                    businessAccess.businesses.forEach { access ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Storefront, contentDescription = null, tint = if (access.isActive) SpaceSaleColors.Cyan else SpaceSaleColors.TextMuted)
                            Spacer(Modifier.width(SpaceSaleSpacing.Md))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(access.business.name, color = SpaceSaleColors.TextPrimary)
                                Text(access.role.uppercase(), style = MaterialTheme.typography.caption, color = SpaceSaleColors.TextSecondary)
                            }
                            TextButton(
                                onClick = {
                                    accessViewModel.switchBusiness(access) { error ->
                                        if (error == null) onBusinessSwitched() else settingsMessage = error
                                    }
                                },
                                enabled = !access.isActive
                            ) { Text(if (access.isActive) "Activo" else "Cambiar") }
                        }
                    }
                }
            }
        }
        businessAccess.message?.let { SpaceSaleInlineMessage(it) }
        SpaceSaleCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = SpaceSaleColors.SurfaceRaised
        ) {
            Text(
                "Puedes seguir vendiendo sin Internet. Los cambios quedan en este telefono y se envian automaticamente cuando vuelve la conexion.",
                style = MaterialTheme.typography.body2,
                color = SpaceSaleColors.TextSecondary,
                modifier = Modifier.padding(SpaceSaleSpacing.Lg)
            )
        }
        SpaceSaleCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(SpaceSaleSpacing.Lg),
                verticalArrangement = Arrangement.spacedBy(SpaceSaleSpacing.Sm)
            ) {
                Text("Apariencia", style = MaterialTheme.typography.subtitle1, color = SpaceSaleColors.TextPrimary)
                Text(
                    "Usa el modo del telefono o elige uno para SpaceSale.",
                    style = MaterialTheme.typography.body2,
                    color = SpaceSaleColors.TextSecondary
                )
                ThemeModeOption(
                    label = "Sistema",
                    description = "Cambia con la configuracion del telefono",
                    icon = Icons.Default.BrightnessAuto,
                    selected = themeMode == AppThemeMode.SYSTEM,
                    onClick = { onThemeModeChange(AppThemeMode.SYSTEM) }
                )
                ThemeModeOption(
                    label = "Claro",
                    description = "Fondo claro y alto contraste",
                    icon = Icons.Default.LightMode,
                    selected = themeMode == AppThemeMode.LIGHT,
                    onClick = { onThemeModeChange(AppThemeMode.LIGHT) }
                )
                ThemeModeOption(
                    label = "Oscuro",
                    description = "Tema espacial OLED",
                    icon = Icons.Default.DarkMode,
                    selected = themeMode == AppThemeMode.DARK,
                    onClick = { onThemeModeChange(AppThemeMode.DARK) }
                )
            }
        }
        SpaceSaleCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(SpaceSaleSpacing.Lg),
                verticalArrangement = Arrangement.spacedBy(SpaceSaleSpacing.Md)
            ) {
                Text("Configuracion del negocio", style = MaterialTheme.typography.subtitle1, color = SpaceSaleColors.TextPrimary)
                OutlinedTextField(
                    value = goalText,
                    onValueChange = { goalText = it; settingsMessage = null },
                    label = { Text("Meta diaria") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = spaceSaleTextFieldColors()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Alertas de stock bajo", color = SpaceSaleColors.TextSecondary, modifier = Modifier.weight(1f))
                    Switch(checked = lowStockEnabled, onCheckedChange = { lowStockEnabled = it })
                }
                OutlinedTextField(
                    value = receiptMessage,
                    onValueChange = { receiptMessage = it.take(240); settingsMessage = null },
                    label = { Text("Mensaje del comprobante") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = spaceSaleTextFieldColors()
                )
                settingsMessage?.let { SpaceSaleInlineMessage(it) }
                SpaceSalePrimaryButton(
                    onClick = {
                        val goal = parseLocalizedDecimal(goalText)
                        if (goal == null || goal < 0.0) {
                            settingsMessage = "Ingresa una meta valida"
                        } else {
                            settingsViewModel.save(goal, lowStockEnabled, receiptMessage) { error ->
                                settingsMessage = error ?: "Configuracion guardada"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Guardar configuracion") }
            }
        }
        if (syncState.phase == CloudSyncPhase.ERROR && !syncState.message.isNullOrBlank()) {
            SpaceSaleInlineMessage(syncState.message.orEmpty())
            OutlinedButton(
                onClick = { scope.launch { CloudSyncScheduler.retryPendingChanges(context) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = SpaceSaleSizes.ButtonHeight),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(SpaceSaleRadii.Medium)
            ) {
                Icon(Icons.Default.Sync, contentDescription = null)
                Spacer(Modifier.width(SpaceSaleSpacing.Sm))
                Text("Reintentar sincronizacion")
            }
        }

        Spacer(Modifier.size(SpaceSaleSpacing.Md))
        OutlinedButton(
            onClick = onSignOut,
            enabled = !isSigningOut,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = SpaceSaleSizes.ButtonHeight),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(SpaceSaleRadii.Medium),
            border = BorderStroke(1.dp, SpaceSaleColors.Error),
            colors = ButtonDefaults.outlinedButtonColors(
                backgroundColor = Color.Transparent,
                contentColor = SpaceSaleColors.Error,
                disabledContentColor = SpaceSaleColors.TextDisabled
            )
        ) {
            Icon(Icons.Default.Logout, contentDescription = null)
            Spacer(Modifier.width(SpaceSaleSpacing.Sm))
            Text(if (isSigningOut) "Cerrando sesion" else "Cerrar sesion")
        }
        Spacer(Modifier.size(SpaceSaleSpacing.Lg))
    }
}

@OptIn(androidx.compose.material.ExperimentalMaterialApi::class)
@Composable
private fun ThemeModeOption(
    label: String,
    description: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    androidx.compose.material.Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SpaceSaleSizes.TouchTarget),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(SpaceSaleRadii.Medium),
        color = if (selected) SpaceSaleColors.CyanContainer else Color.Transparent,
        border = BorderStroke(1.dp, if (selected) SpaceSaleColors.Cyan else SpaceSaleColors.Border),
        elevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = SpaceSaleSpacing.Md, vertical = SpaceSaleSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) SpaceSaleColors.Cyan else SpaceSaleColors.TextSecondary,
                modifier = Modifier.size(SpaceSaleSizes.IconMedium)
            )
            Spacer(Modifier.width(SpaceSaleSpacing.Md))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.body1, color = SpaceSaleColors.TextPrimary)
                Text(description, style = MaterialTheme.typography.caption, color = SpaceSaleColors.TextSecondary)
            }
            RadioButton(
                selected = selected,
                onClick = null,
                colors = RadioButtonDefaults.colors(
                    selectedColor = SpaceSaleColors.Cyan,
                    unselectedColor = SpaceSaleColors.ControlOutline
                )
            )
        }
    }
}

@Composable
private fun AccountLine(icon: ImageVector, text: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(SpaceSaleSizes.IconMedium))
        Spacer(Modifier.width(SpaceSaleSpacing.Md))
        Text(text, style = MaterialTheme.typography.body2, color = SpaceSaleColors.TextSecondary)
    }
}
