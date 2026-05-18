package com.oeims.models.ids

import java.util.UUID

@JvmInline
value class SessionId(val value: UUID)

fun UUID.toSessionId() = SessionId(this)