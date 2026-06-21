package com.oeims.services

import com.oeims.exceptions.NotFoundException
import com.oeims.exceptions.ValidationException
import com.oeims.models.ExamTitle
import com.oeims.models.dto.ExamResponse
import com.oeims.models.ids.ExamId
import com.oeims.models.ids.ProfessorId
import com.oeims.repositories.ExamRecord
import com.oeims.repositories.interfaces.IExamRepository

class ExamService(
    private val examRepository: IExamRepository
) {

    // TODO: Maybe use Instant for time related params
    suspend fun createExam(
        professorId: ProfessorId,
        title: ExamTitle,
        description: String?,
        durationMins: Int
    ): ExamResponse {
        if (durationMins <= 0)
            throw ValidationException("Duration must be greater than 0")

        return examRepository
            .create(professorId.value, title.value, description, durationMins)
            .toResponse()
    }

    suspend fun getExamsByTitle(title: ExamTitle): List<ExamResponse> {
        return examRepository.findByTitle(title.value).map { it.toResponse() }
    }

    suspend fun getAllExams(): List<ExamResponse> =
        examRepository.findAll().map { it.toResponse() }

    suspend fun getExamById(id: ExamId): ExamResponse =
        examRepository.findById(id.value)?.toResponse()
            ?: throw NotFoundException("Exam not found")

    private fun ExamRecord.toResponse() = ExamResponse(
        id = id.toString(),
        createdBy = createdBy.toString(),
        title = title,
        description = description,
        durationMins = durationMins,
        createdAt = createdAt.toString()
    )
}
