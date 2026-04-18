// data-profile — User profile entities + repositories
plugins {
    `java-library`
}

dependencies {
    // parent data/build.gradle.kts provides: spring-boot-starter-data-mongodb, framework-exception, framework-logging
    api(project(":data:data-connections"))               // BaseMongoEntity — api because UserProfile extends it
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-data-mongodb-test")  // @DataMongoTest
    testImplementation("de.flapdoodle.embed:de.flapdoodle.embed.mongo:4.20.0") {
        exclude(group = "org.mongodb")  // use MongoDB driver from Spring Boot BOM (5.x)
    }
    testRuntimeOnly(rootProject.libs.junit.platform.launcher)
}
