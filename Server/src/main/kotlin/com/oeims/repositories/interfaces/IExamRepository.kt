package com.oeims.repositories.interfaces

import com.oeims.repositories.ExamRecord
import java.util.UUID

interface IExamRepository {
    suspend fun findById(id: UUID): ExamRecord?
    suspend fun findAll(): List<ExamRecord>
    suspend fun findByTitle(title: String): List<ExamRecord>
    suspend fun findByProfessor(professorId: UUID): List<ExamRecord>
    suspend fun create(createdBy: UUID, title: String, description: String?, durationMins: Int): ExamRecord
}
