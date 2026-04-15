// data/build.gradle.kts
subprojects {
    apply(plugin = "java-library")
    dependencies {
        add("api", "org.springframework.boot:spring-boot-starter-data-mongodb")
        add("implementation", rootProject.libs.cidadel.framework.exception)
        add("implementation", rootProject.libs.cidadel.framework.logging)
    }
}
