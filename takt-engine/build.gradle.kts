plugins {
    id("java")
    id("com.diffplug.spotless")
    id("io.quarkus") version "3.19.3"
    id("com.google.cloud.tools.jib") version "3.4.4"
}

val quarkusPlatformGroupId: String by project
val quarkusPlatformArtifactId: String by project
val quarkusPlatformVersion: String by project

dependencies {
    implementation(project(":takt-shared"))
    implementation(enforcedPlatform("${quarkusPlatformGroupId}:quarkus-camel-bom:${quarkusPlatformVersion}"))
    implementation(enforcedPlatform("${quarkusPlatformGroupId}:${quarkusPlatformArtifactId}:${quarkusPlatformVersion}"))
    implementation("io.quarkus:quarkus-kafka-streams")
    implementation("io.quarkus:quarkus-jaxb")
    implementation("io.quarkus:quarkus-resteasy-client")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-cbor")
    implementation("com.javax0.license3j:license3j:3.3.0")

    // Micrometer
    implementation("io.quarkus:quarkus-micrometer")
    implementation("io.quarkus:quarkus-micrometer-registry-prometheus")
    implementation("io.quarkus:quarkus-kafka-client")

    //    implementation("io.quarkus:quarkus-container-image-docker")
//    implementation("io.quarkus:quarkus-cache")
//    implementation("io.quarkus:quarkus-smallrye-reactive-messaging-kafka")
//    implementation("io.quarkus:quarkus-arc")
//    implementation("io.quarkus:quarkus-smallrye-openapi")
//    implementation("io.quarkus:quarkus-resteasy-jackson")
//    implementation("io.quarkus:quarkus-jackson")

    implementation("org.camunda.feel:feel-engine:1.19.0")
    implementation("com.cronutils:cron-utils:9.2.1")

    testImplementation(project(":takt-client"))
    testImplementation("io.quarkus:quarkus-jaxb")
//    testImplementation("io.quarkus:quarkus-messaging-kafka")
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.quarkus:quarkus-junit5-mockito")
    testImplementation("io.quarkus:quarkus-jacoco")
    testImplementation("org.assertj:assertj-core:3.24.2")
    testImplementation("org.testcontainers:kafka")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.awaitility:awaitility:4.2.1")

    compileOnly("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")

    implementation("org.mapstruct:mapstruct:1.6.0")
    annotationProcessor("org.mapstruct:mapstruct-processor:1.6.0")
}

group = "nl.flomaestro"
version = "1.0.0-SNAPSHOT"

tasks.test {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
}

spotless {
    java {
        googleJavaFormat()
    }
}