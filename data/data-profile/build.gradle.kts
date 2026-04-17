// data-profile — User profile entities + repositories
plugins {
    `java-library`
}

dependencies {
    // parent data/build.gradle.kts provides: spring-boot-starter-data-mongodb, framework-exception, framework-logging
    api(project(":data:data-connections"))               // BaseMongoEntity — api because UserProfile extends it
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly(rootProject.libs.junit.platform.launcher)
}
