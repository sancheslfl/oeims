package com.oeims.models.ids

import java.util.*

@JvmInline
value class SessionId(val value: UUID)

fun UUID.toSessionId() = SessionId(this)

fun String.toSessionId() = SessionId(UUID.fromString(this))