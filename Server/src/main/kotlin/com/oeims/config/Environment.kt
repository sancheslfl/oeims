package com.oeims.config

import io.ktor.server.config.ApplicationConfig

object Environment {
    private lateinit var _frontendBaseUrl: String

    val frontendBaseUrl: String
        get() = _frontendBaseUrl

    fun configure(config: ApplicationConfig) {
        _frontendBaseUrl = config
            .property("app.frontend.base-url")
            .getString()
            .trimEnd('/')
    }
}