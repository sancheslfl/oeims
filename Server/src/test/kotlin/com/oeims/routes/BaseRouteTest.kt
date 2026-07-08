package com.oeims.routes

import com.oeims.models.*
import com.oeims.models.dto.EmailJoinRequest
import com.oeims.models.dto.VerifyJoinRequest
import com.oeims.models.dto.VerifyJoinResponse
import com.oeims.module
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.sql.DriverManager
import java.time.Instant

abstract class BaseRouteTest {
    companion object {
        private const val DB_NAME = "routetest"
        const val DB_URL = "file:$DB_NAME?mode=memory&cache=shared"

        @Suppress("unused")
        private val keepAlive = DriverManager.getConnection("jdbc:sqlite:$DB_URL")

        init {
            Database.connect("jdbc:sqlite:$DB_URL", "org.sqlite.JDBC")
        }

        val testConfig = MapApplicationConfig(
            "app.environment" to "test",
            "app.api.base-path" to "",
            "app.frontend.base-url" to "http://localhost:5173",

            "database.path" to DB_URL,

            "jwt.secret" to "test-secret-key-long-enough",
            "jwt.issuer" to "oeims-test",

            "jwt.auth.audience" to "oeims-test",
            "jwt.auth.realm" to "oeims-test",
            "jwt.auth.expiration-ms" to "3600000",

            "jwt.email-verification.audience" to "oeims-test:email-verification",
            "jwt.email-verification.realm" to "oeims-test",
            "jwt.email-verification.expiration-ms" to "600000",

            "jwt.sentinel.audience" to "oeims-test:sentinel",
            "jwt.sentinel.realm" to "oeims-test",
            "jwt.sentinel.expiration-ms" to "7200000",

            "heartbeat.interval-ms" to "999999999",
            "heartbeat.timeout-ms" to "999999999",
        )
    }

    protected val capturingEmailSender = CapturingEmailSender()

    @BeforeEach
    fun resetDatabase() {
        capturingEmailSender.lastLink = null

        transaction {
            SchemaUtils.drop(Events, Participants, SessionJoins, SessionSupervisors, Sessions, Exams, Users)
            SchemaUtils.create(Users, Exams, Sessions, SessionSupervisors, SessionJoins, Participants, Events)
        }
    }

    fun routeTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        environment { config = testConfig }
        application { module(capturingEmailSender) }
        block()
    }

    suspend fun ApplicationTestBuilder.joinAsParticipant(code: String, email: String): JoinedParticipant {
        val http = jsonClient()

        http.post("/sessions/$code/join") {
            contentType(ContentType.Application.Json)
            setBody(EmailJoinRequest(email))
        }

        val link = capturingEmailSender.lastLink
            ?: error("No verification email was captured for $email")
        val token = URLDecoder.decode(link.substringAfter("token="), StandardCharsets.UTF_8)

        val verified = http.post("/sessions/join/verify") {
            contentType(ContentType.Application.Json)
            setBody(VerifyJoinRequest(token))
        }.body<VerifyJoinResponse>()

        return JoinedParticipant(verified.token, verified.participantId)
    }

    class CapturingEmailSender : EmailSender {
        @Volatile
        var lastLink: String? = null

        override suspend fun sendJoinVerification(to: String, verificationLink: String, expiresAt: Instant) {
            lastLink = verificationLink
        }
    }

    data class JoinedParticipant(val token: String, val participantId: String)
}

fun ApplicationTestBuilder.jsonClient(): HttpClient = createClient {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
}
