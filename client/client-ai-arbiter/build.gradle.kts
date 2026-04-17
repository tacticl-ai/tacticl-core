// client-ai-arbiter — Arbiter gRPC client (connects to cidadel-ai-arbiter service)
plugins {
    `java-library`
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.protobuf)
}

dependencies {
    // gRPC runtime (netty transport + protobuf integration + stub API)
    implementation(libs.grpc.netty.shaded)
    implementation(libs.grpc.protobuf)
    implementation(libs.grpc.stub)
    implementation(libs.protobuf.java)
    implementation(libs.jackson.databind)

    // gRPC generated code uses @javax.annotation.Generated (not in JDK 9+)
    compileOnly(libs.javax.annotation.api)
    testCompileOnly(libs.javax.annotation.api)

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly(libs.junit.platform.launcher)
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:${libs.versions.grpc.get()}"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                create("grpc")
            }
        }
    }
}
