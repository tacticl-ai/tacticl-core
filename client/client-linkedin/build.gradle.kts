plugins {
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    // HTTP & rate limiting (module-specific)
    implementation(libs.httpclient)
    implementation(libs.bucket4j.core)
}
