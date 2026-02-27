plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":service-social"))
    implementation(project(":service-agent"))
    implementation(project(":service-spark"))
    implementation(project(":service-checkpoint"))
    implementation(project(":service-repo"))
    implementation(project(":service-token"))
    implementation(project(":business-social"))
    implementation(project(":business-agent"))
    implementation(project(":data-social"))
    implementation(project(":client-twitter"))
    implementation(project(":client-linkedin"))
    implementation(project(":client-instagram"))
    implementation(project(":client-google"))
    implementation(project(":client-github"))
    implementation(project(":client-siliconflow"))
    implementation(project(":client-brave-search"))
    implementation(project(":client-jina"))

    // Cidadel shared infrastructure
    implementation(libs.cidadel.framework.authorization)
    implementation(libs.cidadel.framework.token.issuance)
    implementation(libs.cidadel.framework.exception)
    implementation(libs.cidadel.framework.logging)
    implementation(libs.cidadel.framework.secrets)
    implementation(libs.cidadel.framework.resilience)
    implementation(libs.cidadel.framework.api.docs)
    implementation(libs.cidadel.framework.llm.router)

    // Strategiz product-specific libraries
    implementation(libs.strategiz.client.anthropic.direct)
    implementation(libs.strategiz.client.openai.direct)
    implementation(libs.strategiz.client.grok.direct)

    // Auth (reused from strategiz — all /v1/auth/* endpoints)
    implementation(libs.strategiz.service.auth)
    implementation(libs.strategiz.service.framework.base)
    implementation(libs.strategiz.business.token.auth)

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
