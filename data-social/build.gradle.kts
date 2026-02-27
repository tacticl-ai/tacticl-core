plugins {
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    // Cidadel shared infrastructure
    implementation(libs.cidadel.framework.exception)
    implementation(libs.cidadel.framework.logging)

    // Spring Boot
    implementation(libs.spring.boot.starter.web)

    // Google Cloud Firestore
    implementation(libs.google.cloud.firestore)

    // Jackson
    implementation(libs.jackson.databind)
    implementation(libs.jackson.annotations)

    // Testing
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.junit.jupiter)
}
