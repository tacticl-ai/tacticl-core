// Intermediate parent for client-* modules
subprojects {
    dependencies {
        // Cidadel shared infrastructure
        add("implementation", rootProject.libs.cidadel.framework.exception)
        add("implementation", rootProject.libs.cidadel.framework.secrets)
        add("implementation", rootProject.libs.cidadel.client.base)
        add("implementation", rootProject.libs.spring.boot.starter.web)
        add("implementation", rootProject.libs.jackson.databind)

        // Testing
        add("testImplementation", rootProject.libs.spring.boot.starter.test)
        add("testImplementation", rootProject.libs.junit.jupiter)
    }
}
