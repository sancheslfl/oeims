package com.oeims.services

import io.ktor.server.application.*

data class JwtConfig(
    val secret: String,
    val issuer: String,
    val audience: String,
    val realm: String,
    val expirationMs: Long
)

fun Application.loadJwtConfig() = JwtConfig(
    // JWT_SECRET env var takes priority over the yaml placeholder so the yaml file
    // never needs to contain a real secret (safe to commit).
    secret       = System.getenv("JWT_SECRET")
                       ?: environment.config.property("jwt.secret").getString(),
    issuer       = environment.config.property("jwt.issuer").getString(),
    audience     = environment.config.property("jwt.audience").getString(),
    realm        = environment.config.property("jwt.realm").getString(),
    expirationMs = environment.config.property("jwt.expiration-ms").getString().toLong()
)
