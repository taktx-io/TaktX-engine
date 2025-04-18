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
    implementation("io.quarkus:quarkus-smallrye-openapi")
    implementation("io.quarkus:quarkus-swagger-ui")
    implementation("io.quarkus:quarkus-resteasy-jackson")
    implementation("io.quarkus:quarkus-jackson")

    // Micrometer
    implementation(libs.quarkus.micrometer)
    implementation(libs.quarkus.micrometer.registry.prometheus)
    implementation(libs.quarkus.kafka.client)

    implementation(libs.camunda.feel)
    implementation(libs.cronutils)

    testImplementation(project(":taktx-client"))
    testImplementation(libs.quarkus.jaxb)
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
version = "0.0.3-alpha1"

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

tasks.withType<JacocoReport> {
    executionData.setFrom(files(
        "${buildDir}/jacoco/test.exec",
        "${buildDir}/jacoco-quarkus.exec"
    ))
}