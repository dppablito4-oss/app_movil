package com.example.posapp.auth

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.posapp.BuildConfig
import com.example.posapp.data.remote.SupabaseProvider
import com.example.posapp.data.sync.CloudSyncScheduler
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private companion object {
        const val MISSING_SUPABASE_CONFIGURATION_MESSAGE =
            "Falta configurar Supabase. Agrega SUPABASE_URL y SUPABASE_PUBLISHABLE_KEY en local.properties."
    }

    private val repository = AuthRepository(application.applicationContext)
    private var pendingPassword: String? = null
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        if (SupabaseProvider.isConfigured) {
            restoreSession()
            observeSessionChanges()
        } else {
            _uiState.value = AuthUiState(
                step = AuthStep.SESSION_ERROR,
                errorMessage = MISSING_SUPABASE_CONFIGURATION_MESSAGE
            )
        }
    }

    fun chooseMode(mode: AuthMode) {
        pendingPassword = null
        _uiState.value = AuthUiState(
            step = if (mode == AuthMode.SIGN_IN) AuthStep.SIGN_IN else AuthStep.REGISTER_ACCOUNT,
            mode = mode
        )
    }

    fun backToWelcome() {
        _uiState.value = AuthUiState(step = AuthStep.WELCOME)
    }

    fun backToEmail() {
        _uiState.value = _uiState.value.copy(
            step = if (_uiState.value.mode == AuthMode.REGISTER) AuthStep.REGISTER_BUSINESS else AuthStep.SIGN_IN,
            isSubmitting = false,
            errorMessage = null,
            infoMessage = null
        )
    }

    fun signInWithPassword(rawEmail: String, password: String) {
        val error = AuthInputValidator.emailError(rawEmail)
            ?: if (password.isBlank()) "Escribe tu contraseña." else null
        if (error != null) {
            _uiState.value = _uiState.value.copy(errorMessage = error)
            return
        }
        val email = AuthInputValidator.normalizeEmail(rawEmail)
        submit(AuthOperation.SIGN_IN_PASSWORD) { openAuthenticatedUser(repository.signInWithPassword(email, password)) }
    }

    fun signInWithGoogle(idToken: String, nonce: String) {
        submit(AuthOperation.GOOGLE_SIGN_IN) {
            openAuthenticatedUser(repository.signInWithGoogle(idToken, nonce))
        }
    }

    fun reportGoogleSignInFailure(error: Throwable) {
        if (BuildConfig.DEBUG) {
            Log.e("SpaceSaleAuth", "operation=${AuthOperation.GOOGLE_SIGN_IN} category=${classifyAuthFailure(error)} type=${error::class.java.simpleName}")
        }
        _uiState.value = _uiState.value.copy(
            isSubmitting = false,
            errorMessage = authUserMessage(AuthOperation.GOOGLE_SIGN_IN, classifyAuthFailure(error))
        )
    }

    fun continueRegistration(
        rawName: String,
        rawEmail: String,
        password: String,
        confirmation: String
    ) {
        val error = AuthInputValidator.displayNameError(rawName)
            ?: AuthInputValidator.emailError(rawEmail)
            ?: AuthInputValidator.passwordError(password)
            ?: AuthInputValidator.passwordConfirmationError(password, confirmation)
        if (error != null) {
            _uiState.value = _uiState.value.copy(errorMessage = error)
            return
        }
        pendingPassword = password
        _uiState.value = _uiState.value.copy(
            step = AuthStep.REGISTER_BUSINESS,
            email = AuthInputValidator.normalizeEmail(rawEmail),
            displayName = rawName.trim(),
            errorMessage = null
        )
    }

    fun submitRegistrationBusiness(
        rawBusinessName: String,
        address: String,
        phone: String,
        logoPath: String?
    ) {
        val error = AuthInputValidator.businessNameError(rawBusinessName)
        if (error != null) {
            _uiState.value = _uiState.value.copy(errorMessage = error)
            return
        }
        val state = _uiState.value
        submit(AuthOperation.SEND_OTP) {
            repository.sendOtp(state.email, createUser = true)
            _uiState.value = _uiState.value.copy(
                step = AuthStep.OTP,
                businessName = rawBusinessName.trim(),
                businessAddress = address.trim(),
                businessPhone = phone.trim(),
                logoPath = logoPath,
                isSubmitting = false,
                infoMessage = "Te enviamos un código de ${AuthInputValidator.OTP_LENGTH} dígitos."
            )
        }
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
        submit(AuthOperation.SEND_OTP) {
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
        submit(AuthOperation.SEND_OTP) {
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
        submit(AuthOperation.VERIFY_OTP) {
            val state = _uiState.value
            val user = repository.verifyOtp(
                email = email,
                token = token,
                isRegistration = state.mode == AuthMode.REGISTER
            )
            if (state.mode == AuthMode.REGISTER && state.businessName.isNotBlank()) {
                val password = pendingPassword ?: error("La contraseña temporal se perdió. Vuelve a iniciar el registro.")
                repository.setPasswordAndProfile(user.id, password, state.displayName)
                var remoteLogoPath: String? = null
                val business = repository.createBusiness(
                    user.id,
                    state.businessName,
                    state.businessAddress,
                    state.businessPhone
                )
                if (!state.logoPath.isNullOrBlank()) {
                    remoteLogoPath = runCatching {
                        repository.uploadBusinessLogo(business.id, state.logoPath)
                    }.getOrNull()
                }
                val completed = business.copy(logoPath = remoteLogoPath)
                repository.cacheBusiness(user.id, completed)
                repository.saveLocalProfile(state.displayName, completed, state.logoPath)
                repository.bindLocalDataTo(user.id, completed.id)
                pendingPassword = null
                _uiState.value = state.copy(
                    step = AuthStep.AUTHENTICATED,
                    userId = user.id,
                    business = completed,
                    isSubmitting = false,
                    errorMessage = null,
                    infoMessage = if (state.logoPath != null && remoteLogoPath == null) "Cuenta creada; el logo quedó guardado localmente." else null
                )
                CloudSyncScheduler.schedule(getApplication<Application>().applicationContext)
            } else {
                openAuthenticatedUser(user)
            }
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
        submit(AuthOperation.CREATE_BUSINESS) {
            val business = repository.createBusiness(userId, rawName)
            repository.bindLocalDataTo(userId, business.id)
            _uiState.value = _uiState.value.copy(
                step = AuthStep.AUTHENTICATED,
                business = business,
                isSubmitting = false,
                errorMessage = null,
                infoMessage = null
            )
            CloudSyncScheduler.schedule(getApplication<Application>().applicationContext)
        }
    }

    fun retrySession() {
        if (_uiState.value.isSubmitting) return
        if (!SupabaseProvider.isConfigured) {
            _uiState.value = AuthUiState(
                step = AuthStep.SESSION_ERROR,
                errorMessage = MISSING_SUPABASE_CONFIGURATION_MESSAGE
            )
            return
        }
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
        if (_uiState.value.isSubmitting || _uiState.value.isPreparingSignOut) return
        _uiState.value = _uiState.value.copy(
            isSubmitting = true,
            isPreparingSignOut = true,
            showSignOutConfirmation = false,
            errorMessage = null,
            infoMessage = null
        )
        viewModelScope.launch {
            runCatching {
                if (_uiState.value.step == AuthStep.LOCAL_DATA_CONFLICT) {
                    repository.signOutPreservingLocalData()
                    return@runCatching null
                }
                val prepared = repository.preparePendingLocalData()
                if (prepared.total == 0) prepared else repository.tryImmediateSync()
            }.onSuccess { pending ->
                if (pending == null) {
                    _uiState.value = AuthUiState(step = AuthStep.WELCOME)
                } else if (pending.total == 0) {
                    runCatching { repository.signOutAndClearLocalData() }
                        .onSuccess { _uiState.value = AuthUiState(step = AuthStep.WELCOME) }
                        .onFailure { error -> showSignOutFailure(error) }
                } else {
                    CloudSyncScheduler.schedule(getApplication<Application>().applicationContext)
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        isPreparingSignOut = false,
                        showSignOutConfirmation = true,
                        pendingSignOutChanges = pending.total,
                        pendingLocalData = pending,
                        infoMessage = "Todavía hay cambios guardados solo en este dispositivo."
                    )
                }
            }.onFailure { error ->
                showSignOutFailure(error)
            }
        }
    }

    fun retrySignOut() {
        if (_uiState.value.isSubmitting) return
        _uiState.value = _uiState.value.copy(showSignOutConfirmation = false)
        signOut()
    }

    fun cancelSignOut() {
        _uiState.value = _uiState.value.copy(
            showSignOutConfirmation = false,
            isPreparingSignOut = false,
            pendingSignOutChanges = 0,
            pendingLocalData = com.example.posapp.data.sync.PendingLocalData(),
            infoMessage = null
        )
    }

    fun discardPendingAndSignOut() {
        if (_uiState.value.isSubmitting) return
        _uiState.value = _uiState.value.copy(
            isSubmitting = true,
            isPreparingSignOut = true,
            showSignOutConfirmation = false,
            errorMessage = null
        )
        viewModelScope.launch {
            runCatching { repository.signOutAndClearLocalData() }
                .onSuccess { _uiState.value = AuthUiState(step = AuthStep.WELCOME) }
                .onFailure { error ->
                    val failure = classifyAuthFailure(error)
                    if (BuildConfig.DEBUG) {
                        Log.e("SpaceSaleAuth", "operation=${AuthOperation.CLEAR_LOCAL_DATA} category=$failure type=${error::class.java.simpleName}")
                    }
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        isPreparingSignOut = false,
                        errorMessage = authUserMessage(AuthOperation.CLEAR_LOCAL_DATA, failure)
                    )
                }
        }
    }

    private fun showSignOutFailure(error: Throwable) {
        val failure = classifyAuthFailure(error)
        if (BuildConfig.DEBUG) {
            Log.e("SpaceSaleAuth", "operation=${AuthOperation.SIGN_OUT} category=$failure type=${error::class.java.simpleName}")
        }
        _uiState.value = _uiState.value.copy(
            isSubmitting = false,
            isPreparingSignOut = false,
            showSignOutConfirmation = false,
            errorMessage = authUserMessage(AuthOperation.SIGN_OUT, failure)
        )
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
                    errorMessage = friendlyMessage(AuthOperation.RESTORE_SESSION, error)
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
            repository.bindLocalDataTo(user.id, business.id)
            _uiState.value = _uiState.value.copy(
                step = AuthStep.AUTHENTICATED,
                business = business,
                isSubmitting = false,
                errorMessage = null,
                infoMessage = if (remoteResult.isFailure) "Trabajando sin conexión con los datos locales." else null
            )
            CloudSyncScheduler.schedule(getApplication<Application>().applicationContext)
        } else if (remoteResult.isFailure) {
            _uiState.value = _uiState.value.copy(
                step = AuthStep.SESSION_ERROR,
                isSubmitting = false,
                errorMessage = friendlyMessage(AuthOperation.OPEN_BUSINESS, remoteResult.exceptionOrNull()!!)
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

    private fun submit(operation: AuthOperation, block: suspend () -> Unit) {
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
                    errorMessage = friendlyMessage(operation, error)
                )
            }
        }
    }

    private fun friendlyMessage(operation: AuthOperation, error: Throwable): String {
        val failure = classifyAuthFailure(error)
        if (BuildConfig.DEBUG) {
            Log.e("SpaceSaleAuth", "operation=$operation category=$failure type=${error::class.java.simpleName}")
        }
        return authUserMessage(operation, failure)
    }

}
