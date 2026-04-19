// business-telegram — Telegram chat adapter business logic
plugins {
    `java-library`
}

dependencies {
    implementation(project(":client:client-telegram"))
    implementation(project(":data:data-telegram"))
    implementation(libs.cidadel.framework.secrets)
}
