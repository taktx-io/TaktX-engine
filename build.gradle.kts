plugins {
    id("java")
    alias(libs.plugins.jib)
    alias(libs.plugins.spotless)
}

allprojects {
    repositories {
        mavenLocal()
        mavenCentral()
    }
    
    // Apply dependency constraints to all modules
    configurations.all {
        resolutionStrategy {
            // Force specific versions for common dependencies
            force(libs.jackson.annotations.get())
            force(libs.jackson.databind.get())
            force(libs.jackson.cbor.get())
            force(libs.cronutils.get())
        }
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
version = "0.0.2-alpha6"
