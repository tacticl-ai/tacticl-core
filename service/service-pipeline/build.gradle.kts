// service-pipeline — REST controllers for PDLC v2 status, events, checkpoint resolution
plugins {
    `java-library`
}

dependencies {
    implementation(project(":business:business-pipeline"))
    implementation(project(":data:data-pipeline"))
    implementation(project(":data:data-sparks"))
    implementation(libs.cidadel.service.framework.base)
    testRuntimeOnly(libs.junit.platform.launcher)
}
