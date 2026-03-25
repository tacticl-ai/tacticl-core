plugins {
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    // Rate limiting (module-specific, matches client-twitter pattern)
    implementation(libs.bucket4j.core)

    // JUnit Platform launcher required at runtime (client parent omits it)
    testRuntimeOnly(libs.junit.platform.launcher)
}
