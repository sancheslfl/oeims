package com.oeims.repositories

import com.oeims.models.ExamRecord
import com.oeims.models.Exams
import com.oeims.repositories.interfaces.IExamRepository
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant
import java.util.*

class ExamRepository : IExamRepository {

    override suspend fun findById(id: UUID): ExamRecord? = newSuspendedTransaction(Dispatchers.IO) {
        Exams.selectAll()
            .where { Exams.id eq id }
            .singleOrNull()
            ?.toRecord()
    }

    override suspend fun findByTitle(title: String): List<ExamRecord> = newSuspendedTransaction(Dispatchers.IO) {
        Exams.selectAll()
            .where { Exams.title eq title }
            .map { it.toRecord() }
    }

    override suspend fun findByProfessor(professorId: UUID): List<ExamRecord> =
        newSuspendedTransaction(Dispatchers.IO) {
            Exams.selectAll()
                .where { Exams.createdBy eq professorId }
                .map { it.toRecord() }
        }

    override suspend fun findAll(): List<ExamRecord> = newSuspendedTransaction(Dispatchers.IO) {
        Exams.selectAll().map { it.toRecord() }
    }

    override suspend fun create(createdBy: UUID, title: String, description: String?, durationMins: Int): ExamRecord =
        newSuspendedTransaction(Dispatchers.IO) {
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
        id = this[Exams.id].value,
        createdBy = this[Exams.createdBy].value,
        title = this[Exams.title],
        description = this[Exams.description],
        durationMins = this[Exams.durationMins],
        createdAt = this[Exams.createdAt]
    )
}
