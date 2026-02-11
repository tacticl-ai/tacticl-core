plugins {
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    // Strategiz shared frameworks
    implementation(libs.strategiz.framework.exception)
    implementation(libs.strategiz.framework.logging)

    // Spring Boot
    implementation(libs.spring.boot.starter.web)

    // Jackson
    implementation(libs.jackson.databind)
    implementation(libs.jackson.annotations)

    // Testing
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.junit.jupiter)
}
