plugins {
    id("java-library")
    `maven-publish`
    alias(libs.plugins.xjc)
    alias(libs.plugins.spotless)
    id("org.jreleaser")
    jacoco
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

dependencies {
    api(libs.jackson.databind)
    api(libs.jjwt.api)
    implementation(libs.jjwt.impl)
    implementation(libs.jjwt.jackson)

    implementation(libs.jackson.cbor)
    implementation(libs.jackson.datatype.jsr310)
    implementation(libs.kafka.clients)
    implementation(libs.cronutils)

    compileOnly(libs.lombok)
    compileOnly(libs.quarkus.core)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
//    testImplementation(libs.jackson.annotations)
    testImplementation(libs.reflections)
    testImplementation(libs.jaxb.runtime)

    annotationProcessor(libs.lombok)
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
    // Exclude classes that carry no testable business logic and would distort the
    // coverage metric:
    //  - dto/**          : pure Lombok data-transfer objects (generated getters/equals/hashCode)
    //  - bpmn/**         : XJC-generated classes from the BPMN XML Schema
    //  - *TypeIdResolver : Jackson polymorphism configuration wiring (no conditional logic)
    //  - xml/Generic*    : Generic BPMN element mappers — exercised by engine integration tests
    //  - xml/Zeebe*      : Zeebe-specific BPMN mappers  — exercised by engine integration tests
    //  - xml/BpmnMapper* : Mapper interface + factory wiring
    classDirectories.setFrom(
        fileTree(layout.buildDirectory.dir("classes/java/main")) {
            exclude(
                "io/taktx/dto/**",
                "io/taktx/bpmn/**",
                "**/*TypeIdResolver.class",
                "**/xml/Generic*.class",
                "**/xml/Zeebe*.class",
                "**/xml/BpmnMapper.class",
                "**/xml/BpmnMapperFactory.class"
            )
        }
    )
}

// Configure javadoc to work with Lombok
tasks.javadoc {
    options {
        this as StandardJavadocDocletOptions
        addStringOption("Xdoclint:none", "-quiet")
        addBooleanOption("html5", true)
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
                description.set("Shared library for TaktX BPM Engine.")
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
        name.set("taktx-shared")
        description.set("TaktX Shared Library")
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
            // NOTE: Legacy OSSRH (s01.oss.sonatype.org) was decommissioned in 2024.
            // Snapshot publishing is not currently configured.
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