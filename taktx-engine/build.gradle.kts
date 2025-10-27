plugins {
    id("java")
    alias(libs.plugins.spotless)
    alias(libs.plugins.quarkus)
    alias(libs.plugins.jib)
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
    implementation(libs.mapstruct)

    compileOnly(libs.lombok)

    testImplementation(project(":taktx-client"))
    testImplementation(libs.quarkus.jaxb)
    testImplementation(libs.quarkus.junit5)
    testImplementation(libs.quarkus.junit5.mockito)
    testImplementation(libs.quarkus.jacoco)
    testImplementation(libs.assertj.core)
    testImplementation(libs.testcontainers.kafka)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.awaitility)

    annotationProcessor(libs.lombok)
    annotationProcessor(libs.mapstruct.processor)
}

group = "io.taktx"
version = "0.0.8-alpha-3"

tasks.test {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
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

tasks.withType<JacocoReport> {
    executionData.setFrom(files(
        "${layout.buildDirectory}/jacoco/test.exec",
        "${layout.buildDirectory}/jacoco-quarkus.exec"
    ))
}