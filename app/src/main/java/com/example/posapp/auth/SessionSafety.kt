package com.example.posapp.auth

import com.example.posapp.data.normalizedUuidOrNull
import io.github.jan.supabase.postgrest.exception.PostgrestRestException

internal fun shouldClearForAuthenticatedUser(previousUserId: String?, authenticatedUserId: String): Boolean {
    val current = authenticatedUserId.normalizedUuidOrNull() ?: return true
    val previous = previousUserId?.trim()?.takeIf(String::isNotEmpty) ?: return false
    return previous.normalizedUuidOrNull() != current
}

internal fun shouldDiscardCachedBusiness(
    remoteRequestSucceeded: Boolean,
    remoteBusinessFound: Boolean,
    cachedBusinessExists: Boolean
): Boolean = remoteRequestSucceeded && !remoteBusinessFound && cachedBusinessExists

internal fun isInvalidRemoteSession(error: Throwable): Boolean {
    val statusCode = (error as? PostgrestRestException)?.statusCode
    if (statusCode == 401) return true
    val text = generateSequence(error as Throwable?) { it.cause }
        .take(8)
        .joinToString(" ") { "${it::class.java.simpleName} ${it.message.orEmpty()}" }
        .lowercase()
    return listOf(
        "invalid jwt",
        "jwt expired",
        "session_not_found",
        "refresh_token_not_found",
        "user not found",
        "user_not_found",
        "invalid refresh token"
    ).any(text::contains)
}

/** Remote logout is best effort; device data must always be removed. */
internal suspend fun signOutAndAlwaysClear(
    remoteSignOut: suspend () -> Unit,
    clearLocalData: suspend () -> Unit
) {
    try {
        runCatching { remoteSignOut() }
    } finally {
        clearLocalData()
    }
}
