// business-pipeline — PDLC v2 business logic: PdlcV2Service, PipelineEventEmitter, PdlcRouter
plugins {
    `java-library`
}

dependencies {
    implementation(project(":data:data-pipeline"))
    implementation(project(":data:data-sparks"))
    implementation(project(":data:data-conversation"))
    implementation(project(":client:client-ai-arbiter"))
    // Parent provides: exception, logging, web, jackson, test, junit
}
