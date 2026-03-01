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
    implementation(project(":client-gcs"))
    implementation(project(":data-browser"))
    implementation(project(":business-browser"))

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
