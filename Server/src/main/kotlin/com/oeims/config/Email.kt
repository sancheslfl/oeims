package com.oeims.config

import com.oeims.models.EmailSender
import com.oeims.models.SmtpEmailSender
import io.ktor.server.application.Application

private const val SMTP_PORT = 587
private const val EMAIL_FROM = "OEIMS <no-reply@oeims.test>"

private const val SMTP_HOST_ENV = "SMTP_HOST"
private const val SMTP_USERNAME_ENV = "SMTP_USERNAME"
private const val SMTP_PASSWORD_ENV = "SMTP_PASSWORD"

fun Application.configureEmail(): EmailSender {
    return SmtpEmailSender(
        config = SmtpEmailConfig(
            host = requiredEnvironmentVariable(SMTP_HOST_ENV),
            port = SMTP_PORT,
            username = requiredEnvironmentVariable(SMTP_USERNAME_ENV),
            password = requiredEnvironmentVariable(SMTP_PASSWORD_ENV),
            from = EMAIL_FROM,
        ),
    )
}

private fun requiredEnvironmentVariable(name: String): String {
    return System.getenv(name)
        ?: error("Missing required environment variable: $name")
}

data class SmtpEmailConfig(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val from: String,
)