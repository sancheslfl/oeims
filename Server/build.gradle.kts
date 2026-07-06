val ktorVersion = providers.gradleProperty("ktor_version")
val kotlinVersion = providers.gradleProperty("kotlin_version")
val exposedVersion = providers.gradleProperty("exposed_version")

plugins {
    kotlin("jvm") version "2.4.0"
    id("io.ktor.plugin") version "3.5.0"
    kotlin("plugin.serialization") version "2.4.0"
}

group = "com.oeims"
version = "0.0.1"

application {
    mainClass.set("com.oeims.ApplicationKt")
}

repositories {
    mavenCentral()
}

dependencies {
    // Ktor server
    implementation("io.ktor:ktor-server-core:${ktorVersion.get()}")
    implementation("io.ktor:ktor-server-config-yaml:${ktorVersion.get()}")
    implementation("io.ktor:ktor-server-netty:${ktorVersion.get()}")
    implementation("io.ktor:ktor-server-websockets:${ktorVersion.get()}")
    implementation("io.ktor:ktor-server-auth:${ktorVersion.get()}")
    implementation("io.ktor:ktor-server-auth-jwt:${ktorVersion.get()}")
    implementation("io.ktor:ktor-server-content-negotiation:${ktorVersion.get()}")
    implementation("io.ktor:ktor-serialization-kotlinx-json:${ktorVersion.get()}")
    implementation("io.ktor:ktor-server-call-logging:${ktorVersion.get()}")
    implementation("io.ktor:ktor-server-call-id:${ktorVersion.get()}")
    implementation("io.ktor:ktor-server-rate-limit:${ktorVersion.get()}")
    implementation("io.ktor:ktor-server-status-pages:${ktorVersion.get()}")
    implementation("io.ktor:ktor-server-cors:${ktorVersion.get()}")
    implementation("io.ktor:ktor-server-sse:${ktorVersion.get()}")               // SSE

    // Exposed ORM
    implementation("org.jetbrains.exposed:exposed-core:${exposedVersion.get()}")
    implementation("org.jetbrains.exposed:exposed-jdbc:${exposedVersion.get()}")
    implementation("org.jetbrains.exposed:exposed-java-time:${exposedVersion.get()}")

    // SQLite
    implementation("org.xerial:sqlite-jdbc:3.53.2.0")

    // Password hashing
    implementation("org.mindrot:jbcrypt:0.4")

    // JWT
    implementation("com.auth0:java-jwt:4.4.0")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.25")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:1.8.1")

    // Email service
    implementation("org.eclipse.angus:jakarta.mail:2.0.5")

    // Testing
    testImplementation("io.ktor:ktor-server-test-host:${ktorVersion.get()}")
    testImplementation("io.ktor:ktor-client-content-negotiation:${ktorVersion.get()}")
    testImplementation("io.ktor:ktor-client-websockets:${ktorVersion.get()}")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:${kotlinVersion.get()}")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<JavaExec>("run") {
    jvmArgs(
        "--enable-native-access=ALL-UNNAMED",
    )
}

tasks.test {
    useJUnitPlatform()
}