package com.oeims.models.ids

import java.util.*

@JvmInline
value class ProfessorId(val value: UUID)

fun UUID.toProfessorId() = ProfessorId(this)