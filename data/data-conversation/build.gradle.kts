// data-conversation — MongoDB entities + repositories for conversation sessions
plugins {
    `java-library`
}

dependencies {
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly(rootProject.libs.junit.platform.launcher)
}
