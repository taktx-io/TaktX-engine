plugins {
    id("java")
    id("com.github.bjornvester.xjc") version "1.8.1"
    id("com.diffplug.spotless")
    `maven-publish`
}

group = "io.taktx"
version = "1.0.0-SNAPSHOT"

dependencies {
    implementation("com.cronutils:cron-utils:9.2.1")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.18.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-cbor:2.18.2")
    implementation("org.apache.kafka:kafka-clients:3.7.0")

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
    defaultPackage.set("io.taktx.bpmn")
}


publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            pom {
                name.set("My Library")
                description.set("A great library for ...")
                url.set("https://github.com/your-repo")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0")
                    }
                }

                developers {
                    developer {
                        id.set("your-id")
                        name.set("Your Name")
                        email.set("your-email@example.com")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/your-repo.git")
                    developerConnection.set("scm:git:ssh://github.com/your-repo.git")
                    url.set("https://github.com/your-repo")
                }
            }
        }
    }

    repositories {
        maven {
            name = "local"
            url = uri("file://~/.m2")
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