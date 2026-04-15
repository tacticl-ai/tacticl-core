// business-connections — Connection and device registry services
plugins {
    `java-library`
}

dependencies {
    implementation(project(":data:data-connections"))
    implementation(libs.cidadel.framework.secrets)
}
