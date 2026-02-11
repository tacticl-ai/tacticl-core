plugins {
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
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

    // OpenAPI
    implementation(libs.springdoc.openapi)

    // Testing
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.junit.jupiter)
}
