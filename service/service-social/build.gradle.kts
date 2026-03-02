plugins {
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    // Internal modules
    implementation(project(":business:business-social"))
    implementation(project(":data:data-social"))

    // Cidadel (module-specific, beyond parent)
    implementation(libs.cidadel.framework.api.docs)
}
