package com.oeims.http

import io.ktor.http.*
import io.ktor.server.application.*

const val AUTH_COOKIE_NAME = "oeims_auth"

fun ApplicationCall.setAuthCookie(token: String, secure: Boolean) {
    response.cookies.append(
        Cookie(
            name = AUTH_COOKIE_NAME,
            value = token,
            httpOnly = true,
            secure = secure,
            path = "/",
            maxAge = 60 * 60 * 8,
            extensions = mapOf("SameSite" to "Lax")
        )
    )
}

fun ApplicationCall.clearAuthCookie(secure: Boolean) {
    response.cookies.append(
        Cookie(
            name = AUTH_COOKIE_NAME,
            value = "",
            httpOnly = true,
            secure = secure,
            path = "/",
            maxAge = 0,
            extensions = mapOf("SameSite" to "Lax")
        )
    )
}