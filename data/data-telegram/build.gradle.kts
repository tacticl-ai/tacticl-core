// data-telegram — MongoDB entities + repositories for Telegram links
plugins {
    `java-library`
}

dependencies {
    // MongoDB starter inherited from data/build.gradle.kts parent
    api(project(":data:data-connections"))  // for BaseMongoEntity — exposed to consumers
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly(rootProject.libs.junit.platform.launcher)
}
