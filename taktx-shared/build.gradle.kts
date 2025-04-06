plugins {
    id("java")
    alias(libs.plugins.xjc)
    alias(libs.plugins.spotless)
    `maven-publish`
}

group = "io.taktx"
version = "0.0.2-alpha6"

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

xjc {
    markGenerated.set(true)
    defaultPackage.set("io.taktx.bpmn")
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
        target("src/**/*.java")
        targetExclude("${layout.buildDirectory}/**/*.java")
        googleJavaFormat()
    }
}