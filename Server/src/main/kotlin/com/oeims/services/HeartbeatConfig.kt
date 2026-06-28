package com.oeims.services

import io.ktor.server.application.*
import java.time.Duration

data class HeartbeatConfig(
    val interval: Duration,
    val timeout: Duration
)

fun Application.configureHeartbeat() = HeartbeatConfig(
    interval = Duration.ofMillis(environment.config.property("heartbeat.interval-ms").getString().toLong()),
    timeout = Duration.ofMillis(environment.config.property("heartbeat.timeout-ms").getString().toLong())
)
