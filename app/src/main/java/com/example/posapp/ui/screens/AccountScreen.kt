package com.example.posapp.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Logout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.posapp.ui.theme.PablitoColors
import com.example.posapp.ui.theme.PablitoSizes
import com.example.posapp.ui.theme.PablitoSpacing

@Composable
fun AccountScreen(
    email: String,
    businessName: String,
    isSigningOut: Boolean,
    onSignOut: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(PablitoSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(PablitoSpacing.Lg)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = PablitoColors.Surface,
            border = BorderStroke(1.dp, PablitoColors.Border),
            elevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(PablitoSpacing.Lg),
                verticalArrangement = Arrangement.spacedBy(PablitoSpacing.Md)
            ) {
                Text(businessName, style = MaterialTheme.typography.h6, color = PablitoColors.TextPrimary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Email,
                        contentDescription = null,
                        tint = PablitoColors.Cyan,
                        modifier = Modifier.size(PablitoSizes.IconMedium)
                    )
                    Spacer(Modifier.size(PablitoSpacing.Md))
                    Text(email, color = PablitoColors.TextSecondary)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CloudDone,
                        contentDescription = null,
                        tint = PablitoColors.Success,
                        modifier = Modifier.size(PablitoSizes.IconMedium)
                    )
                    Spacer(Modifier.size(PablitoSpacing.Md))
                    Text("Cuenta protegida con Supabase", color = PablitoColors.TextSecondary)
                }
            }
        }

        Text(
            "Tus ventas continúan guardándose localmente en Room. La sincronización de datos se habilitará en la siguiente fase.",
            style = MaterialTheme.typography.body2,
            color = PablitoColors.TextSecondary
        )

        Spacer(Modifier.weight(1f))
        Button(
            onClick = onSignOut,
            enabled = !isSigningOut,
            modifier = Modifier
                .fillMaxWidth()
                .height(PablitoSizes.TouchTarget),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = PablitoColors.Magenta,
                contentColor = Color(0xFF20000D),
                disabledBackgroundColor = PablitoColors.MagentaContainer
            ),
            elevation = ButtonDefaults.elevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
        ) {
            Icon(Icons.Default.Logout, contentDescription = null)
            Spacer(Modifier.size(PablitoSpacing.Sm))
            Text(if (isSigningOut) "Cerrando sesión…" else "Cerrar sesión")
        }
    }
}
