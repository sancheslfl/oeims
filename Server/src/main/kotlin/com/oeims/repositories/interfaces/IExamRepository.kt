package com.oeims.repositories.interfaces

import com.oeims.models.ExamRecord
import java.util.*

interface IExamRepository {
    suspend fun findById(id: UUID): ExamRecord?
    suspend fun findAll(): List<ExamRecord>
    suspend fun findByTitle(title: String): List<ExamRecord>
    suspend fun findByProfessor(professorId: UUID): List<ExamRecord>
    suspend fun create(createdBy: UUID, title: String, description: String?, durationMins: Int): ExamRecord
}
