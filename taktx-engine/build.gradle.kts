plugins {
    id("java")
    alias(libs.plugins.spotless)
    alias(libs.plugins.quarkus)
    id("com.github.jk1.dependency-license-report") version "2.0"
    jacoco
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

dependencies {
    implementation(project(":taktx-shared"))
    implementation(enforcedPlatform(libs.quarkus.camel.bom.get()))
    implementation(enforcedPlatform(libs.quarkus.bom.get()))
    implementation(libs.quarkus.kafka.streams)
    implementation(libs.quarkus.jaxb)
    implementation(libs.quarkus.resteasy.client)
    implementation(libs.jackson.cbor)
    implementation(libs.license3j)
    implementation(libs.quarkus.core)
    implementation(libs.quarkus.scheduler)
    implementation(libs.quarkus.smallrye.openapi)
    implementation(libs.quarkus.swagger.ui)
    implementation(libs.quarkus.resteasy.jackson)
    implementation(libs.quarkus.jackson)
    implementation(libs.quarkus.micrometer)
    implementation(libs.quarkus.micrometer.registry.prometheus)
    implementation(libs.quarkus.kafka.client)
    implementation(libs.camunda.feel)
    implementation(libs.cronutils)
    implementation(libs.caffeine)
    implementation(libs.mapstruct)

    compileOnly(libs.lombok)

    testImplementation(project(":taktx-client"))
    testImplementation(libs.quarkus.jaxb)
    testImplementation(libs.quarkus.junit5)
    testImplementation(libs.quarkus.junit5.mockito)
    testImplementation(libs.quarkus.jacoco)
    testImplementation(libs.assertj.core)
    testImplementation(libs.kafka.streams.test.utils)
//    testImplementation(libs.testcontainers.kafka)
//    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.awaitility)

    annotationProcessor(libs.lombok)
    annotationProcessor(libs.mapstruct.processor)
}

tasks.test {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)

    // Merge both standard test coverage and Quarkus integration test coverage
    executionData.setFrom(
        fileTree(project.layout.buildDirectory) {
            include("jacoco/test.exec")
            include("jacoco-quarkus.exec")
        }
    )

    reports {
        xml.required = true
        html.required = true
    }

    doFirst {
        val execFiles = executionData.files.filter { it.exists() }
        logger.lifecycle("JaCoCo merging coverage from: ${execFiles.joinToString(", ") { it.name }}")
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

// Ensure the produced JARs include useful manifest attributes (Implementation-Version etc.)
tasks.withType<Jar>().configureEach {
    manifest {
        attributes(mapOf(
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version.toString(),
            "Implementation-Vendor" to project.group.toString(),
            "Built-By" to System.getProperty("user.name"),
            "Built-Date" to System.currentTimeMillis().toString()
        ))
    }
}


// Configure dependency license report
licenseReport {
    outputDir = "${layout.buildDirectory.get()}/reports/dependency-license"
    renderers = arrayOf(
        com.github.jk1.license.render.JsonReportRenderer(),
        com.github.jk1.license.render.CsvReportRenderer()
    )
    configurations = arrayOf("runtimeClasspath")
}

