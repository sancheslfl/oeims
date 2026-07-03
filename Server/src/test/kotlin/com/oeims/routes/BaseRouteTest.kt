package com.oeims.routes

import com.oeims.models.*
import com.oeims.models.dto.EmailJoinRequest
import com.oeims.models.dto.VerifyJoinRequest
import com.oeims.models.dto.VerifyJoinResponse
import com.oeims.module
import com.oeims.routes.BaseRouteTest.Companion.keepAlive
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

        /**
         * SQLite shared-cache in-memory URL.  Every connection that uses this URL
         * shares the same in-memory database for the duration of the JVM process.
         */
        const val DB_URL = "file:$DB_NAME?mode=memory&cache=shared"

        /**
         * Keeps the in-memory database alive across many [testApplication] calls.
         * Without this the DB would be destroyed the moment the first application
         * instance shuts down between tests.
         */
        @Suppress("unused")
        private val keepAlive = DriverManager.getConnection("jdbc:sqlite:$DB_URL")

        init {
            // Wire Exposed to the shared in-memory DB once.
            // Each testApplication call will also call Database.connect() via
            // configureDatabase(); that is harmless — they all point at the same URL.
            Database.connect("jdbc:sqlite:$DB_URL", "org.sqlite.JDBC")
        }

        /** Configuration shared by all route tests. */
        val testConfig = MapApplicationConfig(
            "database.path" to DB_URL,
            "app.api.base-path" to "",
            "app.frontend.base-url" to "http://localhost:5173",
            "jwt.secret" to "test-secret-key-long-enough",
            "jwt.issuer" to "oeims",
            "jwt.auth.audience" to "oeims",
            "jwt.auth.realm" to "oeims",
            "jwt.auth.expiration-ms" to "3600000",
            "jwt.email-join.audience" to "oeims:email-join",
            "jwt.email-join.realm" to "oeims",
            "jwt.email-join.expiration-ms" to "600000",
            // Extremely long — ensures the heartbeat sweeper never fires during a test.
            "heartbeat.interval-ms" to "999999999",
            "heartbeat.timeout-ms" to "999999999"
        )
    }

    /**
     * Drops and recreates every table before each test.
     * Order matters: child tables (with FK references) must be dropped first.
     */
    @BeforeEach
    fun resetDatabase() {
        transaction {
            SchemaUtils.drop(Events, Participants, SessionJoins, SessionSupervisors, Sessions, Exams, Users)
            SchemaUtils.create(Users, Exams, Sessions, SessionSupervisors, SessionJoins, Participants, Events)
        }
    }

    // Captures the link the join flow would have emailed, so tests can finish the handshake.
    protected val capturingEmailSender = CapturingEmailSender()

    fun routeTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        environment { config = testConfig }
        application { module(capturingEmailSender) }
        block()
    }

    // Runs the request → verify join flow and returns the Sentinel token and participant id.
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

/**
 * Creates a test HTTP client with JSON serialization pre-configured.
 * Call this inside a [routeTest] block where `this` is [ApplicationTestBuilder].
 */
fun ApplicationTestBuilder.jsonClient(): HttpClient = createClient {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
}
