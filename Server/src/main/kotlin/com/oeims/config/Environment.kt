package com.oeims.config

import io.ktor.http.Url
import io.ktor.server.config.ApplicationConfig

object Environment {
    object Keys {
        const val APP_ENVIRONMENT = "app.environment"
        const val APP_API_BASE_PATH = "app.api.base-path"
        const val APP_FRONTEND_BASE_URL = "app.frontend.base-url"
        const val DATABASE_PATH = "database.path"

        const val JWT_SECRET = "jwt.secret"
        const val JWT_ISSUER = "jwt.issuer"
        const val JWT_AUTH_AUDIENCE = "jwt.auth.audience"
        const val JWT_AUTH_REALM = "jwt.auth.realm"
        const val JWT_AUTH_EXPIRATION_MS = "jwt.auth.expiration-ms"
        const val JWT_EMAIL_VERIFICATION_AUDIENCE = "jwt.email-verification.audience"
        const val JWT_EMAIL_VERIFICATION_REALM = "jwt.email-verification.realm"
        const val JWT_EMAIL_VERIFICATION_EXPIRATION_MS = "jwt.email-verification.expiration-ms"
        const val JWT_SENTINEL_AUDIENCE = "jwt.sentinel.audience"
        const val JWT_SENTINEL_REALM = "jwt.sentinel.realm"
        const val JWT_SENTINEL_EXPIRATION_MS = "jwt.sentinel.expiration-ms"

        const val HEARTBEAT_INTERVAL_MS = "heartbeat.interval-ms"
        const val HEARTBEAT_TIMEOUT_MS = "heartbeat.timeout-ms"
    }

    object Variables {
        const val FRONTEND_BASE_URL = "FRONTEND_BASE_URL"
        const val DATABASE_PATH = "DATABASE_PATH"
        const val SMTP_HOST = "SMTP_HOST"
        const val SMTP_USERNAME = "SMTP_USERNAME"
        const val SMTP_PASSWORD = "SMTP_PASSWORD"
    }

    private lateinit var values: Values

    val name: String
        get() = values.name

    val apiBasePath: String
        get() = values.apiBasePath

    val frontendBaseUrl: Url
        get() = values.frontendBaseUrl

    val databasePath: String
        get() = values.databasePath

    fun configure(config: ApplicationConfig) {
        values = Values(
            name = config.property(Keys.APP_ENVIRONMENT).getString(),
            apiBasePath = config.property(Keys.APP_API_BASE_PATH).getString(),
            frontendBaseUrl = frontendBaseUrl(config),
            databasePath = environmentVariable(Variables.DATABASE_PATH)
                ?: config.property(Keys.DATABASE_PATH).getString(),
        )
    }

    fun requiredVariable(name: String): String =
        environmentVariable(name) ?: error("Missing required environment variable: $name")

    private fun frontendBaseUrl(config: ApplicationConfig): Url {
        val value = environmentVariable(Variables.FRONTEND_BASE_URL)
            ?: config.property(Keys.APP_FRONTEND_BASE_URL).getString()

        val url = Url(value.trimEnd('/'))

        require(url.host.isNotBlank()) {
            "${Keys.APP_FRONTEND_BASE_URL} must include a host"
        }

        return url
    }

    private fun environmentVariable(name: String): String? =
        System.getenv(name)?.takeIf { it.isNotBlank() }

    private data class Values(
        val name: String,
        val apiBasePath: String,
        val frontendBaseUrl: Url,
        val databasePath: String,
    )
}
