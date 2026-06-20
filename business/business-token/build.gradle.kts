// business-token — Personal access token domain logic (issue, list, revoke)
plugins {
    `java-library`
}

dependencies {
    // parent business/build.gradle.kts provides: framework-exception, framework-logging, spring-web, jackson, test deps
    // api: PersonalAccessTokenService's public signature returns data-token types.
    api(project(":data:data-token"))
}
