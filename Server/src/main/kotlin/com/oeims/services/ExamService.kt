package com.oeims.services

import com.oeims.dto.ExamResponse
import com.oeims.exceptions.NotFoundException
import com.oeims.exceptions.ValidationException
import com.oeims.repositories.ExamRecord
import com.oeims.repositories.interfaces.IExamRepository
import java.util.UUID

class ExamService(
    private val examRepository: IExamRepository
) {

    fun createExam(professorId: UUID, title: String, description: String?, durationMins: Int): ExamResponse {
        if (title.isBlank())
            throw ValidationException("Exam title cannot be blank")
        if (durationMins <= 0)
            throw ValidationException("Duration must be greater than 0")

        return examRepository.create(professorId, title, description, durationMins).toResponse()
    }

    fun getExamsByTitle(title: String): List<ExamResponse> {
        if (title.isBlank())
            throw ValidationException("Title cannot be blank")

        return examRepository.findByTitle(title).map { it.toResponse() }
    }

    fun getAllExams(): List<ExamResponse> =
        examRepository.findAll().map { it.toResponse() }

    fun getExamById(id: UUID): ExamResponse =
        examRepository.findById(id)?.toResponse()
            ?: throw NotFoundException("Exam not found")

    private fun ExamRecord.toResponse() = ExamResponse(
        id          = id.toString(),
        createdBy   = createdBy.toString(),
        title       = title,
        description = description,
        durationMins = durationMins,
        createdAt   = createdAt.toString()
    )
}
