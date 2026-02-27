plugins {
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    // Cidadel shared infrastructure
    implementation(libs.cidadel.framework.exception)
    implementation(libs.cidadel.framework.secrets)

    // Spring Boot
    implementation(libs.spring.boot.starter.web)

    // Testing
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.junit.jupiter)
}
