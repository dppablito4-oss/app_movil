package com.example.posapp.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.example.posapp.data.sync.PendingLocalData

enum class AuthMode {
    SIGN_IN,
    REGISTER
}

enum class AuthStep {
    LOADING,
    WELCOME,
    SIGN_IN,
    REGISTER_ACCOUNT,
    REGISTER_BUSINESS,
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
    val displayName: String = "",
    val businessName: String = "",
    val businessAddress: String = "",
    val businessPhone: String = "",
    val logoPath: String? = null,
    val userId: String? = null,
    val business: RemoteBusiness? = null,
    val isSubmitting: Boolean = false,
    val showSignOutConfirmation: Boolean = false,
    val isPreparingSignOut: Boolean = false,
    val pendingSignOutChanges: Int = 0,
    val pendingLocalData: PendingLocalData = PendingLocalData(),
    val errorMessage: String? = null,
    val infoMessage: String? = null
)

internal fun AuthUiState.withBusinessRegistrationDraft(
    businessName: String,
    businessAddress: String,
    businessPhone: String,
    logoPath: String?
): AuthUiState = copy(
    businessName = businessName.trim(),
    businessAddress = businessAddress.trim(),
    businessPhone = businessPhone.trim(),
    logoPath = logoPath,
    errorMessage = null,
    infoMessage = null
)

internal fun AuthUiState.recoverBusinessRegistration(
    verifiedUserId: String,
    message: String
): AuthUiState = copy(
    step = AuthStep.REGISTER_BUSINESS,
    userId = verifiedUserId,
    isSubmitting = false,
    errorMessage = message,
    infoMessage = null
)

@Serializable
data class RemoteBusiness(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    val name: String,
    val address: String? = null,
    val phone: String? = null,
    @SerialName("logo_path") val logoPath: String? = null
)

@Serializable
internal data class CreateBusinessRequest(
    @SerialName("owner_id") val ownerId: String,
    val name: String,
    val address: String? = null,
    val phone: String? = null,
    @SerialName("logo_path") val logoPath: String? = null
)

@Serializable
internal data class BusinessLogoRpc(
    @SerialName("target_business_id") val businessId: String,
    @SerialName("target_logo_path") val logoPath: String
)
