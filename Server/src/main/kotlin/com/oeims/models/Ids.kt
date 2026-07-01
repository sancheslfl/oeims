package com.oeims.models

import java.util.UUID

@JvmInline
value class ExamId(val value: UUID)

fun UUID.toExamId() = ExamId(this)


@JvmInline
value class ParticipantId(val value: UUID)

fun UUID.toParticipantId() = ParticipantId(this)

fun String.toParticipantId() = UUID.fromString(this).toParticipantId()


@JvmInline
value class ProfessorId(val value: UUID)

fun UUID.toProfessorId() = ProfessorId(this)


@JvmInline
value class SessionId(val value: UUID)

fun UUID.toSessionId() = SessionId(this)

fun String.toSessionId() = SessionId(UUID.fromString(this))


@JvmInline
value class StudentId(val value: UUID)

fun UUID.toStudentId() = StudentId(this)


@JvmInline
value class UserId(val value: UUID) {
    // TODO: More validation? Different exceptions?
}

fun UUID.toUserId() = UserId(this)