plugins {
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    // Strategiz shared frameworks
    implementation(libs.strategiz.framework.exception)
    implementation(libs.strategiz.framework.secrets)

    // Spring Boot
    implementation(libs.spring.boot.starter.web)

    // Testing
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.junit.jupiter)
}
