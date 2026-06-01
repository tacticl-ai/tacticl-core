// client-deepgram — Deepgram streaming STT WebSocket client
plugins {
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    // Spring Boot WS starter is intentionally kept in case future bridge code
    // needs Spring's WebSocket abstractions. The client itself uses JDK's
    // `java.net.http.WebSocket` (no extra dep needed) to keep this module light.
    implementation(libs.spring.boot.starter.websocket)
    // Parent (client/build.gradle.kts) provides: exception, secrets, client-base,
    // spring-boot-starter-web, jackson-databind, test

    testRuntimeOnly(libs.junit.platform.launcher)
}
