plugins {
    id("java")
    id("com.diffplug.spotless")
    id("io.quarkus") version "3.15.1"
}

val quarkusPlatformGroupId: String by project
val quarkusPlatformArtifactId: String by project
val quarkusPlatformVersion: String by project

dependencies {
    implementation(enforcedPlatform("${quarkusPlatformGroupId}:quarkus-camel-bom:${quarkusPlatformVersion}"))
    implementation(project(":takt-shared"))
    implementation(project(":takt-client"))
    implementation(enforcedPlatform("${quarkusPlatformGroupId}:${quarkusPlatformArtifactId}:${quarkusPlatformVersion}"))
    implementation("io.quarkus:quarkus-smallrye-reactive-messaging-kafka")
    implementation("io.quarkus:quarkus-scheduler")
    implementation("io.quarkus:quarkus-jaxb")
    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-resteasy-jackson")
    implementation("io.quarkus:quarkus-smallrye-openapi")
    implementation("io.quarkus:quarkus-kafka-streams")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-cbor")
    implementation("org.apache.camel.quarkus:camel-quarkus-influxdb")
    implementation("com.influxdb:influxdb-client-java:7.2.0")

    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("org.assertj:assertj-core:3.24.2")
    testImplementation("org.mockito:mockito-core:3.12.4")

    compileOnly("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")
}

spotless {
    java {
        googleJavaFormat()
    }
}