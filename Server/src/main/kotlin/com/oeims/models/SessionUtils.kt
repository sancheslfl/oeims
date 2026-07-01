package com.oeims.models

@JvmInline
value class SessionCode(val value: String) {

    init {
        validate(CODE_REGEX.matches(value)) { "Invalid session code format" }
    }

    companion object {
        // ABC123
        private val CODE_REGEX = Regex("^[A-Z0-9]{6}$")
    }
}

fun String.toSessionCode(): SessionCode = SessionCode(this)

@JvmInline
value class AllowedEmailDomain(val value: String) {
    init {
        validate(value.isNotBlank()) { "Allowed email domain cannot be blank" }
        validate(value.contains(".")) { "Allowed email domain must contain a dot" }
    }

    fun allows(email: Email): Boolean =
        email.address.endsWith(value, ignoreCase = true)

    override fun toString(): String = value
}

fun String.toAllowedEmailDomain(): AllowedEmailDomain =
    AllowedEmailDomain(trim().lowercase())

@JvmInline
value class JwtToken(val value: String) {
    init {
        validate(value.isNotBlank()) { "Email join token cannot be blank" }
        validate(value.count { it == '.' } == 2) { "Invalid JWT format" }
    }
}

fun String.toJwtToken(): JwtToken =
    JwtToken(trim())