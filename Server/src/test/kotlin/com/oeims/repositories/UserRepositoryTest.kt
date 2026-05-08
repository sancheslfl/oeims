package com.oeims.repositories

import com.oeims.models.UserRole
import com.oeims.models.Users
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.sql.DriverManager
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class UserRepositoryTest {

    private lateinit var repository: UserRepository
    private var keepAlive: java.sql.Connection? = null

    @BeforeEach
    fun setup() {
        // Pin the named in-memory database so it survives Exposed closing idle pool connections.
        keepAlive = DriverManager.getConnection("jdbc:sqlite:file:testdb?mode=memory&cache=shared")
        Database.connect(
            url = "jdbc:sqlite:file:testdb?mode=memory&cache=shared",
            driver = "org.sqlite.JDBC"
        )
        transaction { SchemaUtils.create(Users) }
        repository = UserRepository()
    }

    @AfterEach
    fun teardown() {
        transaction { SchemaUtils.drop(Users) }
        keepAlive?.close()
        keepAlive = null
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    fun `create returns record with correct fields`() {
        val user = repository.create("student@alunos.isel.pt", UserRole.STUDENT, "hashedpassword")

        assertEquals("student@alunos.isel.pt", user.email)
        assertEquals(UserRole.STUDENT, user.role)
        assertEquals("hashedpassword", user.passwordHash)
        assertNotNull(user.id)
        assertNotNull(user.createdAt)
    }

    @Test
    fun `create assigns a unique id to each user`() {
        val user1 = repository.create("student1@alunos.isel.pt", UserRole.STUDENT, "hash1")
        val user2 = repository.create("student2@alunos.isel.pt", UserRole.STUDENT, "hash2")

        assertTrue(user1.id != user2.id)
    }

    // ── findByEmail ───────────────────────────────────────────────────────────

    @Test
    fun `findByEmail returns user when email exists`() {
        repository.create("professor@isel.pt", UserRole.PROFESSOR, "hash")

        val result = repository.findByEmail("professor@isel.pt")

        assertNotNull(result)
        assertEquals("professor@isel.pt", result.email)
        assertEquals(UserRole.PROFESSOR, result.role)
    }

    @Test
    fun `findByEmail returns null when email does not exist`() {
        val result = repository.findByEmail("nobody@isel.pt")

        assertNull(result)
    }

    @Test
    fun `findByEmail is case sensitive`() {
        repository.create("student@alunos.isel.pt", UserRole.STUDENT, "hash")

        val result = repository.findByEmail("STUDENT@alunos.isel.pt")

        assertNull(result)
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Test
    fun `findById returns user when id exists`() {
        val created = repository.create("student@alunos.isel.pt", UserRole.STUDENT, "hash")

        val result = repository.findById(created.id)

        assertNotNull(result)
        assertEquals(created.id, result.id)
        assertEquals(created.email, result.email)
    }

    @Test
    fun `findById returns null when id does not exist`() {
        val result = repository.findById(UUID.randomUUID())

        assertNull(result)
    }

    // ── existsByEmail ─────────────────────────────────────────────────────────

    @Test
    fun `existsByEmail returns true when email exists`() {
        repository.create("student@alunos.isel.pt", UserRole.STUDENT, "hash")

        assertTrue(repository.existsByEmail("student@alunos.isel.pt"))
    }

    @Test
    fun `existsByEmail returns false when email does not exist`() {
        assertFalse(repository.existsByEmail("nobody@isel.pt"))
    }
}
