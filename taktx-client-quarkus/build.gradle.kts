plugins {
    id("java-library")
    `maven-publish`
    alias(libs.plugins.spotless)
    id("org.jreleaser")
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

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    // MicroProfile Config API (annotations + Config interface)
    compileOnlyApi("org.eclipse.microprofile.config:microprofile-config-api:3.1")

    // CDI/Jakarta APIs (no runtime impl here)
    compileOnlyApi("jakarta.inject:jakarta.inject-api:2.0.1")
    compileOnlyApi("jakarta.enterprise:jakarta.enterprise.cdi-api:4.1.0")
    compileOnlyApi("jakarta.annotation:jakarta.annotation-api:2.1.1")
    compileOnly("io.quarkus:quarkus-arc:3.15.1")

    implementation("io.quarkus:quarkus-arc")
    api(project(":taktx-client"))
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

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            // Maven Central requires POM metadata
            pom {
                name.set("TaktX Client Quarkus")
                description.set("Quarkus convenience beans for the TaktX plain Java client")
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
    release {
        github {
            name.set("TaktX-engine")
            commitAuthor {
                name.set("Eric Hendriks")
                email.set("info@taktx.io")
            }
        }
    }
}

spotless {
    java {
        googleJavaFormat()
    }
}

