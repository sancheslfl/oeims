package com.oeims.models

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.timestamp

enum class Severity { INFO, WARNING, CRITICAL }

fun String.toSeverity(): Severity? = when (this) {
    "Info" -> Severity.INFO
    "Warning" -> Severity.WARNING
    "Critical" -> Severity.CRITICAL
    else -> null
}

object Events : UUIDTable("events") {
    val participantId = reference("participant_id", Participants)
    val monitorName = varchar("monitor_name", 50)
    val message = varchar("message", 255)
    val severity = enumerationByName("severity", 16, Severity::class)
    val occurredAt = timestamp("occurred_at")
}
