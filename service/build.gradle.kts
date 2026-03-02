// Intermediate parent for service-* modules
subprojects {
    dependencies {
        // Cidadel framework (shared across all services)
        add("implementation", rootProject.libs.cidadel.framework.authorization)
        add("implementation", rootProject.libs.cidadel.framework.exception)
        add("implementation", rootProject.libs.cidadel.framework.logging)

        // Spring Boot + API
        add("implementation", rootProject.libs.spring.boot.starter.web)
        add("implementation", rootProject.libs.spring.boot.starter.validation)
        add("implementation", rootProject.libs.springdoc.openapi)
        add("implementation", rootProject.libs.jackson.databind)

        // Testing
        add("testImplementation", rootProject.libs.spring.boot.starter.test)
        add("testImplementation", rootProject.libs.junit.jupiter)
        add("testImplementation", rootProject.libs.mockito.core)
    }
}
