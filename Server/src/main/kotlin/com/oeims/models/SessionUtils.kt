package com.oeims.models

@JvmInline
value class SessionCode(val value: String) {

    init {
        if (!CODE_REGEX.matches(value))
            throw ValidationException("Invalid session code format")
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
        require(value.isNotBlank()) { "Allowed email domain cannot be blank" }
        require(value.contains(".")) { "Allowed email domain must contain a dot" }
    }

    fun allows(email: Email): Boolean =
        email.address.endsWith(value, ignoreCase = true)

    override fun toString(): String = value
}

fun String.toAllowedEmailDomain(): AllowedEmailDomain =
    AllowedEmailDomain(trim().lowercase())

@JvmInline
value class EmailJoinToken(val value: String) {
    init {
        require(value.isNotBlank()) { "Email join token cannot be blank" }
        require(value.count { it == '.' } == 2) { "Invalid JWT format" }
    }
}

fun String.toEmailJoinToken(): EmailJoinToken =
    EmailJoinToken(trim())