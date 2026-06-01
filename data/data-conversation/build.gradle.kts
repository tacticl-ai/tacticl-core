// data-conversation — MongoDB entities + repositories for conversation sessions
plugins {
    `java-library`
}

dependencies {
    // SessionMode, SessionStatus, Turn, CostBreakdown live in data-cloud-orchestrator
    // and are referenced by ConversationSession (per cloud-agent-orchestrator SAD §9.1).
    api(project(":data:data-cloud-orchestrator"))

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly(rootProject.libs.junit.platform.launcher)
}
