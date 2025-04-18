plugins {
    id("java")
    alias(libs.plugins.quarkus)
    alias(libs.plugins.spotless)
}

dependencies {
    implementation(enforcedPlatform(libs.quarkus.camel.bom.get()))
    implementation(enforcedPlatform(libs.quarkus.bom.get()))
    implementation("io.quarkus:quarkus-scheduler")
    implementation("io.quarkus:quarkus-arc")
    implementation(libs.quarkus.jaxb)
    implementation("io.quarkus:quarkus-smallrye-openapi")
    implementation("io.quarkus:quarkus-swagger-ui")
    implementation(libs.quarkus.resteasy.client)
    implementation("io.quarkus:quarkus-resteasy-jackson")
    implementation("io.quarkus:quarkus-jackson")
    implementation(project(":taktx-client"))
    implementation(project(":taktx-shared"))

    testImplementation(libs.quarkus.junit5)
    testImplementation(libs.quarkus.junit5.mockito)
    testImplementation("io.quarkus:quarkus-test-kafka-companion")

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
}

// Adds dependency locking to ensure reproducible builds
dependencyLocking {
    lockAllConfigurations()
}

spotless {
    java {
        googleJavaFormat()
    }
}