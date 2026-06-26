package com.oeims.http

import com.oeims.models.dto.CreateSessionRequest
import com.oeims.models.dto.EmailJoinRequest
import com.oeims.models.dto.JoinSessionRequest
import com.oeims.models.dto.VerifyJoinRequest
import com.oeims.models.ids.toExamId
import com.oeims.models.ids.toProfessorId
import com.oeims.models.ids.toSessionId
import com.oeims.models.toAllowedEmailDomain
import com.oeims.models.toEmail
import com.oeims.models.toEmailJoinToken
import com.oeims.models.toSessionCode
import com.oeims.services.EventService
import com.oeims.services.ParticipantService
import com.oeims.services.SessionService
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.sessionRoutes(
    sessionService: SessionService,
    participantService: ParticipantService,
    eventService: EventService,
) {
    route("/sessions") {

        // POST /sessions/{code}/join - request to join a session using a verified email flow
        post("/{code}/join") {
            val code = call.parameters["code"]
                ?: throw BadRequestException("Missing session code")

            val req = call.receive<EmailJoinRequest>()

            val response = participantService.requestJoin(
                code = code.toSessionCode(),
                email = req.email.toEmail(),
            )

            call.respond(HttpStatusCode.Accepted, response)
        }

        // POST /sessions/join/verify - verify join request token
        post("/join/verify") {
            val req = call.receive<VerifyJoinRequest>()

            val response = participantService.verifyJoin(
                token = req.token.toEmailJoinToken(),
            )

            call.respond(HttpStatusCode.OK, response)
        }

        authenticate("auth-professor") {

            // POST /sessions - create a session for an exam
            post {
                val professorId = call.userId()
                val req = call.receive<CreateSessionRequest>()
                val examId = call.uuidParam(req.examId, "examId")

                val response = sessionService.createSession(
                    professorId = professorId.toProfessorId(),
                    examId = examId.toExamId(),
                    allowedEmailDomain = req.allowedEmailDomain.toAllowedEmailDomain(),
                )

                call.respond(HttpStatusCode.Created, response)
            }

            // POST /sessions/join-as-supervisor - professor joins an active session by code
            post("/join-as-supervisor") {
                val professorId = call.userId()
                val req = call.receive<JoinSessionRequest>()

                val response = sessionService.joinAsAdditionalSupervisor(
                    code = req.code.toSessionCode(),
                    professorId = professorId.toProfessorId(),
                )

                call.respond(HttpStatusCode.OK, response)
            }

            // GET /sessions/active - all currently open sessions
            get("/active") {
                val response = sessionService.getActiveSessions()
                call.respond(HttpStatusCode.OK, response)
            }

            // GET /sessions/current - latest pending or active session for professor
            get("/current") {
                val professorId = call.userId()
                val response = sessionService.getCurrentSession(professorId.toProfessorId())

                if (response == null) {
                    call.respond(HttpStatusCode.NoContent)
                    return@get
                }

                call.respond(HttpStatusCode.OK, response)
            }

            route("/{id}") {

                // GET /sessions/{id}
                get {
                    val sessionId = call.uuidParam("id")
                    val response = sessionService.getSession(sessionId.toSessionId())
                    call.respond(HttpStatusCode.OK, response)
                }

                // POST /sessions/{id}/start
                post("/start") {
                    val professorId = call.userId().toProfessorId()
                    val sessionId = call.uuidParam("id").toSessionId()

                    val response = sessionService.startSession(
                        sessionId = sessionId,
                        professorId = professorId,
                    )

                    participantService.sendExamIdentityCodes(sessionId)

                    call.respond(HttpStatusCode.OK, response)
                }

                // POST /sessions/{id}/end
                post("/end") {
                    val professorId = call.userId()
                    val sessionId = call.uuidParam("id")

                    val response = sessionService.endSession(
                        sessionId = sessionId.toSessionId(),
                        professorId = professorId.toProfessorId(),
                    )

                    call.respond(HttpStatusCode.OK, response)
                }

                // GET /sessions/{id}/participants
                get("/participants") {
                    val sessionId = call.uuidParam("id")
                    val response = participantService.getParticipants(sessionId.toSessionId())
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
    }
}