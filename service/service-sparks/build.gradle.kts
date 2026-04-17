// service-sparks — Spark REST API + SSE endpoints
plugins {
    `java-library`
}

dependencies {
    implementation(project(":business:business-sparks"))
    implementation(project(":data:data-sparks"))
    implementation(libs.cidadel.service.framework.base)
    testRuntimeOnly(libs.junit.platform.launcher)
}
