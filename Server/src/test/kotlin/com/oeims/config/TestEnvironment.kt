package com.oeims.config

import io.ktor.server.config.MapApplicationConfig

object TestEnvironment {
    private const val NAME = "test"
    private const val API_BASE_PATH = ""
    private const val FRONTEND_BASE_URL = "http://localhost:5173"
    private const val JWT_SECRET = "test-secret-key-long-enough"
    private const val JWT_ISSUER = "oeims-test"
    private const val JWT_EXPIRATION_MS = "3600000"
    private const val EMAIL_TOKEN_EXPIRATION_MS = "600000"
    private const val SENTINEL_TOKEN_EXPIRATION_MS = "7200000"
    private const val HEARTBEAT_DISABLED_MS = "999999999"

    fun config(databasePath: String): MapApplicationConfig = MapApplicationConfig(
        Environment.Keys.APP_ENVIRONMENT to NAME,
        Environment.Keys.APP_API_BASE_PATH to API_BASE_PATH,
        Environment.Keys.APP_FRONTEND_BASE_URL to FRONTEND_BASE_URL,
        Environment.Keys.DATABASE_PATH to databasePath,

        Environment.Keys.JWT_SECRET to JWT_SECRET,
        Environment.Keys.JWT_ISSUER to JWT_ISSUER,

        Environment.Keys.JWT_AUTH_AUDIENCE to JWT_ISSUER,
        Environment.Keys.JWT_AUTH_REALM to JWT_ISSUER,
        Environment.Keys.JWT_AUTH_EXPIRATION_MS to JWT_EXPIRATION_MS,

        Environment.Keys.JWT_EMAIL_VERIFICATION_AUDIENCE to "$JWT_ISSUER:email-verification",
        Environment.Keys.JWT_EMAIL_VERIFICATION_REALM to JWT_ISSUER,
        Environment.Keys.JWT_EMAIL_VERIFICATION_EXPIRATION_MS to EMAIL_TOKEN_EXPIRATION_MS,

        Environment.Keys.JWT_SENTINEL_AUDIENCE to "$JWT_ISSUER:sentinel",
        Environment.Keys.JWT_SENTINEL_REALM to JWT_ISSUER,
        Environment.Keys.JWT_SENTINEL_EXPIRATION_MS to SENTINEL_TOKEN_EXPIRATION_MS,

        Environment.Keys.HEARTBEAT_INTERVAL_MS to HEARTBEAT_DISABLED_MS,
        Environment.Keys.HEARTBEAT_TIMEOUT_MS to HEARTBEAT_DISABLED_MS,
    )

    fun configure(databasePath: String = ":memory:") {
        Environment.configure(config(databasePath))
    }
}
