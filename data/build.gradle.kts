// Intermediate parent for data-* modules
subprojects {
    dependencies {
        add("implementation", rootProject.libs.cidadel.framework.exception)
        add("implementation", rootProject.libs.cidadel.framework.logging)
        add("implementation", rootProject.libs.spring.boot.starter.web)
        add("implementation", rootProject.libs.google.cloud.firestore)
        add("implementation", rootProject.libs.jackson.databind)
        add("implementation", rootProject.libs.jackson.annotations)

        add("testImplementation", rootProject.libs.spring.boot.starter.test)
        add("testImplementation", rootProject.libs.junit.jupiter)
        add("testImplementation", rootProject.libs.mockito.core)
        add("testRuntimeOnly", rootProject.libs.junit.platform.launcher)
    }
}
