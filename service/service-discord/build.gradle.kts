// service-discord — Discord Interactions webhook controller (Ed25519 verify, PING→PONG, deferred ACK, async dispatch)
plugins {
    `java-library`
}

dependencies {
    implementation(project(":business:business-discord"))
    implementation(project(":business:business-pipeline"))
    implementation(project(":client:client-discord"))
    implementation(project(":data:data-discord"))
    implementation(libs.cidadel.service.framework.base)
    testRuntimeOnly(libs.junit.platform.launcher)
}
