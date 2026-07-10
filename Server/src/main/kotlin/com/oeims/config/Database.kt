package com.oeims.config

import com.oeims.models.*
import io.ktor.server.application.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

fun Application.configureDatabase() {
    val dbPath = Environment.databasePath

    Database.connect(
        url = "jdbc:sqlite:$dbPath",
        driver = "org.sqlite.JDBC"
    )

    transaction {
        SchemaUtils.create(
            Users,
            Exams,
            Sessions,
            SessionSupervisors,
            SessionJoins,
            Participants,
            Events
        )
    }

    log.info("Database initialised at $dbPath")
}
