package com.oeims.config

import com.oeims.models.EmailSender
import com.oeims.models.SmtpEmailSender
import io.ktor.server.application.Application

private const val SMTP_PORT = 587
private const val EMAIL_FROM = "OEIMS <no-reply@oeims.test>"

fun Application.configureEmail(): EmailSender {
    return SmtpEmailSender(
        config = SmtpEmailConfig(
            host = Environment.requiredVariable(Environment.Variables.SMTP_HOST),
            port = SMTP_PORT,
            username = Environment.requiredVariable(Environment.Variables.SMTP_USERNAME),
            password = Environment.requiredVariable(Environment.Variables.SMTP_PASSWORD),
            from = EMAIL_FROM,
        ),
    )
}

data class SmtpEmailConfig(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val from: String,
)
