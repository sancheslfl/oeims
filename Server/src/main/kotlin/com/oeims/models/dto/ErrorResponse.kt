package com.oeims.models.dto

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(val error: String)
