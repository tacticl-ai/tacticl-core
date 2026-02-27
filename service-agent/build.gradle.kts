plugins {
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    // Internal modules
    implementation(project(":business-agent"))
    implementation(project(":business-social"))
    implementation(project(":data-social"))

    // Cidadel shared infrastructure
    implementation(libs.cidadel.service.framework.base)
    implementation(libs.cidadel.framework.authorization)
    implementation(libs.cidadel.framework.token.issuance)
    implementation(libs.cidadel.framework.exception)
    implementation(libs.cidadel.framework.logging)
    implementation(libs.cidadel.framework.api.docs)
    implementation(libs.cidadel.framework.llm.router)
    implementation(libs.cidadel.client.base)

    // Spring Boot
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.websocket)

    // Swagger
    implementation(libs.springdoc.openapi)

    // Jackson
    implementation(libs.jackson.databind)

    // Testing
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
}
