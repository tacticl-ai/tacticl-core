plugins {
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    // Strategiz shared frameworks
    implementation(libs.strategiz.framework.exception)
    implementation(libs.strategiz.framework.secrets)
    implementation(libs.strategiz.client.base)

    // Spring Boot
    implementation(libs.spring.boot.starter.web)

    // HTTP & JSON
    implementation(libs.httpclient)
    implementation(libs.jackson.databind)

    // Rate limiting
    implementation(libs.bucket4j.core)

    // Testing
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
}
