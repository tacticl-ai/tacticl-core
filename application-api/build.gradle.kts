plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    // Service layer
    implementation(project(":service:service-agent"))
    implementation(project(":service:service-spark"))
    implementation(project(":service:service-checkpoint"))
    implementation(project(":service:service-repo"))
    implementation(project(":service:service-token"))
    implementation(project(":service:service-connections"))
    implementation(project(":service:service-pipeline"))
    implementation(project(":service:service-conversation"))

    // Business layer
    implementation(project(":business:business-agent"))

    // Client layer
    implementation(project(":client:client-google"))
    implementation(project(":client:client-github"))
    implementation(project(":client:client-brave-search"))
    implementation(project(":client:client-jina"))

    // Cidadel shared infrastructure
    implementation(libs.cidadel.framework.authorization)
    implementation(libs.cidadel.framework.token.issuance)
    implementation(libs.cidadel.framework.exception)
    implementation(libs.cidadel.framework.logging)
    implementation(libs.cidadel.framework.secrets)
    implementation(libs.cidadel.framework.resilience)
    implementation(libs.cidadel.framework.api.docs)
    implementation(libs.cidadel.framework.llm.router)

    // Cidadel LLM clients
    implementation(libs.cidadel.client.anthropic.direct)
    implementation(libs.cidadel.client.openai.direct)
    implementation(libs.cidadel.client.grok.direct)

    // Cidadel service base (used by service-agent)
    implementation(libs.cidadel.service.framework.base)

    // Spring Boot
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.websocket)
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")

    // Testing
    testImplementation(libs.spring.boot.starter.test)
}

springBoot {
    mainClass = "io.strategiz.social.application.TacticlApplication"
}
