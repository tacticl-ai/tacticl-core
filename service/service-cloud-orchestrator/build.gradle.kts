// service-cloud-orchestrator — /ws/cloud-agent WebSocket handler + REST shims
// for the cloud agent orchestrator (session signal dispatch, persona/skill admin).
plugins {
    `java-library`
}

dependencies {
    implementation(project(":business:business-cloud-orchestrator"))
    implementation(project(":business:business-voice"))
    implementation(project(":data:data-cloud-orchestrator"))
    implementation(libs.cidadel.service.framework.base)
    implementation(libs.cidadel.framework.authorization)
    implementation(libs.spring.boot.starter.websocket)
    testRuntimeOnly(libs.junit.platform.launcher)
}
