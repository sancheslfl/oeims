package com.oeims

import com.auth0.jwt.JWT
import com.oeims.config.Environment
import com.oeims.http.AUTH_COOKIE_NAME
import com.oeims.config.configureDatabase
import com.oeims.config.configureEmail
import com.oeims.config.configureOpenApi
import com.oeims.config.configureRouting
import com.oeims.config.configureSecurity
import com.oeims.repositories.*
import com.oeims.services.*
import com.oeims.connections.SseBroadcaster
import com.oeims.connections.WebSocketBroadcaster
import com.oeims.connections.WebSocketService
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.sse.*
import io.ktor.server.websocket.*
import kotlinx.serialization.json.Json
import org.slf4j.event.Level
import java.time.Clock
import java.util.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

fun main(args: Array<String>): Unit = EngineMain.main(args)

fun Application.module() {
    // CORS
    install(CORS) {
        allowHost("localhost:5173")
        allowCredentials = true

        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Options)

        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
    }

    // Serialization
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }

    // Request tracing
    install(CallId) {
        retrieveFromHeader(HttpHeaders.XRequestId)
        generate { UUID.randomUUID().toString() }
    }

    install(CallLogging) {
        level = Level.INFO
        callIdMdc("call-id")
        mdc("user-email") { call ->
            val token = call.request.parseAuthorizationHeader()
                ?.let { (it as? HttpAuthHeader.Single)?.blob }
                ?: call.request.queryParameters["token"]
                ?: call.request.cookies[AUTH_COOKIE_NAME]
            token?.let { raw ->
                try {
                    JWT.decode(raw).getClaim("email").asString()
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    // Rate limiting
    install(RateLimit) {
        register(RateLimitName("auth")) {
            rateLimiter(limit = 10, refillPeriod = 1.minutes)
            requestKey { call -> call.request.origin.remoteHost }
        }
    }

    // WebSockets
    install(WebSockets) {
        pingPeriod = 30.seconds
        timeout = 60.seconds
        maxFrameSize = 64 * 1024L  // 64 KB
    }

    // SSE
    install(SSE)

    // App variables
    Environment.configure(environment.config)

    // Database
    configureDatabase()

    // Clock
    val clock = Clock.systemDefaultZone()

    // Repositories
    val userRepository = UserRepository()
    val examRepository = ExamRepository()
    val sessionRepository = SessionRepository(clock)
    val participantRepository = ParticipantRepository()
    val eventRepository = EventRepository()

    // Config
    val authJwtSettings = configureAuthJwt()
    val sessionJwtSettings = SessionJwtSettings(configureEmailVerificationJwt(), authJwtSettings)
    val heartbeatConfig = configureHeartbeat()

    // Email service
    val smtpEmailSender = configureEmail()

    // Realtime
    val sseBroadcaster = SseBroadcaster()
    val webSocketService = WebSocketService(ev)

    // Services
    val authService = AuthService(userRepository, authJwtSettings)
    val examService = ExamService(examRepository)
    val sessionService = SessionService(sessionRepository, examRepository, participantRepository, eventRepository, sseBroadcaster)
    val participantService = ParticipantService(
        participantRepository,
        sessionRepository,
        sessionJwtSettings,
        sseBroadcaster,
        webSocketService,
        smtpEmailSender
    )
    val eventService = EventService(eventRepository, participantRepository, sessionRepository, sseBroadcaster)
    val heartbeatService = HeartbeatService(participantRepository, sessionRepository, sseBroadcaster, heartbeatConfig)

    // JWT Authentication
    configureSecurity(authJwtSettings)

    // Routing
    configureRouting(
        authService,
        examService,
        sessionService,
        participantService,
        eventService,
        sseBroadcaster,
        webSocketService,
    )

    // API Docs
    configureOpenApi()

    // Heartbeat sweeper
    monitor.subscribe(ApplicationStarted) {
        heartbeatService.start(this)
    }
}