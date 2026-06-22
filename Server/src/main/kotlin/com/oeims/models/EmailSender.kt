package com.oeims.models

import java.time.Instant

interface EmailSender {
    suspend fun sendJoinVerification(
        to: String,
        verificationLink: String,
        expiresAt: Instant,
    )
}