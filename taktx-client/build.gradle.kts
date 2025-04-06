plugins {
    id("java")
    id("maven-publish")
    alias(libs.plugins.spotless)
}

group = "io.taktx"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
    mavenLocal()
}

val quarkusPlatformGroupId: String by project
val quarkusPlatformArtifactId: String by project
val quarkusPlatformVersion: String by project

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

    testImplementation(libs.assertj.core)
}

// Adds dependency locking to ensure reproducible builds
dependencyLocking {
    lockAllConfigurations()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            pom {
                name.set("My Library")
                description.set("A great library for ...")
                url.set("https://github.com/your-repo")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0")
                    }
                }

                developers {
                    developer {
                        id.set("your-id")
                        name.set("Your Name")
                        email.set("your-email@example.com")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/your-repo.git")
                    developerConnection.set("scm:git:ssh://github.com/your-repo.git")
                    url.set("https://github.com/your-repo")
                }
            }
        }
    }

    repositories {
        maven {
            name = "local"
            url = uri("file://~/.m2")
        }
    }
}

spotless {
    java {
        googleJavaFormat()
    }
}