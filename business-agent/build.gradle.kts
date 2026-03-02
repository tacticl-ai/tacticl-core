plugins {
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    // Internal modules
    implementation(project(":data-social"))
    implementation(project(":data-browser"))
    implementation(project(":business-social"))
    implementation(project(":client-siliconflow"))
    implementation(project(":client-brave-search"))
    implementation(project(":client-jina"))
    implementation(project(":client-google"))

    // Cidadel shared infrastructure
    implementation(libs.cidadel.service.framework.base)
    implementation(libs.cidadel.framework.exception)
    implementation(libs.cidadel.framework.logging)
    implementation(libs.cidadel.framework.secrets)
    implementation(libs.cidadel.framework.token.issuance)
    implementation(libs.cidadel.client.base)
    implementation(libs.cidadel.framework.llm.router)

    // Cidadel LLM clients
    implementation(libs.cidadel.client.anthropic.direct)
    implementation(libs.cidadel.client.openai.direct)
    implementation(libs.cidadel.client.grok.direct)

    // Spring Boot
    implementation(libs.spring.boot.starter.web)

    // Google Cloud Firestore (for UserDataPurgeService batch operations)
    implementation(libs.google.cloud.firestore)

    // Jackson
    implementation(libs.jackson.databind)

    // Testing
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}
