package com.oeims.models.ids

import java.util.UUID

@JvmInline
value class ParticipantId(val value: UUID)

fun UUID.toParticipantId() = ParticipantId(this)