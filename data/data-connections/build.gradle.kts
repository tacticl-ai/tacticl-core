// data-connections — MongoDB entities + repositories for connections and devices
plugins {
    `java-library`
}

dependencies {
    // MongoDB starter inherited from data/build.gradle.kts parent
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly(rootProject.libs.junit.platform.launcher)
}
