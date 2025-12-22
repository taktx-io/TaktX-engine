plugins {
    id("java-library")
    `maven-publish`
    alias(libs.plugins.spotless)
    id("org.jreleaser")
    id("com.github.jk1.dependency-license-report") version "2.0"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks {
    withType<JavaCompile>().configureEach {
        options.release = 21
    }

    withType<Javadoc>().configureEach {
        with(options as StandardJavadocDocletOptions) {
            addStringOption("-release", "21")
        }
    }

    withType<Test>().configureEach {
        javaLauncher.set(project.javaToolchains.launcherFor {
            languageVersion = JavaLanguageVersion.of(21)
        })
    }
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    // Spring Framework dependencies
    compileOnly("org.springframework:spring-context:6.2.1")
    compileOnly("org.springframework:spring-beans:6.2.1")
    compileOnly("org.springframework.boot:spring-boot:3.4.1")
    compileOnly("org.springframework.boot:spring-boot-autoconfigure:3.4.1")
    compileOnlyApi(libs.jakarta.inject.api)
    compileOnlyApi(libs.jakarta.annotation.api)

    api(project(":taktx-client"))

    // Test dependencies
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation("org.springframework:spring-context:6.2.1")
    testImplementation("org.springframework:spring-beans:6.2.1")
    testImplementation("org.springframework:spring-test:6.2.1")
    testImplementation("org.springframework.boot:spring-boot:3.4.1")
    testImplementation("org.springframework.boot:spring-boot-autoconfigure:3.4.1")
    testImplementation(libs.kafka.clients)
}

// These are required for Maven Central
java {
    withJavadocJar()
    withSourcesJar()
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
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
                name.set("TaktX Client Spring")
                description.set("Spring convenience beans for the TaktX plain Java client")
                url.set("https://github.com/taktx-io/TaktX-engine")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0")
                    }
                }

                developers {
                    developer {
                        id.set("taktx-io")
                        name.set("TaktX Team")
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

