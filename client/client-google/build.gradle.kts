plugins {
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    // Rate limiting (module-specific)
    implementation(libs.bucket4j.core)
}
