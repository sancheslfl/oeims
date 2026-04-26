package com.oeims.routes

import com.oeims.dto.CreateSessionRequest
import com.oeims.dto.JoinSessionRequest
import com.oeims.services.EventService
import com.oeims.services.SessionService
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.sessionRoutes(sessionService: SessionService, eventService: EventService) {

    // ── Professor endpoints ───────────────────────────────────────────────────

    authenticate("auth-professor") {

        // POST /sessions — create a session for an exam
        post("/sessions") {
            val professorId = call.userId()
            val req = call.receive<CreateSessionRequest>()
            val examId = call.uuidParam(req.examId, "examId")
            val response = sessionService.createSession(professorId, examId)
            call.respond(HttpStatusCode.Created, response)
        }

        route("/sessions/{id}") {

            // GET /sessions/{id}
            get {
                val sessionId = call.uuidParam("id")
                val response = sessionService.getSession(sessionId)
                call.respond(HttpStatusCode.OK, response)
            }

            // POST /sessions/{id}/start
            post("/start") {
                val professorId = call.userId()
                val sessionId = call.uuidParam("id")
                val response = sessionService.startSession(sessionId, professorId)
                call.respond(HttpStatusCode.OK, response)
            }

            // POST /sessions/{id}/end
            post("/end") {
                val professorId = call.userId()
                val sessionId = call.uuidParam("id")
                val response = sessionService.endSession(sessionId, professorId)
                call.respond(HttpStatusCode.OK, response)
            }

            // GET /sessions/{id}/participants
            get("/participants") {
                val sessionId = call.uuidParam("id")
                val response = sessionService.getParticipants(sessionId)
                call.respond(HttpStatusCode.OK, response)
            }

            // GET /sessions/{id}/events
            get("/events") {
                val sessionId = call.uuidParam("id")
                val response = eventService.getSessionEvents(sessionId)
                call.respond(HttpStatusCode.OK, response)
            }
        }
    }

    // ── Student endpoints ─────────────────────────────────────────────────────

    authenticate("auth-student") {

        // POST /sessions/join — join a session by code
        post("/sessions/join") {
            val studentId = call.userId()
            val req = call.receive<JoinSessionRequest>()
            val response = sessionService.joinSession(req.code, studentId)
            call.respond(HttpStatusCode.OK, response)
        }
    }
}
