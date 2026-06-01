// business-cloud-orchestrator — session orchestration domain:
// CloudAgentSessionService (Temporal facade), PersonaRegistry, PersonaRouter,
// CloudAgentSessionWorkflow + activities. Owns conversational persona prompts
// under src/main/resources/conversational-personas/.
plugins {
    `java-library`
}

dependencies {
    implementation(project(":data:data-cloud-orchestrator"))
    implementation(project(":data:data-pipeline"))
    implementation(project(":data:data-sparks"))
    implementation(project(":data:data-conversation"))
    implementation(project(":business:business-pipeline"))
    implementation(project(":business:business-conversation"))
    implementation(project(":business:business-sparks"))
    implementation(project(":business:business-voice"))
    implementation(libs.cidadel.client.anthropic.direct)

    // Temporal workflow + activity APIs
    // (No spring-boot-starter — application-api uses manual @Configuration beans for clarity; see TemporalWorkerConfig.)
    implementation(libs.temporal.sdk)
    testImplementation(libs.temporal.testing)
    // Parent provides: exception, logging, web, jackson, test, junit
}
