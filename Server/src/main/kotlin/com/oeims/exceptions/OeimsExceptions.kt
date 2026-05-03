package com.oeims.exceptions

/**
 * Base class for all domain exceptions in OEIMS.
 * Every exception that crosses a service boundary must be a subtype of this.
 * StatusPages maps each subtype to exactly one HTTP status code.
 */
sealed class OeimsException(message: String) : Exception(message)

/** 400 Bad Request — malformed input or failed business-rule validation. */
class ValidationException(message: String) : OeimsException(message)

/** 401 Unauthorized — credentials are present but wrong (e.g. bad password). */
class UnauthorizedException(message: String) : OeimsException(message)

/** 403 Forbidden — authenticated, but not permitted to act on this resource. */
class ForbiddenException(message: String) : OeimsException(message)

/** 404 Not Found — the requested resource does not exist. */
class NotFoundException(message: String) : OeimsException(message)

/** 409 Conflict — the operation is invalid given the current state of the resource. */
class ConflictException(message: String) : OeimsException(message)
