// client-whisper — OpenAI Whisper API client (audio transcription)
plugins {
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(libs.httpclient)
    implementation(libs.bucket4j.core)

    testRuntimeOnly(libs.junit.platform.launcher)
}
