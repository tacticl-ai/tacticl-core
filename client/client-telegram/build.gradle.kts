// client-telegram — Telegram Bot API client
plugins {
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(libs.httpclient)
    implementation(libs.bucket4j.core)

    testRuntimeOnly(libs.junit.platform.launcher)
}
