plugins {
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    // Internal modules
    implementation(project(":data:data-browser"))
    implementation(project(":data:data-social"))
    implementation(project(":business:business-agent"))
    implementation(project(":client:client-gcs"))

    // Cidadel shared infrastructure (module-specific, beyond parent)
    implementation(libs.cidadel.framework.secrets)
    implementation(libs.cidadel.client.base)
    implementation(libs.cidadel.framework.llm.router)

    // Google Cloud Firestore
    implementation(libs.google.cloud.firestore)

    // Playwright (browser automation)
    implementation(libs.playwright)
}
