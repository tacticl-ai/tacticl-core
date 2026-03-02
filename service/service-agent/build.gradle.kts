plugins {
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    // Internal modules
    implementation(project(":business:business-agent"))
    implementation(project(":business:business-social"))
    implementation(project(":data:data-social"))

    // Cidadel shared infrastructure (module-specific, beyond parent)
    implementation(libs.cidadel.service.framework.base)
    implementation(libs.cidadel.framework.token.issuance)
    implementation(libs.cidadel.framework.api.docs)
    implementation(libs.cidadel.framework.llm.router)
    implementation(libs.cidadel.client.base)

    // WebSocket (module-specific)
    implementation(libs.spring.boot.starter.websocket)
}
