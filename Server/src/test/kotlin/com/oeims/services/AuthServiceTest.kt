package com.oeims.services

import com.auth0.jwt.JWT
import com.oeims.models.UserRole
import com.oeims.repositories.UserRecord
import com.oeims.repositories.interfaces.IUserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AuthServiceTest {

    // ── Fake ─────────────────────────────────────────────────────────────────

    private inner class FakeUserRepository : IUserRepository {
        val users = mutableListOf<UserRecord>()

        override fun findById(id: UUID): UserRecord? =
            users.find { it.id == id }

        override fun findByEmail(email: String): UserRecord? =
            users.find { it.email == email }

        override fun existsByEmail(email: String): Boolean =
            users.any { it.email == email }

        override fun create(email: String, role: UserRole, passwordHash: String): UserRecord {
            val record = UserRecord(UUID.randomUUID(), email, role, passwordHash, Instant.now())
            users.add(record)
            return record
        }
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private val jwtConfig = JwtConfig(
        secret       = "test-secret-key-long-enough",
        issuer       = "test-issuer",
        audience     = "test-audience",
        realm        = "test-realm",
        expirationMs = 3_600_000L
    )

    private lateinit var fakeRepo: FakeUserRepository
    private lateinit var service: AuthService

    @BeforeEach
    fun setup() {
        fakeRepo = FakeUserRepository()
        service  = AuthService(fakeRepo, jwtConfig)
    }

    // ── register ──────────────────────────────────────────────────────────────

    @Test
    fun `register returns response with correct email and role`() {
        val response = service.register("student@alunos.isel.pt", "password123", "STUDENT")

        assertEquals("student@alunos.isel.pt", response.email)
        assertEquals("STUDENT", response.role)
        assertNotNull(response.userId)
        assertNotNull(response.token)
    }

    @Test
    fun `register persists the user so a subsequent login works`() {
        service.register("prof@isel.pt", "pass", "PROFESSOR")

        val response = service.login("prof@isel.pt", "pass")

        assertEquals("prof@isel.pt", response.email)
    }

    @Test
    fun `register throws IllegalStateException when email already exists`() {
        service.register("student@alunos.isel.pt", "pass1", "STUDENT")

        assertThrows<IllegalStateException> {
            service.register("student@alunos.isel.pt", "pass2", "STUDENT")
        }
    }

    @Test
    fun `register throws IllegalArgumentException for an unknown role`() {
        assertThrows<IllegalArgumentException> {
            service.register("student@alunos.isel.pt", "pass", "ADMIN")
        }
    }

    @Test
    fun `register accepts role in any case`() {
        val response = service.register("student@alunos.isel.pt", "pass", "student")

        assertEquals("STUDENT", response.role)
    }

    @Test
    fun `register issues a JWT with correct userId and role claims`() {
        val response = service.register("prof@isel.pt", "pass", "PROFESSOR")

        val decoded = JWT.decode(response.token)
        assertEquals(response.userId, decoded.getClaim("userId").asString())
        assertEquals("PROFESSOR", decoded.getClaim("role").asString())
    }

    // ── login ─────────────────────────────────────────────────────────────────

    @Test
    fun `login returns response with correct email and role`() {
        service.register("prof@isel.pt", "securepass", "PROFESSOR")

        val response = service.login("prof@isel.pt", "securepass")

        assertEquals("prof@isel.pt", response.email)
        assertEquals("PROFESSOR", response.role)
        assertNotNull(response.token)
    }

    @Test
    fun `login issues a JWT with correct claims`() {
        service.register("student@alunos.isel.pt", "pass", "STUDENT")

        val response = service.login("student@alunos.isel.pt", "pass")
        val decoded  = JWT.decode(response.token)

        assertEquals(response.userId, decoded.getClaim("userId").asString())
        assertEquals("STUDENT", decoded.getClaim("role").asString())
    }

    @Test
    fun `login throws IllegalArgumentException when email does not exist`() {
        assertThrows<IllegalArgumentException> {
            service.login("nobody@isel.pt", "pass")
        }
    }

    @Test
    fun `login throws IllegalArgumentException when password is wrong`() {
        service.register("student@alunos.isel.pt", "correct", "STUDENT")

        assertThrows<IllegalArgumentException> {
            service.login("student@alunos.isel.pt", "wrong")
        }
    }

    @Test
    fun `login returns the same error message for wrong email and wrong password`() {
        service.register("student@alunos.isel.pt", "correct", "STUDENT")

        val wrongEmail    = assertThrows<IllegalArgumentException> { service.login("nobody@isel.pt", "anything") }
        val wrongPassword = assertThrows<IllegalArgumentException> { service.login("student@alunos.isel.pt", "wrong") }

        assertEquals(wrongEmail.message, wrongPassword.message)
    }
}
