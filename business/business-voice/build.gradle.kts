// business-voice — voice command-center turn orchestration. STT/TTS bridges
// (DeepgramSttBridge, ElevenLabsTtsBridge), per-session turn orchestration
// (VoiceSessionService), and the outbound PDLC narration channel
// (VoiceRunUpdateChannel). The WebSocket transport + token endpoint live in
// service-voice; this module owns the transport-neutral orchestration only.
plugins {
    `java-library`
}

dependencies {
    implementation(project(":data:data-cloud-orchestrator"))
    implementation(project(":data:data-pipeline"))
    implementation(project(":client:client-deepgram"))
    implementation(project(":client:client-elevenlabs"))
    // business-pipeline supplies the ingress front door (IngressDispatchService,
    // IngressRequest, RunOrigin, …) and the outbound PipelineEventChannel SPI.
    // business→business is permitted by the layering rules.
    implementation(project(":business:business-pipeline"))
    // Parent provides: exception, logging, web, jackson, test, junit

    testRuntimeOnly(libs.junit.platform.launcher)
}
