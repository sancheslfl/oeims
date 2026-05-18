package com.oeims.models

import com.oeims.exceptions.ValidationException

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