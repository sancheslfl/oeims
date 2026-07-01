package com.oeims.http

import com.auth0.jwt.interfaces.Claim
import com.oeims.models.UnauthorizedException
import com.oeims.models.ValidationException
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import java.util.UUID

/**
 * Returns the authenticated user's UUID from the JWT principal.
 *
 * @throws UnauthorizedException if no authenticated JWT principal is present,
 * or if the `userId` claim is missing or invalid.
 */
fun ApplicationCall.userId(): UUID =
    jwtUuidClaim("userId")

/**
 * Returns the authenticated session participant's UUID from the JWT principal.
 *
 * @throws UnauthorizedException if no authenticated JWT principal is present,
 * or if the `participantId` claim is missing or invalid.
 */
fun ApplicationCall.participantId(): UUID =
    jwtUuidClaim("participantId")

/**
 * Returns a UUID from a request path or query parameter.
 *
 * @param name The parameter name.
 *
 * @throws ValidationException if the parameter is missing or is not a valid UUID.
 */
fun ApplicationCall.uuidParam(name: String): UUID {
    val value = parameters[name]
        ?: throw ValidationException("Missing UUID parameter '$name'")

    return uuidParam(value, name)
}

/**
 * Parses a UUID from a raw request value.
 *
 * @param value The raw UUID value.
 * @param fieldName The field name used in the error message.
 *
 * @throws ValidationException if the value is not a valid UUID.
 */
fun ApplicationCall.uuidParam(value: String, fieldName: String): UUID =
    try {
        UUID.fromString(value)
    } catch (_: IllegalArgumentException) {
        throw ValidationException("Invalid UUID for field '$fieldName'")
    }

/**
 * Returns a UUID claim from the authenticated JWT principal.
 *
 * @param name The JWT claim name.
 *
 * @throws UnauthorizedException if no authenticated JWT principal is present,
 * or if the claim is missing or is not a valid UUID.
 */
private fun ApplicationCall.jwtUuidClaim(name: String): UUID {
    val principal = authentication.principal<JWTPrincipal>()
        ?: throw UnauthorizedException("Missing authenticated user")

    val value = principal.payload.getClaim(name).stringValue()
        ?: throw UnauthorizedException("Missing authenticated claim '$name'")

    return try {
        UUID.fromString(value)
    } catch (_: IllegalArgumentException) {
        throw UnauthorizedException("Invalid authenticated claim '$name'")
    }
}

/**
 * Returns the claim value as a string, or null when the claim is missing or null.
 */
private fun Claim.stringValue(): String? =
    asString()?.takeIf { it.isNotBlank() }