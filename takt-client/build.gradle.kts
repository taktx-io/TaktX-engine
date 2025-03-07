plugins {
    id("java")
    id("maven-publish")
    id("com.diffplug.spotless")
}

group = "nl.flomaestro"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
    mavenLocal()
}

val quarkusPlatformGroupId: String by project
val quarkusPlatformArtifactId: String by project
val quarkusPlatformVersion: String by project

dependencies {
    implementation(project(":takt-shared"))
    implementation("org.slf4j:slf4j-api:2.0.16")
    implementation("org.slf4j:slf4j-simple:2.0.16")
    implementation("org.apache.kafka:kafka-clients:3.9.0")
    implementation("io.github.classgraph:classgraph:4.8.179")
    implementation("com.fasterxml.jackson.core:jackson-core:2.18.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.18.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-cbor:2.18.2")
    implementation("org.awaitility:awaitility:4.2.1")

    compileOnly("jakarta.enterprise:jakarta.enterprise.cdi-api:4.1.0")

    compileOnly("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")

    testImplementation("org.assertj:assertj-core:3.24.2")
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