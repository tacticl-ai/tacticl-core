plugins {
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    // Internal modules
    implementation(project(":client:client-brave-search"))
    implementation(project(":client:client-jina"))
    implementation(project(":client:client-google"))
    implementation(project(":client:client-github"))
    implementation(project(":client:client-whisper"))
    implementation(project(":data:data-sparks"))
    implementation(project(":data:data-pipeline"))
    implementation(project(":business:business-sparks"))
    implementation(project(":business:business-pipeline"))

    // Cidadel shared infrastructure (module-specific, beyond parent)
    implementation(libs.cidadel.service.framework.base)
    implementation(libs.cidadel.framework.secrets)
    implementation(libs.cidadel.framework.token.issuance)
    implementation(libs.cidadel.client.base)
    implementation(libs.cidadel.framework.llm.router)

    // Cidadel LLM clients
    implementation(libs.cidadel.client.anthropic.direct)
    implementation(libs.cidadel.client.openai.direct)
    implementation(libs.cidadel.client.grok.direct)

}
