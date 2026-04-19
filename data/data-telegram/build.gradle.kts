// data-telegram — MongoDB entities + repositories for Telegram links
plugins {
    `java-library`
}

dependencies {
    // MongoDB starter inherited from data/build.gradle.kts parent
    implementation(project(":data:data-connections"))  // for BaseMongoEntity
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly(rootProject.libs.junit.platform.launcher)
}
