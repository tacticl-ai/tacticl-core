// business-connections — Connection and device registry services
plugins {
    `java-library`
}

dependencies {
    implementation(project(":data:data-connections"))
    implementation(project(":data:data-profile"))
    implementation(project(":client:client-github"))
    implementation(libs.cidadel.framework.secrets)
}
