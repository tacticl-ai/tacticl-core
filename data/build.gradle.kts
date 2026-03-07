// Intermediate parent for data-* modules
subprojects {
    apply(plugin = "java-library")
    dependencies {
        add("implementation", rootProject.libs.cidadel.framework.exception)
        add("implementation", rootProject.libs.cidadel.framework.logging)
        add("api", rootProject.libs.cidadel.data.framework.base)
        add("implementation", rootProject.libs.spring.boot.starter.web)
        add("api", rootProject.libs.google.cloud.firestore)
        add("implementation", rootProject.libs.jackson.databind)

        add("testImplementation", rootProject.libs.spring.boot.starter.test)
        add("testImplementation", rootProject.libs.junit.jupiter)
        add("testRuntimeOnly", rootProject.libs.junit.platform.launcher)
    }
}
