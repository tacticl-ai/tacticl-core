plugins {
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    // Internal modules
    implementation(project(":data-social"))
    implementation(project(":business-social"))
    implementation(project(":client-siliconflow"))
    implementation(project(":client-brave-search"))
    implementation(project(":client-jina"))

    // Cidadel shared infrastructure
    implementation(libs.cidadel.service.framework.base)
    implementation(libs.cidadel.framework.exception)
    implementation(libs.cidadel.framework.logging)
    implementation(libs.cidadel.framework.secrets)
    implementation(libs.cidadel.framework.token.issuance)
    implementation(libs.cidadel.client.base)
    implementation(libs.cidadel.framework.llm.router)

    // Strategiz product-specific libraries
    implementation(libs.strategiz.client.anthropic.direct)
    implementation(libs.strategiz.client.openai.direct)
    implementation(libs.strategiz.client.grok.direct)

    // Spring Boot
    implementation(libs.spring.boot.starter.web)

    // Jackson
    implementation(libs.jackson.databind)

    // Testing
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}
