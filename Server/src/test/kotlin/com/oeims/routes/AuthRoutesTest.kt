package com.oeims.routes

import com.oeims.dto.AuthResponse
import com.oeims.dto.LoginRequest
import com.oeims.dto.RegisterRequest
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * HTTP-layer tests for POST /auth/register and POST /auth/login.
 *
 * The full Ktor application (including ContentNegotiation, StatusPages, JWT auth,
 * and the real SQLite-backed repositories) runs in-memory via [testApplication].
 * No mocks — every request travels through the actual route → service → repository stack.
 */
class AuthRoutesTest : BaseRouteTest() {

    // ── POST /auth/register ───────────────────────────────────────────────────

    @Test
    fun `register returns 201 and correct AuthResponse for a valid student`() = routeTest {
        val client = jsonClient()

        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("student@isel.pt", "password123", "STUDENT"))
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val body = response.body<AuthResponse>()
        assertEquals("student@isel.pt", body.email)
        assertEquals("STUDENT", body.role)
        assertNotNull(body.userId)
        assertTrue(body.token.isNotBlank())
    }

    @Test
    fun `register returns 201 and correct role for a valid professor`() = routeTest {
        val client = jsonClient()

        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("prof@isel.pt", "password123", "PROFESSOR"))
        }

        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals("PROFESSOR", response.body<AuthResponse>().role)
    }

    @Test
    fun `register accepts role in lowercase and normalises it`() = routeTest {
        val client = jsonClient()

        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("student@isel.pt", "password123", "student"))
        }

        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals("STUDENT", response.body<AuthResponse>().role)
    }

    @Test
    fun `register returns 400 for an invalid email format`() = routeTest {
        val client = jsonClient()

        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("not-an-email", "password123", "STUDENT"))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `register returns 400 for a password shorter than 8 characters`() = routeTest {
        val client = jsonClient()

        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("student@isel.pt", "short", "STUDENT"))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `register returns 400 for an unknown role`() = routeTest {
        val client = jsonClient()

        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("student@isel.pt", "password123", "ADMIN"))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `register returns 409 when the email is already registered`() = routeTest {
        val client = jsonClient()
        client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("student@isel.pt", "password123", "STUDENT"))
        }

        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("student@isel.pt", "different1", "STUDENT"))
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun `register token contains correct userId and role claims`() = routeTest {
        val client = jsonClient()

        val body = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("prof@isel.pt", "password123", "PROFESSOR"))
        }.body<AuthResponse>()

        // Decode the JWT without verifying the signature and check claim values.
        // The token is a three-part Base64 string; the middle part is the payload.
        val payloadJson = String(
            java.util.Base64.getUrlDecoder().decode(body.token.split(".")[1].padEnd(
                body.token.split(".")[1].length + (4 - body.token.split(".")[1].length % 4) % 4, '='
            ))
        )
        assertTrue(payloadJson.contains(body.userId))
        assertTrue(payloadJson.contains("PROFESSOR"))
    }

    // ── POST /auth/login ──────────────────────────────────────────────────────

    @Test
    fun `login returns 200 and a non-blank token for correct credentials`() = routeTest {
        val client = jsonClient()
        client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("student@isel.pt", "password123", "STUDENT"))
        }

        val response = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest("student@isel.pt", "password123"))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<AuthResponse>()
        assertEquals("student@isel.pt", body.email)
        assertTrue(body.token.isNotBlank())
    }

    @Test
    fun `login returns 401 for a wrong password`() = routeTest {
        val client = jsonClient()
        client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("student@isel.pt", "password123", "STUDENT"))
        }

        val response = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest("student@isel.pt", "wrongpass1"))
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `login returns 401 for an unknown email`() = routeTest {
        val client = jsonClient()

        val response = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest("nobody@isel.pt", "password123"))
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `login returns the same error message for a wrong email and a wrong password`() = routeTest {
        val client = jsonClient()
        client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("student@isel.pt", "password123", "STUDENT"))
        }

        val wrongEmail    = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest("nobody@isel.pt", "anything1"))
        }
        val wrongPassword = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest("student@isel.pt", "wrongpass1"))
        }

        // Both return 401 — error message intentionally identical to prevent user enumeration.
        assertEquals(HttpStatusCode.Unauthorized, wrongEmail.status)
        assertEquals(HttpStatusCode.Unauthorized, wrongPassword.status)
        assertEquals(wrongEmail.bodyAsText(), wrongPassword.bodyAsText())
    }
}
