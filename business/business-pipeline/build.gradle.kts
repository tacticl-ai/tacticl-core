// business-pipeline — PDLC v2 business logic: PdlcV2Service, PipelineEventEmitter, PdlcRouter
plugins {
    `java-library`
}

dependencies {
    implementation(project(":data:data-pipeline"))
    implementation(project(":data:data-sparks"))
    implementation(project(":data:data-conversation"))
    implementation(project(":client:client-ai-arbiter"))
    // Ingress front door reuses Spark lifecycle + classification. business-conversation is NOT a
    // dependency on purpose: it already depends on business-pipeline, so the CONVERSATION_TURN path
    // is wired via the ConversationTurnHandler SPI (impl provided by business-conversation) to avoid
    // a module cycle.
    implementation(project(":business:business-sparks"))
    // Parent provides: exception, logging, web, jackson, test, junit
}
