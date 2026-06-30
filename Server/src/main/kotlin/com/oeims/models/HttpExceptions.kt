package com.oeims.models

/**
 * Base class for all domain exceptions.
 * Every exception that crosses a service boundary must be a subtype of this.
 * StatusPages maps each subtype to exactly one HTTP status code.
 */
sealed class HttpException(message: String) : Exception(message)

/** Mapped to 400 Bad Request where malformed domain input or failed business-rule validation. */
class ValidationException(message: String) : HttpException(message)

/** Mapped to 401 Unauthorized where credentials are present but wrong (e.g. bad password). */
class UnauthorizedException(message: String) : HttpException(message)

/** Mapped to 403 Forbidden where authenticated, but not allowed to act on this resource. */
class ForbiddenException(message: String) : HttpException(message)

/** Mapped to 404 Not Found where the requested resource does not exist. */
class NotFoundException(message: String) : HttpException(message)

/** Mapped to 409 Conflict where the operation is invalid given the current state of the resource. */
class ConflictException(message: String) : HttpException(message)

/**
 * Throws an [ValidationException] with the result of calling [lazyMessage] if the value is false.
 */
inline fun validate(
    value: Boolean,
    lazyMessage: () -> String
) {
    if (!value) {
        throw ValidationException(lazyMessage())
    }
}