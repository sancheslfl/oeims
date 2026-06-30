package com.oeims.models.ids

import java.util.*

@JvmInline
value class ExamId(val value: UUID)

fun UUID.toExamId() = ExamId(this)