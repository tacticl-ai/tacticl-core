plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":service-social"))
    implementation(project(":service-agent"))
    implementation(project(":business-social"))
    implementation(project(":business-agent"))
    implementation(project(":data-social"))
    implementation(project(":client-twitter"))
    implementation(project(":client-linkedin"))
    implementation(project(":client-instagram"))
    implementation(project(":client-siliconflow"))
    implementation(project(":client-brave-search"))
    implementation(project(":client-jina"))

    // Strategiz shared frameworks
    implementation(libs.strategiz.framework.authorization)
    implementation(libs.strategiz.framework.token.issuance)
    implementation(libs.strategiz.framework.exception)
    implementation(libs.strategiz.framework.logging)
    implementation(libs.strategiz.framework.secrets)
    implementation(libs.strategiz.framework.resilience)
    implementation(libs.strategiz.framework.api.docs)
    implementation(libs.strategiz.client.anthropic.direct)
    implementation(libs.strategiz.framework.llm.router)
    implementation(libs.strategiz.client.openai.direct)
    implementation(libs.strategiz.client.grok.direct)

    // Spring Boot
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.websocket)

    // Testing
    testImplementation(libs.spring.boot.starter.test)
}

springBoot {
    mainClass = "io.strategiz.social.application.TacticlApplication"
}
