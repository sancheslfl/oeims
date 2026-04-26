package com.oeims

import com.oeims.plugins.configureDatabase
import com.oeims.plugins.configureRouting
import com.oeims.plugins.configureSecurity
import com.oeims.repositories.EventRepository
import com.oeims.repositories.ExamRepository
import com.oeims.repositories.ParticipantRepository
import com.oeims.repositories.SessionRepository
import com.oeims.repositories.UserRepository
import com.oeims.services.AuthService
import com.oeims.services.EventService
import com.oeims.services.ExamService
import com.oeims.services.SessionService
import com.oeims.services.loadJwtConfig
import com.oeims.websocket.NoOpConnectionRegistry
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import kotlinx.serialization.json.Json

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

    // ── Database ──────────────────────────────────────────────────────────────
    configureDatabase()

    // ── Repositories ──────────────────────────────────────────────────────────
    val userRepository        = UserRepository()
    val examRepository        = ExamRepository()
    val sessionRepository     = SessionRepository()
    val participantRepository = ParticipantRepository()
    val eventRepository       = EventRepository()

    // ── Config ────────────────────────────────────────────────────────────────
    val jwtConfig = loadJwtConfig()

    // ── Services ──────────────────────────────────────────────────────────────
    val authService    = AuthService(userRepository, jwtConfig)
    val examService    = ExamService(examRepository)
    val sessionService = SessionService(sessionRepository, examRepository, participantRepository, userRepository)

    // ConnectionRegistry will be wired here once the WebSocket layer is implemented.
    // val connectionRegistry = ConnectionRegistry()
    // val eventService = EventService(eventRepository, participantRepository, connectionRegistry)
    // val heartbeatService = HeartbeatService(participantRepository, connectionRegistry, loadHeartbeatConfig())

    // ── Security ──────────────────────────────────────────────────────────────
    configureSecurity(jwtConfig)

    // ── Routing ───────────────────────────────────────────────────────────────
    // TODO: replace stub eventService with real one once ConnectionRegistry is ready
    val eventService = EventService(eventRepository, participantRepository, NoOpConnectionRegistry)
    configureRouting(authService, examService, sessionService, eventService)

    // ── WebSockets ────────────────────────────────────────────────────────────
    // TODO: configureWebSockets(connectionRegistry, eventService, sessionService)
}
