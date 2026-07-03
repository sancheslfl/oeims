package com.oeims.config

import io.ktor.http.*
import io.ktor.server.config.*

// TODO: Turn into the interface for envs
object Environment {
    private lateinit var _frontendBaseUrl: Url

    val frontendBaseUrl: Url
        get() = _frontendBaseUrl

    fun configure(config: ApplicationConfig) {
        val frontendBaseUrl = Url(
            config.property("app.frontend.base-url")
                .getString()
                .trimEnd('/')
        )

        require(frontendBaseUrl.host.isNotBlank()) {
            "app.frontend.base-url must include a host"
        }

        _frontendBaseUrl = frontendBaseUrl
    }
}