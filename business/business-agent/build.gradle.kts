plugins {
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    // Internal modules
    implementation(project(":client:client-brave-search"))
    implementation(project(":client:client-jina"))
    implementation(project(":client:client-google"))
    implementation(project(":client:client-github"))

    // Cidadel shared infrastructure (module-specific, beyond parent)
    implementation(libs.cidadel.service.framework.base)
    implementation(libs.cidadel.framework.secrets)
    implementation(libs.cidadel.framework.token.issuance)
    implementation(libs.cidadel.client.base)
    implementation(libs.cidadel.framework.llm.router)

    // Cidadel LLM clients
    implementation(libs.cidadel.client.anthropic.direct)
    implementation(libs.cidadel.client.openai.direct)
    implementation(libs.cidadel.client.grok.direct)

    // Cidadel AI engine
    implementation(libs.cidadel.business.ai.engine)
    implementation(libs.cidadel.client.claude.code)
    implementation(libs.cidadel.client.codex)
}
