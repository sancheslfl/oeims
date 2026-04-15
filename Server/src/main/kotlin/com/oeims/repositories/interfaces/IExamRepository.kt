package com.oeims.repositories.interfaces

import com.oeims.repositories.ExamRecord
import java.util.UUID

interface IExamRepository {
    fun findById(id: UUID): ExamRecord?
    fun findAll(): List<ExamRecord>
    fun findByProfessor(professorId: UUID): List<ExamRecord>
    fun create(createdBy: UUID, title: String, description: String?, durationMins: Int): ExamRecord
}
