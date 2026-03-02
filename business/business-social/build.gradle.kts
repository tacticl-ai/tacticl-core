plugins {
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    // Internal modules
    implementation(project(":data:data-social"))
    implementation(project(":client:client-twitter"))
    implementation(project(":client:client-linkedin"))
    implementation(project(":client:client-instagram"))
    implementation(project(":client:client-google"))
    implementation(project(":client:client-github"))

    // Cidadel shared infrastructure (module-specific, beyond parent)
    implementation(libs.cidadel.client.base)
    implementation(libs.cidadel.framework.secrets)
    implementation(libs.cidadel.framework.resilience)
}
