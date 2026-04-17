// client-ai-arbiter — Arbiter gRPC client interface + stub (real gRPC swapped in later)
plugins {
    `java-library`
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    // JUnit Platform launcher required at runtime (client parent omits it)
    testRuntimeOnly(libs.junit.platform.launcher)
}
