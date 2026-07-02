package com.oeims.config

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

private const val OPENAPI_RESOURCE = "openapi.yaml"
private val YAML_CONTENT_TYPE = ContentType("application", "yaml")

fun Application.configureOpenApi() {
    // Load the spec once from the classpath so it fails fast on startup if missing.
    val spec = object {}.javaClass.classLoader.getResourceAsStream(OPENAPI_RESOURCE)
        ?.bufferedReader()?.use { it.readText() }
        ?: error("OpenAPI spec '$OPENAPI_RESOURCE' not found on the classpath")

    routing {
        get("/docs") {
            call.respondText(swaggerUiPage(), ContentType.Text.Html)
        }
        get("/docs/openapi.yaml") {
            call.respondText(spec, YAML_CONTENT_TYPE)
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
        url: "/docs/openapi.yaml",
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
