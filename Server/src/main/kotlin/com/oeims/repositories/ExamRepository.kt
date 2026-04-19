package com.oeims.repositories

import com.oeims.models.Exams
import com.oeims.repositories.interfaces.IExamRepository
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

data class ExamRecord(
    val id: UUID,
    val createdBy: UUID,
    val title: String,
    val description: String?,
    val durationMins: Int,
    val createdAt: Instant
)

class ExamRepository : IExamRepository {

    override fun findById(id: UUID): ExamRecord? = transaction {
        Exams.selectAll()
            .where { Exams.id eq id }
            .singleOrNull()
            ?.toRecord()
    }

    override fun findByTitle(title: String): List<ExamRecord> = transaction {
        Exams.selectAll()
            .where { Exams.title eq title }
            .map { it.toRecord() }
    }

    override fun findByProfessor(professorId: UUID): List<ExamRecord> = transaction {
        Exams.selectAll()
            .where { Exams.createdBy eq professorId }
            .map { it.toRecord() }
    }

    override fun findAll(): List<ExamRecord> = transaction {
        Exams.selectAll().map { it.toRecord() }
    }

    override fun create(createdBy: UUID, title: String, description: String?, durationMins: Int): ExamRecord = transaction {
        val id = UUID.randomUUID()
        val now = Instant.now()
        Exams.insert {
            it[Exams.id] = id
            it[Exams.createdBy] = createdBy
            it[Exams.title] = title
            it[Exams.description] = description
            it[Exams.durationMins] = durationMins
            it[Exams.createdAt] = now
        }
        ExamRecord(id, createdBy, title, description, durationMins, now)
    }

    private fun ResultRow.toRecord() = ExamRecord(
        id          = this[Exams.id].value,
        createdBy   = this[Exams.createdBy].value,
        title       = this[Exams.title],
        description = this[Exams.description],
        durationMins= this[Exams.durationMins],
        createdAt   = this[Exams.createdAt]
    )
}
