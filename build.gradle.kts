plugins {
    id("java")
    id("com.google.cloud.tools.jib") version "3.4.4"
    id("com.diffplug.spotless") version "6.25.0"
}

allprojects {
    repositories {
        mavenLocal()
        mavenCentral()
    }
}

spotless {
    java {
        googleJavaFormat()
    }
}