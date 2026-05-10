package com.oeims

import com.oeims.plugins.configureDatabase
import com.oeims.plugins.configureOpenApi
import com.oeims.plugins.configureRouting
import com.oeims.plugins.configureSecurity
import com.oeims.repositories.EventRepository
import com.oeims.repositories.ExamRepository
import com.oeims.repositories.ParticipantRepository
import com.oeims.repositories.SessionRepository
import com.oeims.repositories.UserRepository
import com.oeims.http.webSocketRoutes
import com.oeims.services.AuthService
import com.oeims.services.EventService
import com.oeims.services.ExamService
import com.oeims.services.HeartbeatService
import com.oeims.services.SessionService
import com.oeims.services.loadHeartbeatConfig
import com.oeims.services.loadJwtConfig
import com.oeims.websocket.ConnectionRegistry
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun Application.module() {
    // ── Serialization ─────────────────────────────────────────────────────────
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }

    // ── WebSockets ────────────────────────────────────────────────────────────
    install(WebSockets) {
        pingPeriod = 30.seconds
        timeout = 60.seconds
    }

    // ── Database ──────────────────────────────────────────────────────────────
    configureDatabase()

    // ── Repositories ──────────────────────────────────────────────────────────
    val userRepository        = UserRepository()
    val examRepository        = ExamRepository()
    val sessionRepository     = SessionRepository()
    val participantRepository = ParticipantRepository()
    val eventRepository       = EventRepository()

    // ── Config ────────────────────────────────────────────────────────────────
    val jwtConfig       = loadJwtConfig()
    val heartbeatConfig = loadHeartbeatConfig()

    // ── Realtime ──────────────────────────────────────────────────────────────
    val connectionRegistry = ConnectionRegistry()

    // ── Services ──────────────────────────────────────────────────────────────
    val authService      = AuthService(userRepository, jwtConfig)
    val examService      = ExamService(examRepository)
    val sessionService   = SessionService(sessionRepository, examRepository, participantRepository, userRepository)
    val eventService     = EventService(eventRepository, participantRepository, connectionRegistry)
    val heartbeatService = HeartbeatService(participantRepository, connectionRegistry, heartbeatConfig)

    // ── Security ──────────────────────────────────────────────────────────────
    configureSecurity(jwtConfig)

    // ── Routing ───────────────────────────────────────────────────────────────
    configureRouting(authService, examService, sessionService, eventService)

    // ── WebSocket routes ──────────────────────────────────────────────────────
    routing {
        webSocketRoutes(connectionRegistry, eventService, participantRepository)
    }

    // ── API Docs ──────────────────────────────────────────────────────────────
    configureOpenApi()

    // ── Heartbeat sweeper ─────────────────────────────────────────────────────
    monitor.subscribe(ApplicationStarted) {
        heartbeatService.start(this)
    }
}
