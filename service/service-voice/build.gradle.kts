// service-voice — voice command-center WebSocket transport + token endpoint.
//
// Owns the browser-facing edge of the voice surface: the short-lived voice
// session token endpoint (POST /v1/voice/token), the WebSocket transport at
// /v1/voice (binary mic PCM up / binary TTS audio + JSON control down), and the
// WebSocket registration. The transport-neutral turn orchestration + STT/TTS
// bridges + PDLC narration channel live in business-voice; this module only
// adapts that brain onto a live socket.
//
// Everything is gated behind tacticl.voice.enabled=true — with the flag off
// (the default) no WS handler, controller, or config bean is created, so the
// module is inert and the app assembles voice-dormant.
plugins {
    `java-library`
}

dependencies {
    // The transport-neutral voice brain (VoiceSessionService, VoiceOutbound,
    // VoiceSession, VoiceFrames, VoiceState, VoiceProperties).
    implementation(project(":business:business-voice"))

    // Cidadel auth: PasetoTokenValidator (WS handshake token validation) +
    // @RequireAuth / @AuthUser (token endpoint) + BaseController.
    implementation(libs.cidadel.framework.authorization)
    implementation(libs.cidadel.framework.exception)
    implementation(libs.cidadel.service.framework.base)

    // Spring Boot web + websocket transport.
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.websocket)

    // Testing
    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
