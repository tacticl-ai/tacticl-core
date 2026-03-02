plugins {
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    // Internal modules
    implementation(project(":data:data-social"))
}
