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
    // GitHubConfig supplies the resolved Tacticl PAT (Vault github.app-token) so EXPLICIT_TRIGGER
    // submits a usable clone/commit token to the arbiter (resolves the old TODO(vault)).
    implementation(project(":client:client-github"))
    // UserRepoService: auto-register the repo a build uses to the user's repo memory.
    implementation(project(":business:business-profile"))
    // Parent provides: exception, logging, web, jackson, test, junit
}
