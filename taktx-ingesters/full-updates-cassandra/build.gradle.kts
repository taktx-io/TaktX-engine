/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

plugins {
    id("java")
    alias(libs.plugins.quarkus)
    alias(libs.plugins.spotless)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(23)
    }
}

tasks {
    withType<JavaCompile>().configureEach {
        options.release = 23
    }

    withType<Javadoc>().configureEach {
        with(options as StandardJavadocDocletOptions) {
            addStringOption("-release", "23")
        }
    }

    withType<Test>().configureEach {
        javaLauncher.set(project.javaToolchains.launcherFor {
            languageVersion = JavaLanguageVersion.of(23)
        })
    }
}

dependencies {
    implementation(project(":taktx-client-quarkus"))
    implementation(project(":taktx-client"))

    implementation(enforcedPlatform("io.quarkus.platform:quarkus-cassandra-bom:3.28.4"))
    implementation(enforcedPlatform(libs.quarkus.bom.get()))
    implementation(libs.quarkus.arc)
    implementation("com.datastax.oss.quarkus:cassandra-quarkus-client")
    implementation("org.apache.cassandra:java-driver-mapper-runtime")

    testImplementation(libs.quarkus.junit5)
    testImplementation(libs.quarkus.junit5.mockito)

    // Testcontainers for integration tests
    testImplementation("org.testcontainers:junit-jupiter:1.19.0")
    testImplementation("org.testcontainers:cassandra:1.19.0")
    testImplementation("org.testcontainers:testcontainers:1.19.0")

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    annotationProcessor("com.datastax.oss.quarkus:cassandra-quarkus-mapper-processor:1.3.0")
}

spotless {
    java {
        googleJavaFormat()
    }
}