package com.oeims.config

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureOpenApi() {
    routing {
        get("/docs") {
            call.respondText(swaggerUiPage(), ContentType.Text.Html)
        }
        get("/docs/openapi.json") {
            call.respondText(openApiSpec(), ContentType.Application.Json)
        }
    }
}

// Swagger UI HTML
private fun swaggerUiPage() = """
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8"/>
  <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
  <title>OEIMS — API Reference</title>
  <link rel="stylesheet" href="https://unpkg.com/swagger-ui-dist@5/swagger-ui.css"/>
  <style>
    /* Always light — ignore OS dark mode */
    :root { color-scheme: light only; }
    html, body { background: #ffffff; color: #212121; }
    *, *::before, *::after { box-sizing: border-box; }
    body { margin: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; }

    /* ── Custom header ──────────────────────────────────────────────────── */
    #app-header {
      background: #0054A6;
      color: white;
      padding: 0 2rem;
      height: 56px;
      display: flex;
      align-items: center;
      gap: 1rem;
      position: sticky;
      top: 0;
      z-index: 1000;
      box-shadow: 0 2px 8px rgba(0,0,0,0.25);
    }
    #app-header .logo-text { font-size: 1.05rem; font-weight: 700; letter-spacing: 0.02em; }
    #app-header .subtitle  { font-size: 0.78rem; opacity: 0.7; margin-left: 0.25rem; }
    #app-header .spacer    { flex: 1; }
    #app-header .badge {
      background: rgba(255,255,255,0.18);
      border: 1px solid rgba(255,255,255,0.35);
      border-radius: 20px;
      padding: 0.2rem 0.65rem;
      font-size: 0.7rem;
      font-weight: 600;
      letter-spacing: 0.06em;
      text-transform: uppercase;
    }

    /* ── Hide Swagger default topbar ────────────────────────────────────── */
    .swagger-ui .topbar { display: none !important; }

    /* ── Brand colour overrides (authorize / execute buttons, titles) ───── */
    .swagger-ui .info .title { color: #0054A6; }
    .swagger-ui .info a { color: #0054A6; }
    .swagger-ui .model-title { color: #0054A6; }
    .swagger-ui .btn.authorize {
      background: #0054A6 !important;
      border-color: #0054A6 !important;
      color: #fff !important;
    }
    .swagger-ui .btn.authorize svg { fill: #fff !important; }
    .swagger-ui .btn.authorize:hover { background: #003d7a !important; border-color: #003d7a !important; }
    .swagger-ui .btn.execute { background: #0054A6; border-color: #0054A6; }
    .swagger-ui .btn.execute:hover { background: #003d7a; border-color: #003d7a; }
    .swagger-ui .try-out__btn { color: #0054A6; border-color: #0054A6; }
  </style>
</head>
<body>

  <div id="app-header">
    <span class="logo-text">OEIMS</span>
    <span class="subtitle">Online Exam Integrity Monitoring System</span>
    <div class="spacer"></div>
    <span class="badge">v1.0</span>
  </div>

  <div id="swagger-ui"></div>

  <script src="https://unpkg.com/swagger-ui-dist@5/swagger-ui-bundle.js"></script>
  <script src="https://unpkg.com/swagger-ui-dist@5/swagger-ui-standalone-preset.js"></script>
  <script>
    window.onload = () => {
      SwaggerUIBundle({
        url: "/docs/openapi.json",
        dom_id: "#swagger-ui",
        presets: [
          SwaggerUIBundle.presets.apis,
          SwaggerUIStandalonePreset
        ],
        layout: "StandaloneLayout",
        deepLinking: true,
        displayRequestDuration: true,
        persistAuthorization: true,
        filter: true,
        tagsSorter: "alpha",
        defaultModelsExpandDepth: 1,
        defaultModelExpandDepth: 2,
        tryItOutEnabled: false
      });
    };
  </script>
</body>
</html>
""".trimIndent()

// ─────────────────────────────────────────────────────────────────────────────
// OpenAPI 3.0.3 Specification
// ─────────────────────────────────────────────────────────────────────────────

private fun openApiSpec(): String {
    val ref = "\$ref"
    return """
{
  "openapi": "3.0.3",
  "info": {
    "title": "OEIMS API",
    "description": "REST API for the **Online Exam Integrity Monitoring System**.\n\nMost endpoints require a JWT Bearer token. Obtain one from `/auth/login` and click **Authorize** above.\n\n| Role | Access |\n|---|---|\n| `PROFESSOR` | Manage exams, sessions, view participants and events |\n| `STUDENT` | Join sessions and send heartbeats |",
    "version": "1.0.0",
    "contact": {
      "name": "Dinis Contreiras & Miguel Sanches",
      "email": "A50496@alunos.isel.pt"
    }
  },
  "servers": [
    { "url": "http://localhost:8080", "description": "Local development" }
  ],
  "tags": [
    { "name": "Authentication", "description": "Register a new account or log in to obtain a JWT token." },
    { "name": "Exams",          "description": "Create and query exams. **Professor** role required." },
    { "name": "Sessions",       "description": "Manage the exam session lifecycle — create, start, end, join." },
    { "name": "Participants",   "description": "Participant heartbeat. **Student** role required." }
  ],
  "components": {
    "securitySchemes": {
      "bearerAuth": {
        "type": "http",
        "scheme": "bearer",
        "bearerFormat": "JWT",
        "description": "Paste the token returned by `/auth/login`."
      }
    },
    "schemas": {
      "RegisterRequest": {
        "type": "object",
        "required": ["email", "password", "role"],
        "properties": {
          "email":    { "type": "string", "format": "email",  "example": "prof@isel.pt" },
          "password": { "type": "string", "minLength": 4,     "example": "secret" },
          "role":     { "type": "string", "enum": ["PROFESSOR", "STUDENT"], "example": "PROFESSOR" }
        }
      },
      "LoginRequest": {
        "type": "object",
        "required": ["email", "password"],
        "properties": {
          "email":    { "type": "string", "format": "email", "example": "prof@isel.pt" },
          "password": { "type": "string",                    "example": "secret" }
        }
      },
      "AuthResponse": {
        "type": "object",
        "properties": {
          "token":  { "type": "string", "description": "JWT Bearer token to use in Authorization header." },
          "userId": { "type": "string", "format": "uuid" },
          "email":  { "type": "string", "format": "email" },
          "role":   { "type": "string", "enum": ["PROFESSOR", "STUDENT"] }
        }
      },
      "CreateExamRequest": {
        "type": "object",
        "required": ["title", "durationMins"],
        "properties": {
          "title":        { "type": "string",  "example": "Computer Networks" },
          "description":  { "type": "string",  "nullable": true, "example": "Midterm exam" },
          "durationMins": { "type": "integer", "minimum": 1, "example": 90 }
        }
      },
      "ExamResponse": {
        "type": "object",
        "properties": {
          "id":           { "type": "string",  "format": "uuid" },
          "createdBy":    { "type": "string",  "format": "uuid", "description": "Professor user ID" },
          "title":        { "type": "string" },
          "description":  { "type": "string",  "nullable": true },
          "durationMins": { "type": "integer" },
          "createdAt":    { "type": "string",  "format": "date-time" }
        }
      },
      "CreateSessionRequest": {
        "type": "object",
        "required": ["examId"],
        "properties": {
          "examId": { "type": "string", "format": "uuid" }
        }
      },
      "SessionResponse": {
        "type": "object",
        "properties": {
          "id":           { "type": "string", "format": "uuid" },
          "examId":       { "type": "string", "format": "uuid" },
          "supervisorId": { "type": "string", "format": "uuid" },
          "code":         { "type": "string", "minLength": 6, "maxLength": 6, "example": "AB12CD", "description": "6-character join code shared with students." },
          "status":       { "type": "string", "enum": ["PENDING", "ACTIVE", "ENDED"] },
          "startedAt":    { "type": "string", "format": "date-time", "nullable": true },
          "endedAt":      { "type": "string", "format": "date-time", "nullable": true }
        }
      },
      "JoinSessionRequest": {
        "type": "object",
        "required": ["code"],
        "properties": {
          "code": { "type": "string", "minLength": 6, "maxLength": 6, "example": "AB12CD" }
        }
      },
      "JoinSessionResponse": {
        "type": "object",
        "properties": {
          "participantId": { "type": "string",  "format": "uuid" },
          "sessionId":     { "type": "string",  "format": "uuid" },
          "examTitle":     { "type": "string" },
          "durationMins":  { "type": "integer" }
        }
      },
      "ParticipantResponse": {
        "type": "object",
        "properties": {
          "id":               { "type": "string", "format": "uuid" },
          "sessionId":        { "type": "string", "format": "uuid" },
          "userId":           { "type": "string", "format": "uuid" },
          "email":            { "type": "string", "format": "email" },
          "connectionStatus": { "type": "string", "enum": ["CONNECTED", "DISCONNECTED", "TIMED_OUT"] },
          "lastHeartbeat":    { "type": "string", "format": "date-time", "nullable": true },
          "joinedAt":         { "type": "string", "format": "date-time" }
        }
      },
      "EventResponse": {
        "type": "object",
        "properties": {
          "id":            { "type": "string", "format": "uuid" },
          "participantId": { "type": "string", "format": "uuid" },
          "monitorName":   { "type": "string", "example": "ClipboardMonitor" },
          "message":       { "type": "string", "example": "Clipboard access blocked" },
          "severity":      { "type": "string", "enum": ["INFO", "WARNING", "CRITICAL"] },
          "occurredAt":    { "type": "string", "format": "date-time" }
        }
      },
      "ErrorResponse": {
        "type": "object",
        "properties": {
          "error": { "type": "string", "example": "Resource not found" }
        }
      }
    },
    "responses": {
      "BadRequest":          { "description": "Invalid request body or parameters.", "content": { "application/json": { "schema": { "$ref": "#/components/schemas/ErrorResponse" } } } },
      "Unauthorized":        { "description": "Missing or invalid JWT token.",        "content": { "application/json": { "schema": { "$ref": "#/components/schemas/ErrorResponse" } } } },
      "Forbidden":           { "description": "Token valid but role not permitted.",  "content": { "application/json": { "schema": { "$ref": "#/components/schemas/ErrorResponse" } } } },
      "NotFound":            { "description": "Resource not found.",                  "content": { "application/json": { "schema": { "$ref": "#/components/schemas/ErrorResponse" } } } },
      "Conflict":            { "description": "Resource already exists or invalid state transition.", "content": { "application/json": { "schema": { "$ref": "#/components/schemas/ErrorResponse" } } } },
      "InternalServerError": { "description": "Unexpected server error.",             "content": { "application/json": { "schema": { "$ref": "#/components/schemas/ErrorResponse" } } } }
    }
  },
  "paths": {
    "/auth/register": {
      "post": {
        "tags": ["Authentication"],
        "summary": "Register",
        "description": "Create a new user account. Returns a JWT token immediately — no need to log in separately.",
        "operationId": "register",
        "requestBody": {
          "required": true,
          "content": { "application/json": { "schema": { "$ref": "#/components/schemas/RegisterRequest" } } }
        },
        "responses": {
          "201": { "description": "Account created.",  "content": { "application/json": { "schema": { "$ref": "#/components/schemas/AuthResponse" } } } },
          "400": { "$ref": "#/components/responses/BadRequest" },
          "409": { "$ref": "#/components/responses/Conflict" },
          "500": { "$ref": "#/components/responses/InternalServerError" }
        }
      }
    },
    "/auth/login": {
      "post": {
        "tags": ["Authentication"],
        "summary": "Login",
        "description": "Authenticate with email and password. Returns a JWT token.",
        "operationId": "login",
        "requestBody": {
          "required": true,
          "content": { "application/json": { "schema": { "$ref": "#/components/schemas/LoginRequest" } } }
        },
        "responses": {
          "200": { "description": "Login successful.", "content": { "application/json": { "schema": { "$ref": "#/components/schemas/AuthResponse" } } } },
          "400": { "$ref": "#/components/responses/BadRequest" },
          "500": { "$ref": "#/components/responses/InternalServerError" }
        }
      }
    },
    "/exams": {
      "post": {
        "tags": ["Exams"],
        "summary": "Create exam",
        "description": "Create a new exam. The authenticated professor is recorded as the creator.",
        "operationId": "createExam",
        "security": [{ "bearerAuth": [] }],
        "requestBody": {
          "required": true,
          "content": { "application/json": { "schema": { "$ref": "#/components/schemas/CreateExamRequest" } } }
        },
        "responses": {
          "201": { "description": "Exam created.",       "content": { "application/json": { "schema": { "$ref": "#/components/schemas/ExamResponse" } } } },
          "400": { "$ref": "#/components/responses/BadRequest" },
          "401": { "$ref": "#/components/responses/Unauthorized" },
          "403": { "$ref": "#/components/responses/Forbidden" },
          "500": { "$ref": "#/components/responses/InternalServerError" }
        }
      },
      "get": {
        "tags": ["Exams"],
        "summary": "List exams",
        "description": "Return all exams, optionally filtered by title (case-insensitive substring match).",
        "operationId": "listExams",
        "security": [{ "bearerAuth": [] }],
        "parameters": [
          {
            "name": "title",
            "in": "query",
            "required": false,
            "description": "Filter exams whose title contains this string.",
            "schema": { "type": "string", "example": "Networks" }
          }
        ],
        "responses": {
          "200": { "description": "List of exams.", "content": { "application/json": { "schema": { "type": "array", "items": { "$ref": "#/components/schemas/ExamResponse" } } } } },
          "401": { "$ref": "#/components/responses/Unauthorized" },
          "403": { "$ref": "#/components/responses/Forbidden" },
          "500": { "$ref": "#/components/responses/InternalServerError" }
        }
      }
    },
    "/exams/{id}": {
      "get": {
        "tags": ["Exams"],
        "summary": "Get exam",
        "description": "Retrieve a single exam by its ID.",
        "operationId": "getExam",
        "security": [{ "bearerAuth": [] }],
        "parameters": [
          { "name": "id", "in": "path", "required": true, "schema": { "type": "string", "format": "uuid" } }
        ],
        "responses": {
          "200": { "description": "Exam found.",  "content": { "application/json": { "schema": { "$ref": "#/components/schemas/ExamResponse" } } } },
          "400": { "$ref": "#/components/responses/BadRequest" },
          "401": { "$ref": "#/components/responses/Unauthorized" },
          "403": { "$ref": "#/components/responses/Forbidden" },
          "404": { "$ref": "#/components/responses/NotFound" },
          "500": { "$ref": "#/components/responses/InternalServerError" }
        }
      }
    },
    "/sessions": {
      "post": {
        "tags": ["Sessions"],
        "summary": "Create session",
        "description": "Create a new exam session. The server generates a unique 6-character join code. The session starts in `PENDING` status.",
        "operationId": "createSession",
        "security": [{ "bearerAuth": [] }],
        "requestBody": {
          "required": true,
          "content": { "application/json": { "schema": { "$ref": "#/components/schemas/CreateSessionRequest" } } }
        },
        "responses": {
          "201": { "description": "Session created.", "content": { "application/json": { "schema": { "$ref": "#/components/schemas/SessionResponse" } } } },
          "400": { "$ref": "#/components/responses/BadRequest" },
          "401": { "$ref": "#/components/responses/Unauthorized" },
          "403": { "$ref": "#/components/responses/Forbidden" },
          "404": { "$ref": "#/components/responses/NotFound" },
          "500": { "$ref": "#/components/responses/InternalServerError" }
        }
      }
    },
    "/sessions/join": {
      "post": {
        "tags": ["Sessions"],
        "summary": "Join session",
        "description": "Student joins an `ACTIVE` session using the 6-character code. If the student has already joined, the existing participant record is returned (idempotent).",
        "operationId": "joinSession",
        "security": [{ "bearerAuth": [] }],
        "requestBody": {
          "required": true,
          "content": { "application/json": { "schema": { "$ref": "#/components/schemas/JoinSessionRequest" } } }
        },
        "responses": {
          "200": { "description": "Joined successfully.", "content": { "application/json": { "schema": { "$ref": "#/components/schemas/JoinSessionResponse" } } } },
          "400": { "$ref": "#/components/responses/BadRequest" },
          "401": { "$ref": "#/components/responses/Unauthorized" },
          "403": { "$ref": "#/components/responses/Forbidden" },
          "404": { "$ref": "#/components/responses/NotFound" },
          "409": { "$ref": "#/components/responses/Conflict" },
          "500": { "$ref": "#/components/responses/InternalServerError" }
        }
      }
    },
    "/sessions/{id}": {
      "get": {
        "tags": ["Sessions"],
        "summary": "Get session",
        "description": "Retrieve session details by ID.",
        "operationId": "getSession",
        "security": [{ "bearerAuth": [] }],
        "parameters": [
          { "name": "id", "in": "path", "required": true, "schema": { "type": "string", "format": "uuid" } }
        ],
        "responses": {
          "200": { "description": "Session found.", "content": { "application/json": { "schema": { "$ref": "#/components/schemas/SessionResponse" } } } },
          "400": { "$ref": "#/components/responses/BadRequest" },
          "401": { "$ref": "#/components/responses/Unauthorized" },
          "403": { "$ref": "#/components/responses/Forbidden" },
          "404": { "$ref": "#/components/responses/NotFound" },
          "500": { "$ref": "#/components/responses/InternalServerError" }
        }
      }
    },
    "/sessions/{id}/start": {
      "post": {
        "tags": ["Sessions"],
        "summary": "Start session",
        "description": "Transition a `PENDING` session to `ACTIVE`. Only the supervising professor can start the session.",
        "operationId": "startSession",
        "security": [{ "bearerAuth": [] }],
        "parameters": [
          { "name": "id", "in": "path", "required": true, "schema": { "type": "string", "format": "uuid" } }
        ],
        "responses": {
          "200": { "description": "Session started.", "content": { "application/json": { "schema": { "$ref": "#/components/schemas/SessionResponse" } } } },
          "400": { "$ref": "#/components/responses/BadRequest" },
          "401": { "$ref": "#/components/responses/Unauthorized" },
          "403": { "$ref": "#/components/responses/Forbidden" },
          "404": { "$ref": "#/components/responses/NotFound" },
          "409": { "$ref": "#/components/responses/Conflict" },
          "500": { "$ref": "#/components/responses/InternalServerError" }
        }
      }
    },
    "/sessions/{id}/end": {
      "post": {
        "tags": ["Sessions"],
        "summary": "End session",
        "description": "Transition an `ACTIVE` session to `ENDED`. Only the supervising professor can end the session.",
        "operationId": "endSession",
        "security": [{ "bearerAuth": [] }],
        "parameters": [
          { "name": "id", "in": "path", "required": true, "schema": { "type": "string", "format": "uuid" } }
        ],
        "responses": {
          "200": { "description": "Session ended.", "content": { "application/json": { "schema": { "$ref": "#/components/schemas/SessionResponse" } } } },
          "400": { "$ref": "#/components/responses/BadRequest" },
          "401": { "$ref": "#/components/responses/Unauthorized" },
          "403": { "$ref": "#/components/responses/Forbidden" },
          "404": { "$ref": "#/components/responses/NotFound" },
          "409": { "$ref": "#/components/responses/Conflict" },
          "500": { "$ref": "#/components/responses/InternalServerError" }
        }
      }
    },
    "/sessions/{id}/participants": {
      "get": {
        "tags": ["Sessions"],
        "summary": "List participants",
        "description": "Return all participants who have joined the session, with their current connection status and last heartbeat timestamp.",
        "operationId": "listParticipants",
        "security": [{ "bearerAuth": [] }],
        "parameters": [
          { "name": "id", "in": "path", "required": true, "schema": { "type": "string", "format": "uuid" } }
        ],
        "responses": {
          "200": { "description": "Participant list.", "content": { "application/json": { "schema": { "type": "array", "items": { "$ref": "#/components/schemas/ParticipantResponse" } } } } },
          "400": { "$ref": "#/components/responses/BadRequest" },
          "401": { "$ref": "#/components/responses/Unauthorized" },
          "403": { "$ref": "#/components/responses/Forbidden" },
          "404": { "$ref": "#/components/responses/NotFound" },
          "500": { "$ref": "#/components/responses/InternalServerError" }
        }
      }
    },
    "/sessions/{id}/events": {
      "get": {
        "tags": ["Sessions"],
        "summary": "List session events",
        "description": "Return all integrity events logged across all participants in the session, ordered by occurrence time.",
        "operationId": "listSessionEvents",
        "security": [{ "bearerAuth": [] }],
        "parameters": [
          { "name": "id", "in": "path", "required": true, "schema": { "type": "string", "format": "uuid" } }
        ],
        "responses": {
          "200": { "description": "Event list.", "content": { "application/json": { "schema": { "type": "array", "items": { "$ref": "#/components/schemas/EventResponse" } } } } },
          "400": { "$ref": "#/components/responses/BadRequest" },
          "401": { "$ref": "#/components/responses/Unauthorized" },
          "403": { "$ref": "#/components/responses/Forbidden" },
          "404": { "$ref": "#/components/responses/NotFound" },
          "500": { "$ref": "#/components/responses/InternalServerError" }
        }
      }
    },
    "/participants/{id}/heartbeat": {
      "post": {
        "tags": ["Participants"],
        "summary": "Heartbeat",
        "description": "Update the participant's last heartbeat timestamp. The daemon calls this endpoint periodically. If heartbeats stop, the participant is eventually marked as `TIMED_OUT`.",
        "operationId": "heartbeat",
        "security": [{ "bearerAuth": [] }],
        "parameters": [
          { "name": "id", "in": "path", "required": true, "description": "Participant ID returned by `/sessions/join`.", "schema": { "type": "string", "format": "uuid" } }
        ],
        "responses": {
          "204": { "description": "Heartbeat recorded." },
          "400": { "$ref": "#/components/responses/BadRequest" },
          "401": { "$ref": "#/components/responses/Unauthorized" },
          "403": { "$ref": "#/components/responses/Forbidden" },
          "404": { "$ref": "#/components/responses/NotFound" },
          "500": { "$ref": "#/components/responses/InternalServerError" }
        }
      }
    }
  }
}
    """.trimIndent()
}
