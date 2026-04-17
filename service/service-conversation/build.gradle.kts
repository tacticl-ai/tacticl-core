// service-conversation — conversational requirements gathering before spark execution
plugins {
    `java-library`
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":data:data-conversation"))
    implementation(project(":data:data-pipeline"))
    implementation(project(":business:business-sparks"))
    implementation(project(":business:business-pipeline"))
    implementation(libs.cidadel.service.framework.base)
    implementation(libs.cidadel.framework.token.issuance)
    implementation(libs.cidadel.client.anthropic.direct)
    testRuntimeOnly(libs.junit.platform.launcher)
}
