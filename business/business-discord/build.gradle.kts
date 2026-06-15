// business-discord — Discord Interactions adapter business logic (normalize → IngressRequest, run-update channel)
plugins {
    `java-library`
}

dependencies {
    implementation(project(":client:client-discord"))
    implementation(project(":client:client-ai-arbiter"))
    implementation(project(":data:data-discord"))
    implementation(project(":data:data-pipeline"))
    implementation(project(":data:data-sparks"))
    implementation(project(":business:business-pipeline"))
    // cidadel framework-exception/logging, spring-web, jackson + test deps are injected by business/build.gradle.kts

    testRuntimeOnly(libs.junit.platform.launcher)
}
