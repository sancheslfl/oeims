package com.oeims.models

import com.oeims.config.SmtpEmailConfig
import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.Date
import java.util.Properties

/**
 *
 */
interface EmailSender {
    suspend fun sendJoinVerification(
        to: String,
        verificationLink: String,
        expiresAt: Instant,
    )
}

/**
 *
 */
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

/**
 *
 */
class SmtpEmailSender(
    private val config: SmtpEmailConfig,
) : EmailSender {

    override suspend fun sendJoinVerification(
        to: String,
        verificationLink: String,
        expiresAt: Instant,
    ) {
        withContext(Dispatchers.IO) {
            val session = createSession()

            val message = MimeMessage(session).apply {
                setFrom(InternetAddress.parse(config.from, false).single())
                setRecipient(Message.RecipientType.TO, InternetAddress(to))
                subject = "Verify your exam session email"
                sentDate = Date.from(Instant.now())

                setHeader("Auto-Submitted", "auto-generated")

                setText(
                    buildJoinVerificationText(
                        verificationLink = verificationLink,
                        expiresAt = expiresAt,
                    ),
                    Charsets.UTF_8.name(),
                )
            }

            Transport.send(message)
        }
    }

    private fun createSession(): Session {
        val properties = Properties().apply {
            put("mail.transport.protocol", "smtp")
            put("mail.smtp.host", config.host)
            put("mail.smtp.port", config.port.toString())
            put("mail.smtp.auth", "true")

            put("mail.smtp.starttls.enable", "true")
            put("mail.smtp.starttls.required", "true")
            put("mail.smtp.ssl.checkserveridentity", "true")

            put("mail.smtp.connectiontimeout", "10000")
            put("mail.smtp.timeout", "10000")
            put("mail.smtp.writetimeout", "10000")
        }

        return Session.getInstance(
            properties,
            object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(
                        config.username,
                        config.password,
                    )
                }
            },
        )
    }

    private fun buildJoinVerificationText(
        verificationLink: String,
        expiresAt: Instant,
    ): String {
        return """
            Hello,

            Use this link to verify your email and join the exam session:

            $verificationLink

            This link expires at $expiresAt and can only be used once.

            If you did not request this email, you can ignore it.
        """.trimIndent()
    }
}