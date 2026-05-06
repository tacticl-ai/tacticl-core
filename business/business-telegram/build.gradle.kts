// business-telegram — Telegram chat adapter business logic
plugins {
    `java-library`
}

dependencies {
    implementation(project(":client:client-telegram"))
    implementation(project(":data:data-telegram"))
    implementation(project(":data:data-sparks"))
    implementation(project(":data:data-pipeline"))
    implementation(project(":data:data-profile"))
    implementation(project(":business:business-sparks"))
    implementation(project(":business:business-pipeline"))
    implementation(project(":business:business-agent"))
    implementation(libs.cidadel.framework.secrets)
}
