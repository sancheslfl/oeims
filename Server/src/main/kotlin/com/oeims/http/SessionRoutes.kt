package com.oeims.http

import com.oeims.models.dto.CreateSessionRequest
import com.oeims.models.dto.JoinSessionRequest
import com.oeims.models.ids.toExamId
import com.oeims.models.ids.toProfessorId
import com.oeims.models.ids.toSessionId
import com.oeims.models.ids.toStudentId
import com.oeims.models.toSessionCode
import com.oeims.services.EventService
import com.oeims.services.SessionService
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.sessionRoutes(
    sessionService: SessionService,
    eventService: EventService,
) {

    authenticate("auth-professor") {

        // POST /sessions - create a session for an exam
        post("/sessions") {
            val professorId = call.userId()
            val req = call.receive<CreateSessionRequest>()
            val examId = call.uuidParam(req.examId, "examId")
            val response = sessionService.createSession(professorId.toProfessorId(), examId.toExamId())
            call.respond(HttpStatusCode.Created, response)
        }

        // POST /sessions/join-as-supervisor - professor joins an active session by code
        post("/sessions/join-as-supervisor") {
            val professorId = call.userId()
            val req = call.receive<JoinSessionRequest>()
            val response = sessionService.joinAsAdditionalSupervisor(req.code.toSessionCode(), professorId.toProfessorId())
            call.respond(HttpStatusCode.OK, response)
        }

        // GET /sessions/active - all currently open sessions
        get("/sessions/active") {
            val response = sessionService.getActiveSessions()
            call.respond(HttpStatusCode.OK, response)
        }

        // GET /sessions/current - latest pending or active session for professor
        get("/sessions/current") {
            val professorId = call.userId()
            val response = sessionService.getCurrentSession(professorId.toProfessorId())

            if (response == null) {
                call.respond(HttpStatusCode.NoContent)
                return@get
            }

            call.respond(HttpStatusCode.OK, response)
        }

        route("/sessions/{id}") {

            // GET /sessions/{id}
            get {
                val sessionId = call.uuidParam("id")
                val response = sessionService.getSession(sessionId.toSessionId())
                call.respond(HttpStatusCode.OK, response)
            }

            // POST /sessions/{id}/start
            post("/start") {
                val professorId = call.userId()
                val sessionId = call.uuidParam("id")
                val response = sessionService.startSession(sessionId.toSessionId(), professorId.toProfessorId())
                call.respond(HttpStatusCode.OK, response)
            }

            // POST /sessions/{id}/end
            post("/end") {
                val professorId = call.userId()
                val sessionId = call.uuidParam("id")
                val response = sessionService.endSession(sessionId.toSessionId(), professorId.toProfessorId())
                call.respond(HttpStatusCode.OK, response)
            }

            // GET /sessions/{id}/participants
            get("/participants") {
                val sessionId = call.uuidParam("id")
                val response = sessionService.getParticipants(sessionId.toSessionId())
                call.respond(HttpStatusCode.OK, response)
            }

            // GET /sessions/{id}/events
            get("/events") {
                val sessionId = call.uuidParam("id")
                val response = eventService.getSessionEvents(sessionId.toSessionId())
                call.respond(HttpStatusCode.OK, response)
            }
        }
    }


    authenticate("auth-student") {

        // POST /sessions/join - join a session by code
        post("/sessions/join") {
            val studentId = call.userId()
            val req = call.receive<JoinSessionRequest>()
            val response = sessionService.joinSession(req.code.toSessionCode(), studentId.toStudentId())
            call.respond(HttpStatusCode.OK, response)
        }
    }
}
