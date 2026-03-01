plugins {
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    // Internal modules
    implementation(project(":data-browser"))
    implementation(project(":data-social"))
    implementation(project(":business-agent"))
    implementation(project(":client-gcs"))

    // Cidadel shared infrastructure
    implementation(libs.cidadel.framework.exception)
    implementation(libs.cidadel.framework.logging)
    implementation(libs.cidadel.framework.secrets)
    implementation(libs.cidadel.client.base)
    implementation(libs.cidadel.framework.llm.router)

    // Spring Boot
    implementation(libs.spring.boot.starter.web)

    // Google Cloud Firestore
    implementation(libs.google.cloud.firestore)

    // Jackson
    implementation(libs.jackson.databind)

    // Playwright (browser automation)
    implementation(libs.playwright)

    // Testing
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}
