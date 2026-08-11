package com.example.posapp.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AuthRepository(application.applicationContext)
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        restoreSession()
        observeSessionChanges()
    }

    fun chooseMode(mode: AuthMode) {
        _uiState.value = AuthUiState(step = AuthStep.EMAIL, mode = mode)
    }

    fun backToWelcome() {
        _uiState.value = AuthUiState(step = AuthStep.WELCOME)
    }

    fun backToEmail() {
        _uiState.value = _uiState.value.copy(
            step = AuthStep.EMAIL,
            isSubmitting = false,
            errorMessage = null,
            infoMessage = null
        )
    }

    fun clearFeedback() {
        if (_uiState.value.errorMessage != null || _uiState.value.infoMessage != null) {
            _uiState.value = _uiState.value.copy(errorMessage = null, infoMessage = null)
        }
    }

    fun sendOtp(rawEmail: String) {
        val validationError = AuthInputValidator.emailError(rawEmail)
        if (validationError != null) {
            _uiState.value = _uiState.value.copy(errorMessage = validationError)
            return
        }
        val email = AuthInputValidator.normalizeEmail(rawEmail)
        val mode = _uiState.value.mode
        submit {
            repository.sendOtp(email, createUser = mode == AuthMode.REGISTER)
            _uiState.value = _uiState.value.copy(
                step = AuthStep.OTP,
                email = email,
                isSubmitting = false,
                errorMessage = null,
                infoMessage = "Te enviamos un código de ${AuthInputValidator.OTP_LENGTH} dígitos."
            )
        }
    }

    fun resendOtp() {
        val state = _uiState.value
        if (state.email.isBlank()) return
        submit {
            repository.sendOtp(state.email, createUser = state.mode == AuthMode.REGISTER)
            _uiState.value = _uiState.value.copy(
                isSubmitting = false,
                errorMessage = null,
                infoMessage = "Código reenviado. Revisa también spam."
            )
        }
    }

    fun verifyOtp(rawToken: String) {
        val token = rawToken.filter(Char::isDigit)
        val validationError = AuthInputValidator.otpError(token)
        if (validationError != null) {
            _uiState.value = _uiState.value.copy(errorMessage = validationError)
            return
        }
        val email = _uiState.value.email
        submit {
            val user = repository.verifyOtp(email, token)
            openAuthenticatedUser(user)
        }
    }

    fun createFirstBusiness(rawName: String) {
        val validationError = AuthInputValidator.businessNameError(rawName)
        if (validationError != null) {
            _uiState.value = _uiState.value.copy(errorMessage = validationError)
            return
        }
        val userId = _uiState.value.userId
        if (userId == null) {
            _uiState.value = _uiState.value.copy(errorMessage = "La sesión venció. Vuelve a ingresar.")
            return
        }
        submit {
            val business = repository.createBusiness(userId, rawName)
            repository.bindLocalDataTo(userId)
            _uiState.value = _uiState.value.copy(
                step = AuthStep.AUTHENTICATED,
                business = business,
                isSubmitting = false,
                errorMessage = null,
                infoMessage = null
            )
        }
    }

    fun retrySession() {
        if (_uiState.value.isSubmitting) return
        val user = repository.currentUser()
        if (user == null) {
            _uiState.value = AuthUiState(
                step = AuthStep.WELCOME,
                errorMessage = "Tu sesión terminó. Ingresa nuevamente."
            )
            return
        }
        viewModelScope.launch { openAuthenticatedUser(user) }
    }

    fun signOut() {
        submit {
            repository.signOut()
            _uiState.value = AuthUiState(step = AuthStep.WELCOME)
        }
    }

    private fun restoreSession() {
        viewModelScope.launch {
            runCatching {
                repository.awaitInitialization()
                repository.currentUser()
            }.onSuccess { user ->
                if (user == null) {
                    _uiState.value = AuthUiState(step = AuthStep.WELCOME)
                } else {
                    openAuthenticatedUser(user)
                }
            }.onFailure { error ->
                _uiState.value = AuthUiState(
                    step = AuthStep.WELCOME,
                    errorMessage = friendlyMessage(error)
                )
            }
        }
    }

    private suspend fun openAuthenticatedUser(user: AuthenticatedUser) {
        _uiState.value = _uiState.value.copy(
            step = AuthStep.LOADING,
            email = user.email,
            userId = user.id,
            isSubmitting = false,
            errorMessage = null
        )
        if (!repository.canOpenLocalData(user.id)) {
            _uiState.value = _uiState.value.copy(
                step = AuthStep.LOCAL_DATA_CONFLICT,
                isSubmitting = false,
                errorMessage = null,
                infoMessage = null
            )
            return
        }
        val cached = repository.cachedBusiness(user.id)
        val remoteResult = runCatching { repository.findBusiness() }
        val business = remoteResult.getOrNull() ?: cached

        if (business != null) {
            if (remoteResult.isSuccess) repository.cacheBusiness(user.id, business)
            repository.bindLocalDataTo(user.id)
            _uiState.value = _uiState.value.copy(
                step = AuthStep.AUTHENTICATED,
                business = business,
                isSubmitting = false,
                errorMessage = null,
                infoMessage = if (remoteResult.isFailure) "Trabajando sin conexión con los datos locales." else null
            )
        } else if (remoteResult.isFailure) {
            _uiState.value = _uiState.value.copy(
                step = AuthStep.SESSION_ERROR,
                isSubmitting = false,
                errorMessage = friendlyMessage(remoteResult.exceptionOrNull()!!)
            )
        } else {
            _uiState.value = _uiState.value.copy(
                step = AuthStep.FIRST_BUSINESS,
                business = null,
                isSubmitting = false,
                errorMessage = null,
                infoMessage = null
            )
        }
    }

    private fun observeSessionChanges() {
        viewModelScope.launch {
            repository.sessionStatus.drop(1).collectLatest { status ->
                if (status is SessionStatus.NotAuthenticated && _uiState.value.step == AuthStep.AUTHENTICATED) {
                    _uiState.value = AuthUiState(
                        step = AuthStep.WELCOME,
                        errorMessage = "Tu sesión terminó. Ingresa nuevamente."
                    )
                }
            }
        }
    }

    private fun submit(block: suspend () -> Unit) {
        if (_uiState.value.isSubmitting) return
        _uiState.value = _uiState.value.copy(
            isSubmitting = true,
            errorMessage = null,
            infoMessage = null
        )
        viewModelScope.launch {
            runCatching { block() }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isSubmitting = false,
                    errorMessage = friendlyMessage(error)
                )
            }
        }
    }

    private fun friendlyMessage(error: Throwable): String {
        val message = error.message.orEmpty().lowercase()
        return when {
            "invalid" in message && ("otp" in message || "token" in message) ->
                "El código es incorrecto o ya venció."
            "expired" in message -> "El código ya venció. Solicita uno nuevo."
            "rate" in message || "too many" in message ->
                "Hiciste varios intentos. Espera un momento y vuelve a probar."
            "user not found" in message || "signups not allowed" in message ->
                "No encontramos esa cuenta. Comprueba el correo o crea una cuenta."
            "already registered" in message || "already exists" in message ->
                "Ese correo ya tiene una cuenta. Elige Ingresar."
            "row-level security" in message || "rls" in message ->
                "Supabase bloqueó la creación del negocio. Aplica la migración RLS pendiente."
            "membresía del propietario" in message ->
                "El negocio se creó, pero falta habilitar su acceso. Aplica la migración RLS pendiente."
            "network" in message || "unable to resolve" in message || "failed to connect" in message ->
                "Sin conexión. Comprueba internet y vuelve a intentar."
            else -> "No pudimos completar la operación. Intenta nuevamente."
        }
    }
}
