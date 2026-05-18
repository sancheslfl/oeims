package com.oeims.models.ids

import java.util.UUID

@JvmInline
value class ExamId(val value: UUID)

fun UUID.toExamId() = ExamId(this)