package com.oeims.services

import com.auth0.jwt.JWT
import com.oeims.exceptions.ConflictException
import com.oeims.exceptions.UnauthorizedException
import com.oeims.exceptions.ValidationException
import com.oeims.models.Email
import com.oeims.models.Password
import com.oeims.models.UserRole
import com.oeims.repositories.UserRecord
import com.oeims.repositories.interfaces.IUserRepository
import kotlinx.coroutines.runBlocking
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

        override suspend fun findById(id: UUID): UserRecord? =
            users.find { it.id == id }

        override suspend fun findByEmail(email: String): UserRecord? =
            users.find { it.email == email }

        override suspend fun existsByEmail(email: String): Boolean =
            users.any { it.email == email }

        override suspend fun create(email: String, role: UserRole, passwordHash: String): UserRecord {
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
    fun `register returns response with correct email and role`() = runBlocking {
        val response = service.register(Email("student@alunos.isel.pt"), Password("password123"), "STUDENT")

        assertEquals("student@alunos.isel.pt", response.email)
        assertEquals("STUDENT", response.role)
        assertNotNull(response.userId)
        assertNotNull(response.token)
    }

    @Test
    fun `register persists the user so a subsequent login works`() = runBlocking {
        service.register(Email("prof@isel.pt"), Password("password123"), "PROFESSOR")

        val response = service.login(Email("prof@isel.pt"), Password("password123"))

        assertEquals("prof@isel.pt", response.email)
    }

    @Test
    fun `register throws ConflictException when email already exists`() = runBlocking<Unit> {
        service.register(Email("student@alunos.isel.pt"), Password("password1"), "STUDENT")

        assertThrows<ConflictException> {
            runBlocking { service.register(Email("student@alunos.isel.pt"), Password("password2"), "STUDENT") }
        }
    }

    @Test
    fun `register throws ValidationException for an unknown role`() {
        assertThrows<ValidationException> {
            runBlocking { service.register(Email("student@alunos.isel.pt"), Password("password1"), "ADMIN") }
        }
    }

    @Test
    fun `register throws ValidationException for invalid email format`() {
        assertThrows<ValidationException> {
            runBlocking { service.register(Email("not-an-email"), Password("password123"), "STUDENT") }
        }
    }

    @Test
    fun `register throws ValidationException for password shorter than 8 characters`() {
        assertThrows<ValidationException> {
            runBlocking { service.register(Email("student@alunos.isel.pt"), Password("short"), "STUDENT") }
        }
    }

    @Test
    fun `register accepts role in any case`() = runBlocking {
        val response = service.register(Email("student@alunos.isel.pt"), Password("password1"), "student")

        assertEquals("STUDENT", response.role)
    }

    @Test
    fun `register issues a JWT with correct userId and role claims`() = runBlocking {
        val response = service.register(Email("prof@isel.pt"), Password("password123"), "PROFESSOR")

        val decoded = JWT.decode(response.token)
        assertEquals(response.userId, decoded.getClaim("userId").asString())
        assertEquals("PROFESSOR", decoded.getClaim("role").asString())
    }

    // ── login ─────────────────────────────────────────────────────────────────

    @Test
    fun `login returns response with correct email and role`() = runBlocking {
        service.register(Email("prof@isel.pt"), Password("securepass1"), "PROFESSOR")

        val response = service.login(Email("prof@isel.pt"), Password("securepass1"))

        assertEquals("prof@isel.pt", response.email)
        assertEquals("PROFESSOR", response.role)
        assertNotNull(response.token)
    }

    @Test
    fun `login issues a JWT with correct claims`() = runBlocking {
        service.register(Email("student@alunos.isel.pt"), Password("password1"), "STUDENT")

        val response = service.login(Email("student@alunos.isel.pt"), Password("password1"))
        val decoded  = JWT.decode(response.token)

        assertEquals(response.userId, decoded.getClaim("userId").asString())
        assertEquals("STUDENT", decoded.getClaim("role").asString())
    }

    @Test
    fun `login throws UnauthorizedException when email does not exist`() {
        assertThrows<UnauthorizedException> {
            runBlocking { service.login(Email("nobody@isel.pt"), Password("password1")) }
        }
    }

    @Test
    fun `login throws UnauthorizedException when password is wrong`() = runBlocking<Unit> {
        service.register(Email("student@alunos.isel.pt"), Password("correct12"), "STUDENT")

        assertThrows<UnauthorizedException> {
            runBlocking { service.login(Email("student@alunos.isel.pt"), Password("wrongpass1")) }
        }
    }

    @Test
    fun `login returns the same error message for wrong email and wrong password`() = runBlocking<Unit> {
        service.register(Email("student@alunos.isel.pt"), Password("correct12"), "STUDENT")

        val wrongEmail    = assertThrows<UnauthorizedException> { runBlocking { service.login(Email("nobody@isel.pt"), Password("anything1")) } }
        val wrongPassword = assertThrows<UnauthorizedException> { runBlocking { service.login(Email("student@alunos.isel.pt"), Password("wrongpass1")) } }

        assertEquals(wrongEmail.message, wrongPassword.message)
    }
}
