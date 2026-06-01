// client-elevenlabs — ElevenLabs streaming TTS WebSocket client
plugins {
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(libs.spring.boot.starter.websocket)
    // Parent (client/build.gradle.kts) provides: exception, secrets, client-base,
    // spring-boot-starter-web, jackson-databind, test

    testRuntimeOnly(libs.junit.platform.launcher)
}
