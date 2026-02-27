plugins {
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":data-social"))
    implementation(project(":client-twitter"))
    implementation(project(":client-linkedin"))
    implementation(project(":client-instagram"))
    implementation(project(":client-google"))
    implementation(project(":client-github"))

    // Cidadel shared infrastructure
    implementation(libs.cidadel.client.base)
    implementation(libs.cidadel.framework.exception)
    implementation(libs.cidadel.framework.logging)
    implementation(libs.cidadel.framework.secrets)
    implementation(libs.cidadel.framework.resilience)

    // Spring Boot
    implementation(libs.spring.boot.starter.web)

    // Jackson
    implementation(libs.jackson.databind)

    // Testing
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
}
