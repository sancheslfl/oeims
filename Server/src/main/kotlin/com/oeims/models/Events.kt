package com.oeims.models

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.timestamp

// Mirrors the daemon's Severity enum (Daemon.Abstractions.Severity)
// Daemon uses PascalCase (Info/Warning/Critical); mapping happens at the DTO boundary.
enum class Severity { INFO, WARNING, CRITICAL }

object Events : UUIDTable("events") {
    val participantId = reference("participant_id", Participants)
    val monitorName   = varchar("monitor_name", 50)
    val message       = varchar("message", 255)
    val severity      = enumerationByName("severity", 16, Severity::class)
    val occurredAt    = timestamp("occurred_at")
}
