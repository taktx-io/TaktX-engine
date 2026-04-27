plugins {
    id("java")
    alias(libs.plugins.spotless)
    id("jacoco")
    id("org.jreleaser") version "1.23.0"
}

allprojects {
    group = "io.taktx"
    version = "0.5.1-beta"

    repositories {
        mavenLocal()
        mavenCentral()
    }
}

subprojects {
    // Apply JaCoCo to all subprojects
    plugins.withId("java") {
        // Apply the JaCoCo plugin to each subproject with the java plugin
        apply(plugin = "jacoco")

        // Configure JaCoCo for all subprojects
        configure<JacocoPluginExtension> {
            toolVersion = "0.8.14" // Use a version compatible with Java 23
        }

        // Configure test task to generate coverage data
        tasks.withType<Test> {
            finalizedBy(tasks.named("jacocoTestReport"))
        }

        // Configure the JaCoCo report task
        tasks.withType<JacocoReport> {
            reports {
                xml.required.set(true)
                html.required.set(true)
            }

            // Exclude specified packages from all modules
            classDirectories.setFrom(
                files(classDirectories.files.map {
                    fileTree(it) {
                        // User-specified exclusions
                        exclude("io/taktx/dto/**")
                        exclude("io/taktx/bpmn/**")

                        // JDK and runtime libraries that cause issues with JaCoCo
                        exclude("sun/util/resources/**")
                        exclude("jdk/internal/**")
                        exclude("java/**")
                        exclude("javax/**")
                        exclude("sun/**")
                        exclude("com/sun/**")
                        exclude("**/*_.class") // Lombok generated classes
                        exclude("**/*$*.class") // Inner classes
                    }
                })
            )
        }
    }
}

spotless {
    java {
        googleJavaFormat()
        licenseHeader("""
            /*
             * TaktX - A high-performance BPMN engine
             * Copyright (c) 2025 Eric Hendriks
             * Licensed under the Apache License, Version 2.0 (the "License");
             * you may not use this file except in compliance with the License.
             * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
             */
            """.trimIndent())
    }
}

// Adds dependency locking to ensure reproducible builds
dependencyLocking {
    lockAllConfigurations()
}

// Task to generate coverage badges from JaCoCo reports
tasks.register<Exec>("generateCoverageBadges") {
    group = "verification"
    description = "Generate coverage badges from JaCoCo XML reports"

    commandLine("python3", "${projectDir}/scripts/generate_coverage_badges.py")

    doFirst {
        println("🎨 Generating coverage badges from JaCoCo reports...")
    }

    doLast {
        println("✅ Coverage badges updated in badges/ directory")
    }
}

val allSubprojectChecks = subprojects.map { "${it.path}:check" }

val allJacocoReports = subprojects.map { "${it.path}:jacocoTestReport" }

val runAllTests by tasks.registering {
    group = "verification"
    description = "Runs all module checks plus engine Quarkus integration tests"
    dependsOn(allSubprojectChecks)
    dependsOn(":taktx-engine:quarkusIntTest")
}

val refreshCoverageReports by tasks.registering {
    group = "verification"
    description = "Regenerates all JaCoCo XML reports after tests"
    dependsOn(allJacocoReports)
    mustRunAfter(runAllTests)
}

tasks.named("generateCoverageBadges") {
    mustRunAfter(refreshCoverageReports)
}

tasks.register("testWithBadges") {
    group = "verification"
    description = "Runs the full test suite and updates coverage badges from fresh reports"
    dependsOn(runAllTests)
    dependsOn(refreshCoverageReports)
    dependsOn(tasks.named("generateCoverageBadges"))
}

