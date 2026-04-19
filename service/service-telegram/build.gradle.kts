// service-telegram — Telegram webhook + link REST controllers
plugins {
    `java-library`
}

dependencies {
    implementation(project(":business:business-telegram"))
    implementation(project(":client:client-telegram"))
    implementation(project(":data:data-telegram"))
    implementation(libs.cidadel.service.framework.base)
    testRuntimeOnly(libs.junit.platform.launcher)
}
