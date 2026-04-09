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
    // Spring Framework 7 / Spring Boot 4 dependencies
    compileOnly(libs.springContext7)
    compileOnly(libs.springBeans7)
    compileOnly(libs.springBoot4)
    compileOnly(libs.springBootAutoconfigure4)
    compileOnlyApi(libs.jakarta.inject.api)
    compileOnlyApi(libs.jakarta.annotation.api)

    api(project(":taktx-client"))

    // Test dependencies
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.springContext7)
    testImplementation(libs.springBeans7)
    testImplementation(libs.springTest7)
    testImplementation(libs.springBoot4)
    testImplementation(libs.springBootAutoconfigure4)
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
                name.set("TaktX Client Spring Boot 4")
                description.set("Spring Boot 4 convenience beans for the TaktX plain Java client")
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
                        name.set("Eric Hendriks")
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
    repositories {
        maven {
            url = uri(layout.buildDirectory.dir("staging-deploy").get().asFile)
        }
    }
}

jreleaser {
    gitRootSearch.set(true)
    project {
        name.set("taktx-client-spring-boot-4")
        description.set("TaktX Spring Boot 4 Client Library")
        authors.set(listOf("Eric Hendriks"))
        license.set("Apache-2.0")
        inceptionYear.set("2025")
        links {
            homepage.set("https://www.taktx.io")
        }
    }
    signing {
        active.set(org.jreleaser.model.Active.ALWAYS)
        armored.set(true)
    }
    deploy {
        maven {
            mavenCentral {
                register("release-deploy") {
                    active.set(org.jreleaser.model.Active.RELEASE)
                    url.set("https://central.sonatype.com/api/v1/publisher")
                    stagingRepository("build/staging-deploy")
                }
            }
            nexus2 {
                register("snapshot-deploy") {
                    active.set(org.jreleaser.model.Active.SNAPSHOT)
                    url.set("https://s01.oss.sonatype.org/service/local/")
                    snapshotUrl.set("https://s01.oss.sonatype.org/content/repositories/snapshots/")
                    applyMavenCentralRules.set(true)
                    snapshotSupported.set(true)
                    closeRepository.set(true)
                    releaseRepository.set(true)
                    stagingRepository("build/staging-deploy")
                }
            }
        }
    }
}

spotless {
    java {
        googleJavaFormat()
    }
}

