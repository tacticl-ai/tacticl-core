plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    // Service layer
    implementation(project(":service:service-social"))
    implementation(project(":service:service-agent"))
    implementation(project(":service:service-spark"))
    implementation(project(":service:service-checkpoint"))
    implementation(project(":service:service-repo"))
    implementation(project(":service:service-token"))

    // Business layer
    implementation(project(":business:business-social"))
    implementation(project(":business:business-agent"))
    implementation(project(":business:business-browser"))

    // Data layer
    implementation(project(":data:data-social"))
    implementation(project(":data:data-browser"))

    // Client layer
    implementation(project(":client:client-twitter"))
    implementation(project(":client:client-linkedin"))
    implementation(project(":client:client-instagram"))
    implementation(project(":client:client-google"))
    implementation(project(":client:client-github"))
    implementation(project(":client:client-siliconflow"))
    implementation(project(":client:client-brave-search"))
    implementation(project(":client:client-jina"))
    implementation(project(":client:client-gcs"))

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

    // Cidadel auth (all /v1/auth/* endpoints)
    implementation(libs.cidadel.service.auth)
    implementation(libs.cidadel.service.framework.base)
    implementation(libs.cidadel.business.auth)
    implementation(libs.cidadel.data.auth)
    implementation(libs.cidadel.data.user)
    implementation(libs.cidadel.data.session)
    implementation(libs.cidadel.data.product)
    implementation(libs.cidadel.data.framework.base)
    implementation(libs.cidadel.client.google)
    implementation(libs.cidadel.client.facebook)
    implementation(libs.cidadel.client.sms)

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
