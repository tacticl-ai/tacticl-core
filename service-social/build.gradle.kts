plugins {
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":business-social"))
    implementation(project(":data-social"))

    // Cidadel shared infrastructure
    implementation(libs.cidadel.framework.authorization)
    implementation(libs.cidadel.framework.exception)
    implementation(libs.cidadel.framework.logging)
    implementation(libs.cidadel.framework.api.docs)

    // Spring Boot
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.validation)

    // OpenAPI
    implementation(libs.springdoc.openapi)

    // Testing
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.junit.jupiter)
}
