package com.example.posapp.ui.screens.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Security
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.example.posapp.auth.AuthMode
import com.example.posapp.auth.AuthInputValidator
import com.example.posapp.auth.AuthStep
import com.example.posapp.auth.AuthUiState
import com.example.posapp.ui.theme.PablitoColors
import com.example.posapp.ui.theme.PablitoRadii
import com.example.posapp.ui.theme.PablitoSizes
import com.example.posapp.ui.theme.PablitoSpacing

@Composable
fun AuthGate(
    state: AuthUiState,
    onChooseMode: (AuthMode) -> Unit,
    onBackToWelcome: () -> Unit,
    onBackToEmail: () -> Unit,
    onSendOtp: (String) -> Unit,
    onVerifyOtp: (String) -> Unit,
    onResendOtp: () -> Unit,
    onCreateBusiness: (String) -> Unit,
    onClearFeedback: () -> Unit,
    onSignOut: () -> Unit,
    onRetrySession: () -> Unit,
    authenticatedContent: @Composable () -> Unit
) {
    when (state.step) {
        AuthStep.LOADING -> LoadingScreen()
        AuthStep.WELCOME -> WelcomeScreen(
            errorMessage = state.errorMessage,
            onSignIn = { onChooseMode(AuthMode.SIGN_IN) },
            onRegister = { onChooseMode(AuthMode.REGISTER) }
        )
        AuthStep.EMAIL -> EmailScreen(
            mode = state.mode,
            isSubmitting = state.isSubmitting,
            errorMessage = state.errorMessage,
            onBack = onBackToWelcome,
            onContinue = onSendOtp,
            onInputChanged = onClearFeedback
        )
        AuthStep.OTP -> OtpScreen(
            email = state.email,
            isSubmitting = state.isSubmitting,
            errorMessage = state.errorMessage,
            infoMessage = state.infoMessage,
            onBack = onBackToEmail,
            onVerify = onVerifyOtp,
            onResend = onResendOtp,
            onInputChanged = onClearFeedback
        )
        AuthStep.FIRST_BUSINESS -> FirstBusinessScreen(
            email = state.email,
            isSubmitting = state.isSubmitting,
            errorMessage = state.errorMessage,
            onCreate = onCreateBusiness,
            onInputChanged = onClearFeedback,
            onSignOut = onSignOut
        )
        AuthStep.SESSION_ERROR -> SessionErrorScreen(
            errorMessage = state.errorMessage,
            onRetry = onRetrySession,
            onSignOut = onSignOut
        )
        AuthStep.LOCAL_DATA_CONFLICT -> LocalDataConflictScreen(onSignOut = onSignOut)
        AuthStep.AUTHENTICATED -> authenticatedContent()
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PablitoColors.Background)
            .testTag("auth_loading"),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BrandMark()
            Spacer(Modifier.height(PablitoSpacing.Xl))
            CircularProgressIndicator(color = PablitoColors.Cyan)
            Spacer(Modifier.height(PablitoSpacing.Md))
            Text("Preparando tu espacio…", color = PablitoColors.TextSecondary)
        }
    }
}

@Composable
private fun WelcomeScreen(
    errorMessage: String?,
    onSignIn: () -> Unit,
    onRegister: () -> Unit
) {
    AuthShell {
        Spacer(Modifier.height(PablitoSpacing.Xxl))
        BrandMark()
        Spacer(Modifier.height(PablitoSpacing.Xl))
        Text(
            text = "SPACESALE",
            style = MaterialTheme.typography.h4,
            color = PablitoColors.TextPrimary,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.height(PablitoSpacing.Sm))
        Text(
            text = "Tu negocio, claro y bajo control.",
            style = MaterialTheme.typography.body1,
            color = PablitoColors.TextSecondary
        )
        Spacer(Modifier.height(PablitoSpacing.Xxxl))
        FeedbackMessage(error = errorMessage)
        PrimaryActionButton(text = "Ingresar", onClick = onSignIn)
        Spacer(Modifier.height(PablitoSpacing.Md))
        OutlinedButton(
            onClick = onRegister,
            modifier = Modifier
                .fillMaxWidth()
                .height(PablitoSizes.TouchTarget),
            border = BorderStroke(1.dp, PablitoColors.Border),
            shape = RoundedCornerShape(PablitoRadii.Medium)
        ) {
            Text("Crear cuenta", color = PablitoColors.TextPrimary)
        }
        Spacer(Modifier.height(PablitoSpacing.Xl))
        Text(
            text = "Acceso seguro con un código enviado a tu correo.",
            style = MaterialTheme.typography.caption,
            color = PablitoColors.TextSecondary
        )
    }
}

@Composable
private fun EmailScreen(
    mode: AuthMode,
    isSubmitting: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onContinue: (String) -> Unit,
    onInputChanged: () -> Unit
) {
    var email by rememberSaveable(mode) { mutableStateOf("") }
    val title = if (mode == AuthMode.REGISTER) "Crea tu cuenta" else "Ingresa a tu cuenta"
    val description = if (mode == AuthMode.REGISTER) {
        "Te enviaremos un código para verificar tu correo."
    } else {
        "Usa el correo asociado a tu negocio."
    }

    AuthShell {
        BackButton(onClick = onBack)
        Spacer(Modifier.height(PablitoSpacing.Md))
        SectionIcon(icon = { Icon(Icons.Default.Email, contentDescription = null) })
        Spacer(Modifier.height(PablitoSpacing.Lg))
        AuthTitle(title, description)
        Spacer(Modifier.height(PablitoSpacing.Xxl))
        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it.take(254)
                onInputChanged()
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Correo electrónico") },
            placeholder = { Text("negocio@correo.com") },
            singleLine = true,
            isError = errorMessage != null,
            enabled = !isSubmitting,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { if (!isSubmitting) onContinue(email) }),
            colors = authTextFieldColors()
        )
        Spacer(Modifier.height(PablitoSpacing.Sm))
        FeedbackMessage(error = errorMessage)
        Spacer(Modifier.height(PablitoSpacing.Lg))
        PrimaryActionButton(
            text = if (isSubmitting) "Enviando…" else "Enviar código",
            enabled = !isSubmitting,
            onClick = { onContinue(email) }
        )
    }
}

@Composable
private fun OtpScreen(
    email: String,
    isSubmitting: Boolean,
    errorMessage: String?,
    infoMessage: String?,
    onBack: () -> Unit,
    onVerify: (String) -> Unit,
    onResend: () -> Unit,
    onInputChanged: () -> Unit
) {
    var otp by rememberSaveable(email) { mutableStateOf("") }
    var resendSeconds by rememberSaveable(email) { mutableIntStateOf(30) }

    LaunchedEffect(resendSeconds) {
        if (resendSeconds > 0) {
            delay(1_000)
            resendSeconds -= 1
        }
    }

    AuthShell {
        BackButton(onClick = onBack)
        Spacer(Modifier.height(PablitoSpacing.Md))
        SectionIcon(icon = { Icon(Icons.Default.Lock, contentDescription = null) })
        Spacer(Modifier.height(PablitoSpacing.Lg))
        AuthTitle(
            "Revisa tu correo",
            "Escribe el código de ${AuthInputValidator.OTP_LENGTH} dígitos enviado a $email"
        )
        Spacer(Modifier.height(PablitoSpacing.Xxl))
        OutlinedTextField(
            value = otp,
            onValueChange = { value ->
                otp = value.filter(Char::isDigit).take(AuthInputValidator.OTP_LENGTH)
                onInputChanged()
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Código de verificación") },
            placeholder = { Text("0".repeat(AuthInputValidator.OTP_LENGTH)) },
            singleLine = true,
            isError = errorMessage != null,
            enabled = !isSubmitting,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { if (!isSubmitting) onVerify(otp) }),
            colors = authTextFieldColors()
        )
        Spacer(Modifier.height(PablitoSpacing.Sm))
        FeedbackMessage(error = errorMessage, info = infoMessage)
        Spacer(Modifier.height(PablitoSpacing.Lg))
        PrimaryActionButton(
            text = if (isSubmitting) "Verificando…" else "Verificar y continuar",
            enabled = !isSubmitting,
            onClick = { onVerify(otp) }
        )
        Spacer(Modifier.height(PablitoSpacing.Sm))
        TextButton(
            onClick = {
                resendSeconds = 30
                onResend()
            },
            enabled = !isSubmitting && resendSeconds == 0,
            modifier = Modifier
                .fillMaxWidth()
                .height(PablitoSizes.TouchTarget)
        ) {
            Text(
                if (resendSeconds > 0) "Reenviar en ${resendSeconds}s" else "Reenviar código",
                color = if (resendSeconds > 0) PablitoColors.TextDisabled else PablitoColors.Cyan
            )
        }
    }
}

@Composable
private fun FirstBusinessScreen(
    email: String,
    isSubmitting: Boolean,
    errorMessage: String?,
    onCreate: (String) -> Unit,
    onInputChanged: () -> Unit,
    onSignOut: () -> Unit
) {
    var businessName by rememberSaveable(email) { mutableStateOf("") }

    AuthShell {
        Spacer(Modifier.height(PablitoSpacing.Xxl))
        SectionIcon(icon = { Icon(Icons.Default.Storefront, contentDescription = null) })
        Spacer(Modifier.height(PablitoSpacing.Lg))
        AuthTitle(
            title = "Crea tu primer negocio",
            description = "Este nombre aparecerá en el dashboard y podrás editarlo después."
        )
        Spacer(Modifier.height(PablitoSpacing.Xxl))
        OutlinedTextField(
            value = businessName,
            onValueChange = {
                businessName = it.take(120)
                onInputChanged()
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Nombre del negocio") },
            placeholder = { Text("Ej. Bodega San Martín") },
            singleLine = true,
            isError = errorMessage != null,
            enabled = !isSubmitting,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (!isSubmitting) onCreate(businessName) }),
            colors = authTextFieldColors()
        )
        Spacer(Modifier.height(PablitoSpacing.Sm))
        FeedbackMessage(error = errorMessage)
        Spacer(Modifier.height(PablitoSpacing.Lg))
        PrimaryActionButton(
            text = if (isSubmitting) "Creando negocio…" else "Crear negocio",
            enabled = !isSubmitting,
            onClick = { onCreate(businessName) }
        )
        Spacer(Modifier.height(PablitoSpacing.Sm))
        TextButton(
            onClick = onSignOut,
            enabled = !isSubmitting,
            modifier = Modifier
                .fillMaxWidth()
                .height(PablitoSizes.TouchTarget)
        ) {
            Text("Usar otra cuenta", color = PablitoColors.TextSecondary)
        }
        Spacer(Modifier.height(PablitoSpacing.Lg))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = PablitoColors.Success,
                modifier = Modifier.size(PablitoSizes.IconSmall)
            )
            Spacer(Modifier.size(PablitoSpacing.Sm))
            Text(
                "Room seguirá guardando tus datos en el teléfono.",
                style = MaterialTheme.typography.caption,
                color = PablitoColors.TextSecondary
            )
        }
    }
}

@Composable
private fun SessionErrorScreen(
    errorMessage: String?,
    onRetry: () -> Unit,
    onSignOut: () -> Unit
) {
    AuthShell {
        SectionIcon(icon = { Icon(Icons.Default.CloudOff, contentDescription = null) })
        Spacer(Modifier.height(PablitoSpacing.Lg))
        AuthTitle(
            title = "No pudimos abrir tu negocio",
            description = "Tu sesión sigue protegida. Comprueba la conexión para recuperar los datos del negocio."
        )
        Spacer(Modifier.height(PablitoSpacing.Lg))
        FeedbackMessage(error = errorMessage)
        Spacer(Modifier.height(PablitoSpacing.Md))
        PrimaryActionButton(text = "Volver a intentar", onClick = onRetry)
        Spacer(Modifier.height(PablitoSpacing.Sm))
        TextButton(
            onClick = onSignOut,
            modifier = Modifier
                .fillMaxWidth()
                .height(PablitoSizes.TouchTarget)
        ) {
            Text("Usar otra cuenta", color = PablitoColors.TextSecondary)
        }
    }
}

@Composable
private fun LocalDataConflictScreen(onSignOut: () -> Unit) {
    AuthShell {
        SectionIcon(icon = { Icon(Icons.Default.Security, contentDescription = null) })
        Spacer(Modifier.height(PablitoSpacing.Lg))
        AuthTitle(
            title = "Datos locales protegidos",
            description = "Los datos guardados en este teléfono pertenecen a otra cuenta. Ingresa con la cuenta original para evitar mezclar ventas e inventario."
        )
        Spacer(Modifier.height(PablitoSpacing.Xl))
        Text(
            text = "La compatibilidad segura con varias cuentas se habilitará al separar Room por usuario en la siguiente fase.",
            style = MaterialTheme.typography.body2,
            color = PablitoColors.TextSecondary
        )
        Spacer(Modifier.height(PablitoSpacing.Xl))
        PrimaryActionButton(text = "Cerrar sesión", onClick = onSignOut)
    }
}

@Composable
private fun AuthShell(content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PablitoColors.Background)
            .imePadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(PablitoSpacing.Lg),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Center,
            content = content
        )
    }
}

@Composable
private fun BrandMark() {
    Card(
        modifier = Modifier.size(64.dp),
        shape = RoundedCornerShape(PablitoRadii.Large),
        backgroundColor = PablitoColors.CyanContainer,
        border = BorderStroke(1.dp, PablitoColors.Cyan),
        elevation = 0.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.Storefront,
                contentDescription = "SpaceSale",
                tint = PablitoColors.Cyan,
                modifier = Modifier.size(PablitoSizes.IconLarge)
            )
        }
    }
}

@Composable
private fun SectionIcon(icon: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(PablitoSizes.TouchTarget)
            .background(PablitoColors.CyanContainer, RoundedCornerShape(PablitoRadii.Medium)),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material.LocalContentColor provides PablitoColors.Cyan,
            content = icon
        )
    }
}

@Composable
private fun BackButton(onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(PablitoSizes.TouchTarget)
    ) {
        Icon(
            Icons.Default.ArrowBack,
            contentDescription = "Volver",
            tint = PablitoColors.TextPrimary
        )
    }
}

@Composable
private fun AuthTitle(title: String, description: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.h5,
        color = PablitoColors.TextPrimary,
        modifier = Modifier.semantics { heading() }
    )
    Spacer(Modifier.height(PablitoSpacing.Sm))
    Text(
        text = description,
        style = MaterialTheme.typography.body1,
        color = PablitoColors.TextSecondary
    )
}

@Composable
private fun PrimaryActionButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(PablitoSizes.TouchTarget),
        shape = RoundedCornerShape(PablitoRadii.Medium),
        colors = ButtonDefaults.buttonColors(
            backgroundColor = PablitoColors.Cyan,
            contentColor = Color(0xFF001014),
            disabledBackgroundColor = PablitoColors.CyanContainer,
            disabledContentColor = PablitoColors.TextDisabled
        ),
        elevation = ButtonDefaults.elevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
    ) {
        if (text.endsWith("…")) {
            CircularProgressIndicator(
                modifier = Modifier.size(PablitoSizes.IconSmall),
                color = PablitoColors.TextDisabled,
                strokeWidth = 2.dp
            )
            Spacer(Modifier.size(PablitoSpacing.Sm))
        }
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun FeedbackMessage(error: String? = null, info: String? = null) {
    val message = error ?: info ?: return
    Text(
        text = message,
        style = MaterialTheme.typography.body2,
        color = if (error != null) PablitoColors.Error else PablitoColors.Success,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(PablitoSpacing.Sm))
}

@Composable
private fun authTextFieldColors() = TextFieldDefaults.outlinedTextFieldColors(
    textColor = PablitoColors.TextPrimary,
    cursorColor = PablitoColors.Cyan,
    focusedBorderColor = PablitoColors.Cyan,
    unfocusedBorderColor = PablitoColors.Border,
    errorBorderColor = PablitoColors.Error,
    focusedLabelColor = PablitoColors.Cyan,
    unfocusedLabelColor = PablitoColors.TextSecondary,
    placeholderColor = PablitoColors.TextDisabled
)
