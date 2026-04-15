plugins {
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    // Cidadel shared infrastructure (module-specific, beyond parent)
    implementation(libs.cidadel.service.framework.base)
}
