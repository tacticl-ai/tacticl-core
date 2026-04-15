// service-connections — Connection and device REST controllers
plugins {
    `java-library`
}

dependencies {
    implementation(project(":business:business-connections"))
    implementation(project(":data:data-connections"))
    implementation(libs.cidadel.service.framework.base)
    testRuntimeOnly(libs.junit.platform.launcher)
}
