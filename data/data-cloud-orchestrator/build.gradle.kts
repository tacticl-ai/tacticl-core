// data-cloud-orchestrator — MongoDB entities + repositories for personas, skills,
// playbooks v2, turns, and voice presets (cloud agent orchestrator domain).
//
// Cross-data deps: PlaybookV2 references SparkType (data-sparks) + PdlcPhase /
// PdlcRole (data-pipeline). These are stable enums shared across domains, so the
// cross-data references are intentional and minimal.
plugins {
    `java-library`
}

dependencies {
    api(project(":data:data-sparks"))
    api(project(":data:data-pipeline"))
    implementation(rootProject.libs.jackson.databind)

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly(rootProject.libs.junit.platform.launcher)
}
