package com.example.posapp.auth

enum class AuthOperation {
    SEND_OTP, VERIFY_OTP, SIGN_IN_PASSWORD, RESTORE_SESSION,
    CREATE_BUSINESS, OPEN_BUSINESS, GOOGLE_SIGN_IN, SIGN_OUT, CLEAR_LOCAL_DATA
}

enum class AuthFailure {
    INVALID_OTP, EXPIRED_OTP, RATE_LIMITED, OFFLINE, UNAUTHORIZED,
    SMTP_FAILURE, ACCOUNT_NOT_FOUND, ACCOUNT_EXISTS, INVALID_CREDENTIALS,
    WEAK_PASSWORD, INVALID_EMAIL, PERMISSION_DENIED, UNKNOWN
}

internal fun classifyAuthFailure(error: Throwable): AuthFailure {
    val text = generateSequence(error as Throwable?) { it.cause }
        .take(8)
        .joinToString(" ") { "${it::class.java.simpleName} ${it.message.orEmpty()}" }
        .lowercase()
    return when {
        "invalid" in text && ("otp" in text || "token" in text) -> AuthFailure.INVALID_OTP
        "expired" in text -> AuthFailure.EXPIRED_OTP
        "rate" in text || "too many" in text || "over_email_send_rate_limit" in text -> AuthFailure.RATE_LIMITED
        "network" in text || "unable to resolve" in text || "failed to connect" in text || "timeout" in text -> AuthFailure.OFFLINE
        "user not found" in text || "signups not allowed" in text || "signup is disabled" in text || "otp_disabled" in text -> AuthFailure.ACCOUNT_NOT_FOUND
        "already registered" in text || "already exists" in text -> AuthFailure.ACCOUNT_EXISTS
        "invalid login credentials" in text || "invalid credentials" in text -> AuthFailure.INVALID_CREDENTIALS
        "weak password" in text || "password should" in text -> AuthFailure.WEAK_PASSWORD
        "email_address_invalid" in text || "invalid email" in text -> AuthFailure.INVALID_EMAIL
        "smtp" in text || "email sending" in text || "send email" in text || "unexpected_failure" in text -> AuthFailure.SMTP_FAILURE
        "row-level security" in text || "rls" in text || "permission" in text || "forbidden" in text -> AuthFailure.PERMISSION_DENIED
        "401" in text || "unauthorized" in text || "invalid api key" in text || "apikey" in text -> AuthFailure.UNAUTHORIZED
        else -> AuthFailure.UNKNOWN
    }
}

internal fun authUserMessage(operation: AuthOperation, failure: AuthFailure): String = when (failure) {
    AuthFailure.INVALID_OTP -> "El codigo es incorrecto o ya vencio."
    AuthFailure.EXPIRED_OTP -> "El codigo ya vencio. Solicita uno nuevo."
    AuthFailure.RATE_LIMITED -> "Alcanzaste el limite de intentos. Espera un minuto y vuelve a probar."
    AuthFailure.OFFLINE -> "Sin conexion. Comprueba internet y vuelve a intentar."
    AuthFailure.ACCOUNT_NOT_FOUND -> "Ese correo aun no tiene una cuenta. Vuelve y elige Crear cuenta."
    AuthFailure.ACCOUNT_EXISTS -> "Ese correo ya tiene una cuenta. Elige Ingresar."
    AuthFailure.INVALID_CREDENTIALS -> "Correo o contrasena incorrectos. Tambien puedes ingresar con codigo OTP."
    AuthFailure.WEAK_PASSWORD -> "La contrasena no cumple la seguridad requerida. Usa letras y numeros."
    AuthFailure.INVALID_EMAIL -> "Comprueba que el correo este escrito correctamente."
    AuthFailure.SMTP_FAILURE -> "No se pudo enviar el correo. Intenta nuevamente en unos minutos."
    AuthFailure.PERMISSION_DENIED -> when (operation) {
        AuthOperation.CREATE_BUSINESS, AuthOperation.OPEN_BUSINESS -> "No se pudo preparar tu negocio. Intenta nuevamente."
        else -> "Tu cuenta no tiene permiso para completar esta operacion."
    }
    AuthFailure.UNAUTHORIZED -> "Tu sesion no es valida. Ingresa nuevamente."
    AuthFailure.UNKNOWN -> when (operation) {
        AuthOperation.SEND_OTP -> "No se pudo enviar el codigo. Intenta nuevamente."
        AuthOperation.VERIFY_OTP -> "No se pudo verificar el codigo. Solicita uno nuevo e intenta otra vez."
        AuthOperation.SIGN_IN_PASSWORD -> "No se pudo iniciar sesion. Intenta nuevamente."
        AuthOperation.GOOGLE_SIGN_IN -> "No se pudo ingresar con Google. Intenta nuevamente."
        AuthOperation.RESTORE_SESSION -> "No pudimos restaurar tu sesion. Ingresa nuevamente."
        AuthOperation.CREATE_BUSINESS, AuthOperation.OPEN_BUSINESS -> "No se pudo preparar tu negocio. Intenta nuevamente."
        AuthOperation.SIGN_OUT -> "No pudimos cerrar la sesion de forma segura. Tus datos locales se conservaron."
        AuthOperation.CLEAR_LOCAL_DATA -> "No pudimos limpiar los datos de este dispositivo. Intenta nuevamente."
    }
}
