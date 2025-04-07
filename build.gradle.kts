plugins {
    id("java")
    alias(libs.plugins.jib)
    alias(libs.plugins.spotless)
    id("io.github.gradle-nexus.publish-plugin") version "1.3.0"
}

allprojects {
    group = "io.taktx"
    version = "0.0.2-alpha6"
    
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

// Maven Central publishing configuration
nexusPublishing {
    repositories {
        sonatype {
            // The default is 's01.oss.sonatype.org' which is the newer instance
            nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
            snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
            
            // These credentials will be used by the publishToSonatype task
            username.set(System.getenv("MAVEN_USERNAME"))
            password.set(System.getenv("MAVEN_PASSWORD"))
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
