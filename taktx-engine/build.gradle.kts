plugins {
    id("java")
    alias(libs.plugins.spotless)
    alias(libs.plugins.quarkus)
    alias(libs.plugins.jib)
}

dependencies {
    implementation(project(":taktx-shared"))
    implementation(enforcedPlatform(libs.quarkus.camel.bom.get()))
    implementation(enforcedPlatform(libs.quarkus.bom.get()))
    implementation(libs.quarkus.kafka.streams)
    implementation(libs.quarkus.jaxb)
    implementation(libs.quarkus.resteasy.client)
    implementation(libs.jackson.cbor)
    implementation(libs.license3j)

    // Micrometer
    implementation(libs.quarkus.micrometer)
    implementation(libs.quarkus.micrometer.registry.prometheus)
    implementation(libs.quarkus.kafka.client)

    //    implementation("io.quarkus:quarkus-container-image-docker")
//    implementation("io.quarkus:quarkus-cache")
//    implementation("io.quarkus:quarkus-smallrye-reactive-messaging-kafka")
//    implementation("io.quarkus:quarkus-arc")
//    implementation("io.quarkus:quarkus-smallrye-openapi")
//    implementation("io.quarkus:quarkus-resteasy-jackson")
//    implementation("io.quarkus:quarkus-jackson")

    implementation(libs.camunda.feel)
    implementation(libs.cronutils)

    testImplementation(project(":taktx-client"))
    testImplementation(libs.quarkus.jaxb)
//    testImplementation("io.quarkus:quarkus-messaging-kafka")
    testImplementation(libs.quarkus.junit5)
    testImplementation(libs.quarkus.junit5.mockito)
    testImplementation(libs.quarkus.jacoco)
    testImplementation(libs.assertj.core)
    testImplementation(libs.testcontainers.kafka)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.awaitility)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    implementation(libs.mapstruct)
    annotationProcessor(libs.mapstruct.processor)
}

group = "io.taktx"
version = "0.0.2-alpha6"

tasks.test {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
}

spotless {
    java {
        googleJavaFormat()
    }
}

// Adds dependency locking to ensure reproducible builds
dependencyLocking {
    lockAllConfigurations()
}