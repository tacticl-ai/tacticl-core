// data-sparks — MongoDB entities + repositories for sparks and checkpoints
plugins {
    `java-library`
}

dependencies {
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly(rootProject.libs.junit.platform.launcher)
}
