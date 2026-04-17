// service-conversation — Conversation session REST controllers
plugins {
    `java-library`
}

dependencies {
    implementation(project(":data:data-conversation"))
    implementation(libs.cidadel.service.framework.base)
    testRuntimeOnly(libs.junit.platform.launcher)
}
