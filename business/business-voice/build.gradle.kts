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
    // Durable conversation persistence: voice turns are written into the new-model
    // conversation_sessions collection (ConversationSession.turns), bypassing the
    // deprecated ConversationService state machine. Lets a conversation be resumed
    // across reconnects and listed by a conversation picker.
    implementation(project(":data:data-conversation"))
    implementation(project(":client:client-deepgram"))
    implementation(project(":client:client-elevenlabs"))
    // business-pipeline supplies the ingress front door (IngressDispatchService,
    // IngressRequest, RunOrigin, …) and the outbound PipelineEventChannel SPI.
    // business→business is permitted by the layering rules.
    implementation(project(":business:business-pipeline"))
    // Conversation persona brain (fallback engine): turns a final transcript into a
    // spoken reply via a direct Anthropic call. Also brings LlmMessage/LlmResponse.
    implementation(libs.cidadel.client.anthropic.direct)
    // Conversation persona brain (primary engine): the arbiter ConverseTurn gRPC
    // client. business→client edge is permitted by the layering rules.
    implementation(project(":client:client-ai-arbiter"))
    // GitHubConfig supplies the resolved Tacticl PAT (github.app-token from Vault),
    // sent on the ConverseTurn request so the arbiter's create_repo skill can provision
    // a repo. Mirrors business-conversation's GitHubConfig injection.
    implementation(project(":client:client-github"))
    // UserRepoService: the user's known repos, sent as ConverseTurn grounding so the
    // analyst can offer them once requirements are understood.
    implementation(project(":business:business-profile"))
    // Parent provides: exception, logging, web, jackson, test, junit

    testRuntimeOnly(libs.junit.platform.launcher)
}
