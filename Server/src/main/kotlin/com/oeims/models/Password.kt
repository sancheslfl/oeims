package com.oeims.models

import com.oeims.exceptions.ValidationException

@JvmInline
value class Password(val value: String) {

    init {
        // TODO: Create DSL function to throw validation exceptions
        if (value.length < 8) throw ValidationException("Password must be at least 8 characters")
    }
}

fun String.toPassword() = Password(this)
