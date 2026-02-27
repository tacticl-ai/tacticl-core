plugins {
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    // Cidadel shared infrastructure
    implementation(libs.cidadel.framework.exception)
    implementation(libs.cidadel.framework.secrets)
    implementation(libs.cidadel.client.base)

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
