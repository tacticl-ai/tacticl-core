// business-conversation — ConversationService for requirements gathering before spark execution
plugins {
    `java-library`
}

dependencies {
    implementation(project(":data:data-conversation"))
    implementation(project(":data:data-pipeline"))
    implementation(project(":data:data-sparks"))
    implementation(project(":business:business-sparks"))
    implementation(project(":business:business-pipeline"))
    implementation(project(":client:client-github"))
    implementation(project(":client:client-ai-arbiter"))
    // Parent provides: exception, logging, web, jackson, test, junit
}
