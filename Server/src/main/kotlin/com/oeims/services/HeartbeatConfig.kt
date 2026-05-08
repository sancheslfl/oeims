package com.oeims.services

import io.ktor.server.application.*

data class HeartbeatConfig(
    val intervalMs: Long,
    val timeoutMs: Long
)

fun Application.loadHeartbeatConfig() = HeartbeatConfig(
    intervalMs = environment.config.property("heartbeat.interval-ms").getString().toLong(),
    timeoutMs  = environment.config.property("heartbeat.timeout-ms").getString().toLong()
)
