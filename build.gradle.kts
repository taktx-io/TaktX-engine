plugins {
    id("java")
    id("com.google.cloud.tools.jib") version "3.4.4"
}

allprojects {
    repositories {
        mavenCentral()
    }
}