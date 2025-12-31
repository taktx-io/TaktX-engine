plugins {
    id("java")
    alias(libs.plugins.spotless)
    id("jacoco")
    id("org.jreleaser") version "1.21.0"
}

allprojects {
    group = "io.taktx"
    version = "0.0.11-alpha-6"

    repositories {
        mavenLocal()
        mavenCentral()
    }

    // Apply dependency constraints to all modules
    configurations.all {
        resolutionStrategy {
            // Force specific versions for common dependencies
            force(libs.jackson.annotations.get())
            force(libs.jackson.databind.get())
            force(libs.jackson.cbor.get())
            force(libs.cronutils.get())
        }
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

// Automatically generate badges after all JaCoCo reports are created
subprojects {
    plugins.withId("jacoco") {
        tasks.named("jacocoTestReport") {
            finalizedBy(rootProject.tasks.named("generateCoverageBadges"))
        }
    }
}

