plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":service-social"))
    implementation(project(":business-social"))
    implementation(project(":data-social"))
    implementation(project(":client-twitter"))
    implementation(project(":client-linkedin"))
    implementation(project(":client-instagram"))

    // Strategiz shared frameworks
    implementation(libs.strategiz.framework.authorization)
    implementation(libs.strategiz.framework.token.issuance)
    implementation(libs.strategiz.framework.exception)
    implementation(libs.strategiz.framework.logging)
    implementation(libs.strategiz.framework.secrets)
    implementation(libs.strategiz.framework.resilience)
    implementation(libs.strategiz.framework.api.docs)

    // Spring Boot
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.security)

    // Testing
    testImplementation(libs.spring.boot.starter.test)
}

springBoot {
    mainClass = "io.strategiz.social.application.SocialAutomationApplication"
}
