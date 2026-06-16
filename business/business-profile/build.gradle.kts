// business-profile — Profile domain logic (read, update, sign-out)
plugins {
    `java-library`
}

dependencies {
    // parent business/build.gradle.kts provides: framework-exception, framework-logging, spring-web, jackson, test deps
    // api (not implementation): UserRepoService's public signature uses data-profile types
    // (RepoSource, UserRepo), so consumers (business-pipeline, business-voice) get them transitively.
    api(project(":data:data-profile"))
    implementation(project(":data:data-pipeline"))        // EntryPoint/EntryPointRepository for product channel bindings
    implementation(project(":client:client-github"))      // GitHubClient/GitHubConfig for product repo provisioning
    implementation(libs.cidadel.framework.authorization)  // needed for AuthenticatedUser; not in business parent
}
