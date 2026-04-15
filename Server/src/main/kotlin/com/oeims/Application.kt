package com.oeims

import com.oeims.plugins.configureDatabase
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import kotlinx.serialization.json.Json

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun Application.module() {
    // Serialization
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }

    // Database — creates tables on first run
    configureDatabase()

    // TODO (next steps, in order):
    // configureSecurity()   — JWT auth plugin
    // configureRouting()    — REST routes (auth, exams, sessions)
    // configureWebSockets() — daemon socket + professor console socket
}
