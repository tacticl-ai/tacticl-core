// service-conversation — REST controller + HTTP DTOs for conversation sessions
plugins {
    `java-library`
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":business:business-conversation"))
    implementation(project(":data:data-conversation"))
    implementation(libs.cidadel.service.framework.base)
    implementation(libs.cidadel.framework.token.issuance)
    testRuntimeOnly(libs.junit.platform.launcher)
}
