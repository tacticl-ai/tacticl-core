plugins {
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    // Internal — need base repository class
    implementation(project(":data:data-social"))
}
