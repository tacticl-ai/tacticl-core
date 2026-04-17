// data-pipeline — MongoDB entities + repositories for PDLC v2 pipeline state
plugins {
    `java-library`
}

dependencies {
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly(rootProject.libs.junit.platform.launcher)
}
