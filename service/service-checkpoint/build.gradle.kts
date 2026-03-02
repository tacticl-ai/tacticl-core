plugins {
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    // Internal modules
    implementation(project(":business:business-agent"))
    implementation(project(":data:data-social"))
}
