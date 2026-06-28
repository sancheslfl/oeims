package com.oeims.http

import com.oeims.models.UnauthorizedException
import com.oeims.models.ValidationException
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import java.util.*

/**
 * Returns the authenticated user's UUID from the JWT principal.
 *
 * @throws UnauthorizedException if no authenticated JWT principal is present.
 * @throws IllegalArgumentException if the `userId` claim is not a valid UUID.
 */
fun ApplicationCall.userId(): UUID {
    val principal = authentication.principal<JWTPrincipal>() ?: throw UnauthorizedException("Missing authenticated user")
    return UUID.fromString(principal.payload.getClaim("userId").asString())
}
/**
 * Returns the authenticated session participant's UUID from the JWT principal.
 *
 * @throws UnauthorizedException if no authenticated JWT principal is present.
 * @throws IllegalArgumentException if the `participantId` claim is not a valid UUID.
 */
fun ApplicationCall.participantId(): UUID {
    val principal = authentication.principal<JWTPrincipal>() ?: throw UnauthorizedException("Missing authenticated user")
    return UUID.fromString(principal.payload.getClaim("participantId").asString())
}

// Parse a path parameter as a UUID, throwing IllegalArgumentException on bad input
// so StatusPages maps it to a 400 automatically.
fun ApplicationCall.uuidParam(name: String): UUID =
    try {
        UUID.fromString(parameters[name])
    } catch (_: IllegalArgumentException) {
        throw ValidationException("Invalid UUID for parameter '$name'")
    }

// Overload for parsing a UUID that came from a request body field (not a path param).
fun ApplicationCall.uuidParam(value: String, fieldName: String): UUID =
    try {
        UUID.fromString(value)
    } catch (_: IllegalArgumentException) {
        throw ValidationException("Invalid UUID for field '$fieldName'")
    }
