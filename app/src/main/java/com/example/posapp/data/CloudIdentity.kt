package com.example.posapp.data

import java.util.UUID

private val canonicalUuid = Regex(
    "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
)

/** Returns a canonical UUID or null. Blank and malformed identities never reach Supabase. */
internal fun String?.normalizedUuidOrNull(): String? {
    val candidate = this?.trim()?.takeIf { it.matches(canonicalUuid) } ?: return null
    return runCatching { UUID.fromString(candidate).toString() }.getOrNull()
}

internal fun String?.requireCloudUuid(fieldName: String): String =
    normalizedUuidOrNull()
        ?: throw IllegalArgumentException("$fieldName no contiene un UUID valido")
