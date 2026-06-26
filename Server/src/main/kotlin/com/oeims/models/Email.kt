package com.oeims.models

@JvmInline
value class Email(val address: String) {

    init {
        if (!EMAIL_REGEX.matches(address)) throw ValidationException("Invalid email format")
    }

    companion object {
        private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}

fun String.toEmail() = Email(this)