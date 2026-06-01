// data-discord — MongoDB entities + repositories for Discord interaction dedup + account links
plugins {
    `java-library`
}

dependencies {
    // MongoDB starter inherited from data/build.gradle.kts parent
    api(project(":data:data-connections"))  // for BaseMongoEntity — exposed to consumers
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-data-mongodb-test")  // @DataMongoTest
    testImplementation("de.flapdoodle.embed:de.flapdoodle.embed.mongo:4.20.0") {
        exclude(group = "org.mongodb")  // use MongoDB driver from Spring Boot BOM (5.x)
    }
    testRuntimeOnly(rootProject.libs.junit.platform.launcher)
}
