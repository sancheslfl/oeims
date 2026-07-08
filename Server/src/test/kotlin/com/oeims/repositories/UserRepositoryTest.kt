package com.oeims.repositories

import com.oeims.models.UserRole
import com.oeims.models.Users
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserRepositoryTest {
    private lateinit var database: TestDatabase
    private lateinit var repository: UserRepository

    @BeforeEach
    fun setup() {
        database = TestDatabase(Users).also { it.connect() }
        repository = UserRepository()
    }

    @AfterEach
    fun teardown() {
        database.close()
    }

    @Test
    fun `create returns record with correct fields`(): Unit = runBlocking {
        val user = repository.create("student@alunos.isel.pt", UserRole.STUDENT, "hashedpassword")

        assertEquals("student@alunos.isel.pt", user.email)
        assertEquals(UserRole.STUDENT, user.role)
        assertEquals("hashedpassword", user.passwordHash)
        assertNotNull(user.id)
        assertNotNull(user.createdAt)
    }

    @Test
    fun `create assigns a unique id to each user`() = runBlocking {
        val user1 = repository.create("student1@alunos.isel.pt", UserRole.STUDENT, "hash1")
        val user2 = repository.create("student2@alunos.isel.pt", UserRole.STUDENT, "hash2")

        assertTrue(user1.id != user2.id)
    }

    @Test
    fun `findByEmail returns user when email exists`() = runBlocking {
        repository.create("professor@isel.pt", UserRole.PROFESSOR, "hash")

        val result = repository.findByEmail("professor@isel.pt")

        assertNotNull(result)
        assertEquals("professor@isel.pt", result.email)
        assertEquals(UserRole.PROFESSOR, result.role)
    }

    @Test
    fun `findByEmail returns null when email does not exist`() = runBlocking {
        val result = repository.findByEmail("nobody@isel.pt")

        assertNull(result)
    }

    @Test
    fun `findByEmail is case sensitive`() = runBlocking {
        repository.create("student@alunos.isel.pt", UserRole.STUDENT, "hash")

        val result = repository.findByEmail("STUDENT@alunos.isel.pt")

        assertNull(result)
    }

    @Test
    fun `findById returns user when id exists`() = runBlocking {
        val created = repository.create("student@alunos.isel.pt", UserRole.STUDENT, "hash")

        val result = repository.findById(created.id)

        assertNotNull(result)
        assertEquals(created.id, result.id)
        assertEquals(created.email, result.email)
    }

    @Test
    fun `findById returns null when id does not exist`() = runBlocking {
        val result = repository.findById(UUID.randomUUID())

        assertNull(result)
    }

    @Test
    fun `existsByEmail returns true when email exists`() = runBlocking {
        repository.create("student@alunos.isel.pt", UserRole.STUDENT, "hash")

        assertTrue(repository.existsByEmail("student@alunos.isel.pt"))
    }

    @Test
    fun `existsByEmail returns false when email does not exist`() = runBlocking {
        assertFalse(repository.existsByEmail("nobody@isel.pt"))
    }
}
