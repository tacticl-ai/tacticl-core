plugins {
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    // Internal modules
    implementation(project(":business-agent"))
    implementation(project(":business-social"))
    implementation(project(":data-social"))

    // Strategiz shared frameworks
    implementation(libs.strategiz.framework.authorization)
    implementation(libs.strategiz.framework.exception)
    implementation(libs.strategiz.framework.logging)
    implementation(libs.strategiz.framework.api.docs)

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
