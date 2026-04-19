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
    secret      = environment.config.property("jwt.secret").getString(),
    issuer      = environment.config.property("jwt.issuer").getString(),
    audience    = environment.config.property("jwt.audience").getString(),
    realm       = environment.config.property("jwt.realm").getString(),
    expirationMs = environment.config.property("jwt.expiration-ms").getString().toLong()
)
