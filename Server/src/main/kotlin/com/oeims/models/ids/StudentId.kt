package com.oeims.models.ids

import java.util.*

@JvmInline
value class StudentId(val value: UUID)

fun UUID.toStudentId() = StudentId(this)