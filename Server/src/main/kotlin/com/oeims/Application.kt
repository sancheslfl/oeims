package com.oeims

import com.oeims.http.webSocketRoutes
import com.oeims.plugins.configureDatabase
import com.oeims.plugins.configureOpenApi
import com.oeims.plugins.configureRouting
import com.oeims.plugins.configureSecurity
import com.oeims.repositories.*
import com.oeims.services.*
import com.oeims.sse.SseBroadcaster
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.server.websocket.*
import kotlinx.serialization.json.Json
import org.slf4j.event.Level
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
        pingPeriod   = 30.seconds
        timeout      = 60.seconds
        maxFrameSize = 64 * 1024L  // 64 KB
    }

    // SSE
    install(SSE)

    // Database
    configureDatabase()

    // Repositories
    val userRepository        = UserRepository()
    val examRepository        = ExamRepository()
    val sessionRepository     = SessionRepository()
    val participantRepository = ParticipantRepository()
    val eventRepository       = EventRepository()

    // Config
    val jwtConfig       = loadJwtConfig()
    val heartbeatConfig = loadHeartbeatConfig()

    // Realtime
    val sseBroadcaster = SseBroadcaster()

    // Services
    val authService      = AuthService(userRepository, jwtConfig)
    val examService      = ExamService(examRepository)
    val sessionService   = SessionService(sessionRepository, examRepository, participantRepository, userRepository, sseBroadcaster)
    val eventService     = EventService(eventRepository, participantRepository, sseBroadcaster)
    val heartbeatService = HeartbeatService(participantRepository, sseBroadcaster, heartbeatConfig)

    // JWT Authentication
    configureSecurity(jwtConfig)

    // Routing
    configureRouting(authService, examService, sessionService, eventService, sseBroadcaster)

    // WebSocket routes
    routing {
        webSocketRoutes(eventService, participantRepository)
    }

    // API Docs
    configureOpenApi()

    // Heartbeat sweeper
    monitor.subscribe(ApplicationStarted) {
        heartbeatService.start(this)
    }
}
