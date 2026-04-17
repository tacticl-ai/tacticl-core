plugins {
    `java-library`
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    // Internal modules
    implementation(project(":business:business-sparks"))
    implementation(project(":business:business-pipeline"))
    implementation(project(":data:data-sparks"))

    // Cidadel shared infrastructure (module-specific, beyond parent)
    implementation(libs.cidadel.service.framework.base)
    implementation(libs.cidadel.framework.token.issuance)
    implementation(libs.cidadel.framework.api.docs)
    implementation(libs.cidadel.client.base)

    // Cidadel LLM client (direct Anthropic calls for cloud orchestration)
    implementation(libs.cidadel.client.anthropic.direct)

    // JUnit Platform launcher required at runtime for Mockito extension compatibility
    testRuntimeOnly(libs.junit.platform.launcher)
}
