// business-voice — STT/TTS bridges: DeepgramStreamBridge, ElevenLabsStreamBridge,
// OutboundAudioQueue, VoiceWebSocketHandler. Bridges the voice WebSocket plane
// to Temporal activities owned by business-cloud-orchestrator.
plugins {
    `java-library`
}

dependencies {
    implementation(project(":data:data-cloud-orchestrator"))
    implementation(project(":client:client-deepgram"))
    implementation(project(":client:client-elevenlabs"))
    // Parent provides: exception, logging, web, jackson, test, junit
}
