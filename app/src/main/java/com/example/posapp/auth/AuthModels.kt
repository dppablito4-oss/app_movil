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
    val pendingSignOutChanges: Int = 0,
    val errorMessage: String? = null,
    val infoMessage: String? = null
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
internal data class ProfileUpsert(
    val id: String,
    @SerialName("display_name") val displayName: String
)

@Serializable
internal data class BusinessLogoRpc(
    @SerialName("target_business_id") val businessId: String,
    @SerialName("target_logo_path") val logoPath: String
)
