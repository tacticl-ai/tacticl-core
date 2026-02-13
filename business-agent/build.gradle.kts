plugins {
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    // Internal modules
    implementation(project(":data-social"))
    implementation(project(":business-social"))
    implementation(project(":client-siliconflow"))

    // Strategiz shared frameworks
    implementation(libs.strategiz.framework.exception)
    implementation(libs.strategiz.framework.logging)
    implementation(libs.strategiz.framework.secrets)
    implementation(libs.strategiz.client.base)

    // Spring Boot
    implementation(libs.spring.boot.starter.web)

    // Jackson
    implementation(libs.jackson.databind)

    // Testing
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
}
