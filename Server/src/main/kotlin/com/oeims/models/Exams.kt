package com.oeims.models

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.timestamp

object Exams : UUIDTable("exams") {
    val createdBy    = reference("created_by", Users)
    val title        = varchar("title", 255) // TODO: Tighten the length
    val description  = text("description").nullable()
    val durationMins = integer("duration_mins")
    val createdAt    = timestamp("created_at")
}
