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
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Sync
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.posapp.data.sync.CloudSyncPhase
import com.example.posapp.data.sync.CloudSyncRuntime
import com.example.posapp.ui.components.SpaceSaleCard
import com.example.posapp.ui.components.SpaceSaleScreenHeader
import com.example.posapp.ui.components.SpaceSaleStatusPill
import com.example.posapp.ui.theme.SpaceSaleColors
import com.example.posapp.ui.theme.SpaceSaleRadii
import com.example.posapp.ui.theme.SpaceSaleSizes
import com.example.posapp.ui.theme.SpaceSaleSpacing

@Composable
fun AccountScreen(
    email: String,
    businessName: String,
    isSigningOut: Boolean,
    onMenuClick: (() -> Unit)? = null,
    onSignOut: () -> Unit
) {
    val syncState by CloudSyncRuntime.state.collectAsState()
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
        if (syncState.phase == CloudSyncPhase.ERROR && !syncState.message.isNullOrBlank()) {
            Text(syncState.message.orEmpty(), style = MaterialTheme.typography.body2, color = SpaceSaleColors.Error)
        }

        Spacer(Modifier.weight(1f))
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

@Composable
private fun AccountLine(icon: ImageVector, text: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(SpaceSaleSizes.IconMedium))
        Spacer(Modifier.width(SpaceSaleSpacing.Md))
        Text(text, style = MaterialTheme.typography.body2, color = SpaceSaleColors.TextSecondary)
    }
}
