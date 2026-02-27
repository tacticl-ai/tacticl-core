plugins {
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    // Internal modules
    implementation(project(":data-social"))

    // Cidadel shared infrastructure
    implementation(libs.cidadel.framework.authorization)
    implementation(libs.cidadel.framework.exception)
    implementation(libs.cidadel.framework.logging)

    // Spring Boot
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.validation)

    // Swagger
    implementation(libs.springdoc.openapi)

    // Jackson
    implementation(libs.jackson.databind)

    // Testing
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
}
