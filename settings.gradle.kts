pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "taktx"

include("taktx-shared", "taktx-client", "taktx-engine", "taktx-client-quarkus", "taktx-client-spring")
