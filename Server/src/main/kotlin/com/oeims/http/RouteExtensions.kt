package com.oeims.http

import com.oeims.exceptions.ValidationException
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import java.util.UUID

// Extract the authenticated user's UUID from the JWT principal.
fun ApplicationCall.userId(): UUID {
    val principal = authentication.principal<JWTPrincipal>()!!
    return UUID.fromString(principal.payload.getClaim("userId").asString())
}

// Parse a path parameter as a UUID, throwing IllegalArgumentException on bad input
// so StatusPages maps it to a 400 automatically.
fun ApplicationCall.uuidParam(name: String): UUID =
    try {
        UUID.fromString(parameters[name])
    } catch (e: IllegalArgumentException) {
        throw ValidationException("Invalid UUID for parameter '$name'")
    }

// Overload for parsing a UUID that came from a request body field (not a path param).
fun ApplicationCall.uuidParam(value: String, fieldName: String): UUID =
    try {
        UUID.fromString(value)
    } catch (e: IllegalArgumentException) {
        throw ValidationException("Invalid UUID for field '$fieldName'")
    }
