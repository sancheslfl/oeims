package com.oeims.http

import com.oeims.models.dto.CreateExamRequest
import com.oeims.models.toExamId
import com.oeims.models.toExamTitle
import com.oeims.models.toProfessorId
import com.oeims.services.ExamService
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.exams(examService: ExamService) {
    authenticate("auth-professor") {
        route("/exams") {

            // POST /exams — create a new exam
            post {
                val professorId = call.userId()
                val req = call.receive<CreateExamRequest>()
                val response = examService.createExam(
                    professorId.toProfessorId(),
                    req.title.toExamTitle(),
                    req.description,
                    req.durationMins
                )
                call.respond(HttpStatusCode.Created, response)
            }

            // GET /exams?title=... — filter by title, or return all
            get {
                val title = call.request.queryParameters["title"]
                val response = if (title != null) examService.getExamsByTitle(title.toExamTitle())
                else examService.getAllExams()
                call.respond(HttpStatusCode.OK, response)
            }

            // GET /exams/{id}
            get("/{id}") {
                val examId = call.uuidParam("id")
                val response = examService.getExamById(examId.toExamId())
                call.respond(HttpStatusCode.OK, response)
            }
        }
    }
}
