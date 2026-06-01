// client-discord — Discord Interactions + REST API client (Ed25519 verify, guild commands, channel messages)
plugins {
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(libs.httpclient)
    implementation(libs.bucket4j.core)

    // Ed25519 verification + raw 32-byte public-key decoding use only java.security (JDK 25).

    testRuntimeOnly(libs.junit.platform.launcher)
}
