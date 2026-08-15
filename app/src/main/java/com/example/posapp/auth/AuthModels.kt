package com.example.posapp.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class AuthMode {
    SIGN_IN,
    REGISTER
}

enum class AuthStep {
    LOADING,
    WELCOME,
    EMAIL,
    OTP,
    FIRST_BUSINESS,
    SESSION_ERROR,
    LOCAL_DATA_CONFLICT,
    AUTHENTICATED
}

data class AuthUiState(
    val step: AuthStep = AuthStep.LOADING,
    val mode: AuthMode = AuthMode.SIGN_IN,
    val email: String = "",
    val userId: String? = null,
    val business: RemoteBusiness? = null,
    val isSubmitting: Boolean = false,
    val showSignOutConfirmation: Boolean = false,
    val pendingSignOutChanges: Int = 0,
    val errorMessage: String? = null,
    val infoMessage: String? = null
)

@Serializable
data class RemoteBusiness(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    val name: String,
    val address: String? = null
)

@Serializable
internal data class CreateBusinessRequest(
    @SerialName("owner_id") val ownerId: String,
    val name: String
)
