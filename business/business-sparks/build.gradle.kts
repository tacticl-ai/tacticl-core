// business-sparks — Spark lifecycle, classification, and event emission services
plugins {
    `java-library`
}

dependencies {
    implementation(project(":data:data-sparks"))
    implementation(libs.cidadel.client.anthropic.direct)
}
