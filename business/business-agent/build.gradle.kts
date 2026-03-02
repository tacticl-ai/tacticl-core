plugins {
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    // Internal modules
    implementation(project(":data:data-social"))
    implementation(project(":data:data-browser"))
    implementation(project(":business:business-social"))
    implementation(project(":client:client-siliconflow"))
    implementation(project(":client:client-brave-search"))
    implementation(project(":client:client-jina"))
    implementation(project(":client:client-google"))

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

    // Google Cloud Firestore (for UserDataPurgeService batch operations)
    implementation(libs.google.cloud.firestore)
}
