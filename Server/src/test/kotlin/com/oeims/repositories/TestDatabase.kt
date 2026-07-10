package com.oeims.repositories

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID

/**
 * Tiny SQLite database helper for repository tests.
 *
 * Each test class gets its own shared in-memory database name, so tests do not
 * accidentally reuse rows from another class. The open JDBC connection keeps the
 * database alive until [close] is called.
 */
internal class TestDatabase(
    private vararg val tables: Table,
) {
    private val url = "jdbc:sqlite:file:test-${UUID.randomUUID()}?mode=memory&cache=shared"
    private lateinit var keepAlive: Connection

    fun connect() {
        keepAlive = DriverManager.getConnection(url)
        Database.connect(url = url, driver = "org.sqlite.JDBC")
        transaction { SchemaUtils.create(*tables) }
    }

    fun close() {
        transaction { SchemaUtils.drop(*tables.reversedArray()) }
        keepAlive.close()
    }
}
