// service-profile — Profile and sign-out REST controllers
plugins {
    `java-library`
}

dependencies {
    // parent service/build.gradle.kts provides: framework-authorization, framework-exception, spring-web, spring-validation, springdoc-openapi, jackson, test deps
    implementation(project(":business:business-profile"))
    implementation(project(":data:data-profile"))          // UserProfile return type from UserProfileService
    implementation(libs.cidadel.service.framework.base)   // BaseController
    implementation(libs.cidadel.framework.api.docs)       // OpenAPI/Swagger

    testImplementation(project(":data:data-profile"))
    testRuntimeOnly(libs.junit.platform.launcher)
}
