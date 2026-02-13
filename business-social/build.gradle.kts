plugins {
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":data-social"))
    implementation(project(":client-twitter"))
    implementation(project(":client-linkedin"))
    implementation(project(":client-instagram"))

    // Strategiz shared frameworks
    implementation(libs.strategiz.client.base)
    implementation(libs.strategiz.framework.exception)
    implementation(libs.strategiz.framework.logging)
    implementation(libs.strategiz.framework.secrets)
    implementation(libs.strategiz.framework.resilience)

    // Spring Boot
    implementation(libs.spring.boot.starter.web)

    // Jackson
    implementation(libs.jackson.databind)

    // Testing
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
}
