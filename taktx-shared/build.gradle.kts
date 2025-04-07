plugins {
    id("java")
    alias(libs.plugins.xjc)
    alias(libs.plugins.spotless)
    `maven-publish`
    id("signing")
}

// Group and version are inherited from the root project

dependencies {
    implementation(libs.cronutils)
    implementation(libs.jackson.annotations)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.cbor)
    implementation(libs.kafka.clients)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.jackson.annotations)
    testImplementation(libs.reflections)
    testImplementation(libs.jaxb.runtime)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
}

tasks.test {
    useJUnitPlatform()
}

// Configure javadoc to work with Lombok
tasks.javadoc {
    options {
        this as StandardJavadocDocletOptions
        addStringOption("Xdoclint:none", "-quiet")
        addBooleanOption("html5", true)
        // Use Java 21 compatibility for modern language features
        addStringOption("source", "21")
    }
    isFailOnError = false
}

xjc {
    markGenerated.set(true)
    defaultPackage.set("io.taktx.bpmn")
}

// These are required for Maven Central
java {
    withJavadocJar()
    withSourcesJar()
}

// Adds dependency locking to ensure reproducible builds
dependencyLocking {
    lockAllConfigurations()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            // Maven Central requires POM metadata
            pom {
                name.set("TaktX Shared")
                description.set("Shared library for TaktX BPM Engine with licensing controls for Kafka partition limits")
                url.set("https://github.com/taktx-io/TaktX-engine")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0")
                    }
                }

                developers {
                    developer {
                        id.set("taktx")
                        name.set("TaktX Development Team")
                        email.set("info@taktx.io")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/taktx-io/TaktX-engine.git")
                    developerConnection.set("scm:git:ssh://github.com/taktx-io/TaktX-engine.git")
                    url.set("https://github.com/taktx-io/TaktX-engine")
                }
            }
        }
    }
}

// Maven Central requires signed artifacts
// This uses environment variables to avoid storing credentials in the repo
signing {
    val signingKey = System.getenv("GPG_PRIVATE_KEY")
    val signingPassword = System.getenv("GPG_PASSPHRASE")
    
    if (signingKey != null && signingPassword != null) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications["mavenJava"])
    }
}

spotless {
    java {
        target("src/**/*.java")
        targetExclude("${layout.buildDirectory}/**/*.java")
        googleJavaFormat()
    }
}