package com.oeims.routes

import com.oeims.dto.CreateExamRequest
import com.oeims.services.ExamService
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.examRoutes(examService: ExamService) {
    authenticate("auth-professor") {
        route("/exams") {

            // POST /exams — create a new exam
            post {
                val professorId = call.userId()
                val req = call.receive<CreateExamRequest>()
                val response = examService.createExam(professorId, req.title, req.description, req.durationMins)
                call.respond(HttpStatusCode.Created, response)
            }

            // GET /exams?title=... — filter by title, or return all
            get {
                val title = call.request.queryParameters["title"]
                val response = if (title != null) examService.getExamsByTitle(title)
                               else examService.getAllExams()
                call.respond(HttpStatusCode.OK, response)
            }

            // GET /exams/{id}
            get("/{id}") {
                val id = call.uuidParam("id")
                val response = examService.getExamById(id)
                call.respond(HttpStatusCode.OK, response)
            }
        }
    }
}
