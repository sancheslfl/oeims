package com.oeims.models.ids

import java.util.*

@JvmInline
value class ParticipantId(val value: UUID)

fun UUID.toParticipantId() = ParticipantId(this)

fun String.toParticipantId() = UUID.fromString(this).toParticipantId()