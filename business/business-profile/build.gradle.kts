// business-profile — Profile domain logic (read, update, sign-out)
plugins {
    `java-library`
}

dependencies {
    // parent business/build.gradle.kts provides: framework-exception, framework-logging, spring-web, jackson, test deps
    implementation(project(":data:data-profile"))
    implementation(libs.cidadel.framework.authorization)  // needed for AuthenticatedUser; not in business parent
}
