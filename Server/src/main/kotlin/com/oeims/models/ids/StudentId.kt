package com.oeims.models.ids

import java.util.UUID

@JvmInline
value class StudentId(val value: UUID)

fun UUID.toStudentId() = StudentId(this)