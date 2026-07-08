package com.oeims.routes

import com.oeims.config.TestEnvironment
import com.oeims.models.EmailSender
import com.oeims.models.Events
import com.oeims.models.Exams
import com.oeims.models.Participants
import com.oeims.models.SessionJoins
import com.oeims.models.SessionSupervisors
import com.oeims.models.Sessions
import com.oeims.models.Users
import com.oeims.models.dto.EmailJoinRequest
import com.oeims.models.dto.VerifyJoinRequest
import com.oeims.models.dto.VerifyJoinResponse
import com.oeims.module
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.sql.DriverManager
import java.time.Instant

/**
 * Base class for route tests that boot the real Ktor application.
 *
 * It gives each test the boring setup it needs: the same test environment config,
 * a shared in-memory SQLite database, a clean schema before each test, and a fake
 * email sender so join-flow tests can grab the verification link without SMTP.
 */
abstract class BaseRouteTest {
    companion object {
        private const val DB_NAME = "routetest"
        private const val DB_URL = "file:$DB_NAME?mode=memory&cache=shared"
        private val testConfig = TestEnvironment.config(databasePath = DB_URL)

        @Suppress("unused")
        private val keepAlive = DriverManager.getConnection("jdbc:sqlite:$DB_URL")

        init {
            Database.connect("jdbc:sqlite:$DB_URL", "org.sqlite.JDBC")
        }
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

/**
 * Small JSON client for route tests.
 *
 * Ktor's test client does not automatically know how to send and read our JSON
 * DTOs, so tests call this instead of repeating the same plugin setup everywhere.
 */
fun ApplicationTestBuilder.jsonClient(): HttpClient = createClient {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
}
