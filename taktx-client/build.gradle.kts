plugins {
    id("java")
    id("maven-publish")
    alias(libs.plugins.spotless)
    id("signing")
}

// Group and version are inherited from the root project

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation(project(":taktx-shared"))
    implementation("org.slf4j:slf4j-api:2.0.16")
    implementation("org.slf4j:slf4j-simple:2.0.16")
    implementation(libs.kafka.clients)
    implementation("io.github.classgraph:classgraph:4.8.179")
    implementation(libs.jackson.annotations)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.cbor)
    implementation(libs.awaitility)

    compileOnly("jakarta.enterprise:jakarta.enterprise.cdi-api:4.1.0")

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    // Add these two lines to enable Lombok in tests
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
}

// These are required for Maven Central
java {
    withJavadocJar()
    withSourcesJar()
}

tasks.test {
    useJUnitPlatform()
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