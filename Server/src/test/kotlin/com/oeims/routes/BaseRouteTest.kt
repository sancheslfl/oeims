package com.oeims.routes

import com.oeims.module
import com.oeims.models.*
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import java.sql.DriverManager

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
            "database.path"         to DB_URL,
            "jwt.secret"            to "test-secret-key-long-enough",
            "jwt.issuer"            to "oeims",
            "jwt.audience"          to "oeims-users",
            "jwt.realm"             to "OEIMS",
            "jwt.expiration-ms"     to "3600000",
            // Extremely long — ensures the heartbeat sweeper never fires during a test.
            "heartbeat.interval-ms" to "999999999",
            "heartbeat.timeout-ms"  to "999999999"
        )
    }

    /**
     * Drops and recreates every table before each test.
     * Order matters: child tables (with FK references) must be dropped first.
     */
    @BeforeEach
    fun resetDatabase() {
        transaction {
            SchemaUtils.drop(Events, Participants, Sessions, Exams, Users)
            SchemaUtils.create(Users, Exams, Sessions, Participants, Events)
        }
    }

    /**
     * Runs [block] inside a fully configured in-memory Ktor application.
     *
     * The application is torn down after [block] completes, but the underlying
     * SQLite database persists (kept alive by [keepAlive]).
     */
    fun routeTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        environment { config = testConfig }
        application { module() }
        block()
    }
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
