plugins {
    id("java-library")
    `maven-publish`
    alias(libs.plugins.xjc)
    alias(libs.plugins.spotless)
    alias(libs.plugins.protobuf)
    id("org.jreleaser")
    jacoco
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

val goldenTestSourceSet = sourceSets.create("goldenTest") {
    java.srcDir("src/goldenTest/java")
    resources.srcDir("src/test/resources")
    compileClasspath += sourceSets["main"].output + sourceSets["test"].output + sourceSets["test"].compileClasspath
    runtimeClasspath += output + sourceSets["main"].output + sourceSets["test"].output + sourceSets["test"].runtimeClasspath
}

configurations[goldenTestSourceSet.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
configurations[goldenTestSourceSet.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())

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
    api(libs.jjwt.api)
    api(libs.protobuf.javalite)
    runtimeOnly(libs.jjwt.impl)

    implementation(libs.kafka.clients)
    implementation(libs.cronutils)

    compileOnly(libs.lombok)
    compileOnly(libs.quarkus.core)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.jjwt.impl)
    testRuntimeOnly(libs.jjwt.jackson)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.reflections)
    testImplementation(libs.jaxb.runtime)

    annotationProcessor(libs.lombok)
}

// ── Protobuf ────────────────────────────────────────────────────────────────
protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}"
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                named("java") {
                    option("lite")
                }
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

val goldenTest by tasks.registering(Test::class) {
    description = "Runs golden protobuf compatibility tests"
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    useJUnitPlatform()
    testClassesDirs = goldenTestSourceSet.output.classesDirs
    classpath = goldenTestSourceSet.runtimeClasspath
    systemProperty("updateGoldens", System.getProperty("updateGoldens", "false"))
    shouldRunAfter(tasks.test)
}

val variableSizeBenchmark by tasks.registering(Test::class) {
    description = "Runs the VariableValue/VarMap size benchmark against saved legacy CBOR fixtures"
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    useJUnitPlatform()
    filter {
        includeTestsMatching("io.taktx.variables.VariablesEncodingBenchmarkTest")
        includeTestsMatching("io.taktx.serdes.ProtoPayloadSizeExplorationTest")
    }
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    shouldRunAfter(tasks.test)
    testLogging {
        showStandardStreams = true
    }
}

tasks.named("check") {
    dependsOn(goldenTest)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test, goldenTest)
    executionData(
        fileTree(layout.buildDirectory.dir("jacoco")) {
            include("test.exec", "goldenTest.exec")
        }
    )
    reports {
        xml.required = true
        html.required = true
    }
    // Exclude only low-value classes that add noise to coverage without hiding
    // runtime behavior:
    //  - dto/**             : passive DTOs/records/constants
    //  - bpmn/**            : XJC-generated classes from the BPMN XML Schema
    //  - proto/**           : protobuf-generated message/builders
    //  - security/*Exception: thin exception wrappers with no custom behavior
    //  - SigningKeyRegistrar/SigningKeysStore: Kafka I/O orchestration better exercised by
    //    integration tests than brittle unit tests
    //  - thin xml leaf mappers: small DTO-construction adapters selected by parser/factory tests;
    //    keep the higher-level flow/event mapping logic in coverage
    classDirectories.setFrom(
        fileTree(layout.buildDirectory.dir("classes/java/main")) {
            exclude(
                "io/taktx/dto/**",
                "io/taktx/bpmn/**",
                "io/taktx/proto/**",
                "io/taktx/security/*Exception.class",
                "io/taktx/security/SigningKeyRegistrar.class",
                "io/taktx/security/SigningKeysStore.class",
                "io/taktx/xml/GenericSignalMapper.class",
                "io/taktx/xml/GenericMessageEndEventMapper.class",
                "io/taktx/xml/GenericSendTaskMapper.class",
                "io/taktx/xml/GenericUserTaskMapper.class",
                "io/taktx/xml/GenericServiceTaskMapper.class",
                "io/taktx/xml/GenericErrorMapper.class",
                "io/taktx/xml/GenericScriptTaskMapper.class",
                "io/taktx/xml/GenericEscalationMapper.class",
                "io/taktx/xml/GenericBusinessRuleTaskMapper.class",
                "io/taktx/xml/GenericCallActivityMapper.class",
                "io/taktx/xml/GenericLoopCharacteristicsMapper.class",
                "io/taktx/xml/GenericReceiveTaskMapper.class",
                "io/taktx/xml/GenericMessageMapper.class",
                "io/taktx/xml/GenericMessageIntermediateThrowEventMapper.class",
                "io/taktx/xml/ZeebeMessageEndEventMapper.class",
                "io/taktx/xml/ZeebeBusinessRuleTaskMapper.class",
                "io/taktx/xml/ZeebeMessagekMapper.class",
                "io/taktx/xml/ZeebeServiceTaskMapper.class",
                "io/taktx/xml/ZeebeLoopCharacteristicsMapper.class",
                "io/taktx/xml/ZeebeSendTaskMapper.class",
                "io/taktx/xml/ZeebeCallActivityMapper.class",
                "io/taktx/xml/ZeebeUserTaskMapper.class",
                "io/taktx/xml/ZeebeMessageIntermediateThrowEventMapper.class"
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
                    retryDelay.set(30)
                    maxRetries.set(40)
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