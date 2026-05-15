plugins {
    id("java")
    id("idea")
    id("java-test-fixtures")
    alias(libs.plugins.spotless)
    alias(libs.plugins.quarkus)
    id("com.github.jk1.dependency-license-report") version "2.9"
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
    implementation(libs.jackson.cbor)
    implementation(libs.license3j)
    implementation(libs.quarkus.core)
    implementation(libs.quarkus.scheduler)
    implementation(libs.quarkus.smallrye.health)
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
    testImplementation(libs.testcontainersKafka)
    testImplementation(libs.testcontainersJunitJupiter)
    testImplementation(libs.awaitility)

    annotationProcessor(libs.lombok)
    annotationProcessor(libs.mapstruct.processor)

    // ── Test fixture infrastructure (shared by `test` and `securityIntegrationTest`) ──────────────
    // These classes live in src/testFixtures/java so that the securityIntegrationTest source set
    // can depend on them WITHOUT pulling in the compiled regular-test classes.  If both source sets
    // shared sourceSets["test"].output on their runtime classpath, Quarkus's test runner would
    // discover all @QuarkusTest classes from the regular test suite and execute them when a single
    // security-integration test is launched from the IDE.
    testFixturesImplementation(project(":taktx-shared"))
    testFixturesImplementation(project(":taktx-client"))
    testFixturesImplementation(enforcedPlatform(libs.quarkus.bom.get()))
    testFixturesImplementation(enforcedPlatform(libs.quarkus.camel.bom.get()))
    testFixturesImplementation(libs.quarkus.kafka.client)
    testFixturesImplementation(libs.quarkus.core)
    testFixturesImplementation(libs.quarkus.junit5)
    testFixturesImplementation(libs.assertj.core)
    testFixturesImplementation(libs.awaitility)
    testFixturesImplementation(libs.jackson.cbor)

    // Re-expose testFixtures to the regular test source set (was implicitly available before).
    testImplementation(testFixtures(project))
}

tasks.test {
    // Exclude the security-integration tests: they use a different Quarkus profile which forces
    // a Kafka dev-services restart.  Keeping them in the same JVM as the default-profile tests
    // causes the SingletonBpmnTestEngine's consumers to hammer the old (now-dead) broker with
    // reconnection errors for the rest of the run.  The dedicated securityIntegrationTest task
    // below runs them in a fresh JVM after this task completes, completely sidestepping the issue.
    useJUnitPlatform {
        excludeTags("security-integration")
    }
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
    finalizedBy(tasks.jacocoTestReport)
}

// ── Security integration test task ───────────────────────────────────────────────────────────────
// Runs SecurityIntegrationTest (and any future @Tag("security-integration") tests) in its own
// forked JVM, AFTER the main test task has finished and its JVM has exited.
// Because Gradle forks a separate process for every Test task, the SingletonBpmnTestEngine
// shutdown hook fires at the end of `test` and all consumers are cleanly closed before
// this task even starts — no stale connections, no log spam.

// Register the source set so the compiler picks up src/securityIntegrationTest/java/.
// Its compile/runtime classpath extends the main test source set so all test dependencies
// (Quarkus, Mockito, AssertJ, …) are available without repetition.
val securityIntegrationTestSourceSet = sourceSets.create("securityIntegrationTest") {
    java.srcDir("src/securityIntegrationTest/java")
    resources.srcDir("src/securityIntegrationTest/resources")
    // compileClasspath: test JARs + main output — intentionally excludes sourceSets["test"].output so
    // that the compiled regular unit-test classes are not on the classpath.  This prevents Quarkus's
    // test runner from discovering (and executing) unrelated unit tests when a security-integration
    // test is launched directly from the IDE.
    compileClasspath += sourceSets["test"].compileClasspath
    // runtimeClasspath: reconstruct what sourceSets["test"].runtimeClasspath normally provides but
    // without sourceSets["test"].output (= compiled regular test classes).
    runtimeClasspath += configurations["testRuntimeClasspath"] + sourceSets["main"].output + output
}

// Mark custom source set roots as test code for IDE/static-analysis tooling.
idea {
    module {
        testSources.from(file("src/securityIntegrationTest/java"))
        testResources.from(file("src/securityIntegrationTest/resources"))
    }
}

val securityIntegrationTest by tasks.registering(Test::class) {
    description = "Runs security-profile integration tests (separate Quarkus profile / JVM)"
    group = LifecycleBasePlugin.VERIFICATION_GROUP

    useJUnitPlatform {
        includeTags("security-integration")
    }

    // Use the dedicated source set's compiled output and runtime classpath.
    testClassesDirs = securityIntegrationTestSourceSet.output.classesDirs
    classpath = securityIntegrationTestSourceSet.runtimeClasspath

    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")

    // Must start only after the main test task is fully done (JVM exited, shutdown hooks run).
    mustRunAfter(tasks.test)

    // Gradle automatically writes JaCoCo exec data to build/jacoco/{taskName}.exec, so this task
    // produces build/jacoco/securityIntegrationTest.exec without any additional configuration.

    finalizedBy(tasks.jacocoTestReport)
}

// Make `check` (and therefore `build`) include the security integration tests.
tasks.named("check") {
    dependsOn(securityIntegrationTest)
}

tasks.jacocoTestReport {
    // NOTE: no `dependsOn(tasks.test)` here – that would pull the full unit-test suite into the
    // execution graph whenever `securityIntegrationTest` (or any other finalizing task) calls
    // `finalizedBy(tasks.jacocoTestReport)`.  Both `tasks.test` and `securityIntegrationTest`
    // already declare `finalizedBy(tasks.jacocoTestReport)`, so the report is always regenerated
    // after whichever test task ran.  The `doFirst` filter below safely skips exec files that
    // don't exist yet (e.g. when only security-integration tests have been executed).

    // Merge coverage from all engine test tasks:
    //  • jacoco/test.exec                    – standard Gradle JaCoCo (unit + default-profile tests)
    //  • jacoco/securityIntegrationTest.exec – standard Gradle JaCoCo (security-profile tests)
    //  • jacoco/quarkusIntTest.exec          – standard Gradle JaCoCo (Quarkus integration tests)
    executionData.setFrom(
        fileTree(project.layout.buildDirectory) {
            include("jacoco/test.exec")
            include("jacoco/securityIntegrationTest.exec")
            include("jacoco/quarkusIntTest.exec")
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
