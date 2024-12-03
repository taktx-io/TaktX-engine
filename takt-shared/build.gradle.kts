plugins {
    id("java")
    id("com.github.bjornvester.xjc") version "1.8.1"
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("com.cronutils:cron-utils:9.2.1")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.18.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.1")
    testImplementation("org.assertj:assertj-core:3.24.2")
    testImplementation("org.mockito:mockito-core:3.12.4")
    testImplementation("com.fasterxml.jackson.core:jackson-annotations:2.15.3")
    testImplementation("org.reflections:reflections:0.10.2")
    testImplementation("org.glassfish.jaxb:jaxb-runtime:3.0.2")

    compileOnly("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")
}


tasks.test {
    useJUnitPlatform()
}

xjc {
    markGenerated.set(true)
    defaultPackage.set("nl.qunit.bpmnmeister.bpmn")
}

tasks.jar {
    manifest {
        attributes(
                "Implementation-Title" to "TaktClient",
                "Implementation-Version" to version
        )
    }
}
