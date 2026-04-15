package com.oeims.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateExamRequest(
    val title: String,
    val description: String? = null,
    val durationMins: Int
)

@Serializable
data class ExamResponse(
    val id: String,
    val createdBy: String,
    val title: String,
    val description: String?,
    val durationMins: Int,
    val createdAt: String
)
