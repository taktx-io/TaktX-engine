plugins {
    id("java")
    id("io.quarkus") version "3.15.1"
    id("com.diffplug.spotless")
}

val quarkusPlatformGroupId: String by project
val quarkusPlatformArtifactId: String by project
val quarkusPlatformVersion: String by project

dependencies {
    implementation(enforcedPlatform("${quarkusPlatformGroupId}:quarkus-camel-bom:${quarkusPlatformVersion}"))
    implementation(enforcedPlatform("$quarkusPlatformGroupId:$quarkusPlatformArtifactId:$quarkusPlatformVersion"))
    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-jaxb")
    implementation("io.quarkus:quarkus-smallrye-openapi")
    implementation("io.quarkus:quarkus-resteasy-client")
    implementation("io.quarkus:quarkus-resteasy-jackson")
    implementation("io.quarkus:quarkus-jackson")
    implementation(project(":takt-client"))
    implementation(project(":takt-shared"))

    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.quarkus:quarkus-junit5-mockito")
    testImplementation("io.quarkus:quarkus-test-kafka-companion")

    compileOnly("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")
}

spotless {
    java {
        googleJavaFormat()
    }
}