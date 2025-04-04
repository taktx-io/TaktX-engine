# TaktX Release Process

This document outlines the release process for TaktX Engine and Client, following industry best practices.

## 1. Version Management Strategy

### 1.1 Git as the Source of Truth

We use Git tags as the single source of truth for versioning:

- Each release is tagged in Git (e.g., `v1.0.0`)
- The build system extracts version information directly from Git
- No need for maintaining separate version files that can become out of sync

### 1.2 Semantic Versioning

We follow [Semantic Versioning 2.0.0](https://semver.org/):

| Version Component | Meaning |
|-------------------|---------|
| MAJOR (X.0.0) | Incompatible API changes |
| MINOR (0.X.0) | New features (backwards compatible) |
| PATCH (0.0.X) | Bug fixes (backwards compatible) |

Example versions:
- `1.0.0` - Initial stable release
- `1.1.0` - New features added
- `1.0.1` - Bug fixes only
- `2.0.0` - Breaking changes introduced

### 1.3 Pre-release Versions

For pre-release versions, we append suffixes:
- Alpha: `1.0.0-alpha.1`
- Beta: `1.0.0-beta.1`
- Release Candidate: `1.0.0-rc.1`

## 2. Build System Configuration

### 2.1 Gradle Version Configuration

Update `build.gradle.kts` to extract version from Git:

```kotlin
// Helper function to run shell commands
fun String.runCommand(): String? {
    return try {
        val parts = this.split("\\s".toRegex())
        val process = ProcessBuilder(*parts.toTypedArray())
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start()
        
        process.waitFor(10, TimeUnit.SECONDS)
        process.inputStream.bufferedReader().readText().trim()
    } catch (e: Exception) {
        null
    }
}

// Extract version from Git tag
version = provider { 
    val gitTag = "git describe --tags --abbrev=0".runCommand()
    
    // If no tag found or in detached state - use fallback
    if (gitTag.isNullOrEmpty()) {
        return@provider "0.0.1-SNAPSHOT"
    }
    
    // Remove 'v' prefix if present
    gitTag.removePrefix("v")
}
```

### 2.2 Maven Publish Configuration

Configuration for Maven Central publishing:

```kotlin
plugins {
    // Other plugins...
    id("maven-publish")
    id("signing")
    id("io.github.gradle-nexus.publish-plugin") version "1.1.0"
}

// Maven Central publishing configuration
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            
            pom {
                name.set("TaktX Client")
                description.set("Java client for TaktX Engine")
                url.set("https://github.com/taktx/TaktX-engine")
                
                licenses {
                    license {
                        name.set("TaktX Business Source License 1.1")
                        url.set("https://taktx.io/license")
                    }
                }
                
                developers {
                    developer {
                        id.set("taktx")
                        name.set("TaktX Team")
                        email.set("info@taktx.io")
                    }
                }
                
                scm {
                    url.set("https://github.com/taktx/TaktX-engine")
                    connection.set("scm:git:git://github.com/taktx/TaktX-engine.git")
                    developerConnection.set("scm:git:ssh://git@github.com:taktx/TaktX-engine.git")
                }
            }
        }
    }
}

signing {
    val signingKey: String? by project
    val signingPassword: String? by project
    useInMemoryPgpKeys(signingKey, signingPassword)
    sign(publishing.publications["maven"])
}

nexusPublishing {
    repositories {
        sonatype {
            nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
            snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
        }
    }
}
```

## 3. CI/CD Workflows

### 3.1 Continuous Integration Workflow

File: `.github/workflows/ci.yml`

```yaml
name: Continuous Integration

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main, develop ]

jobs:
  build-and-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
        with:
          fetch-depth: 0  # Fetch all history for Git-based versioning
      
      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: gradle
      
      - name: Build and test
        run: ./gradlew build test
      
      - name: Dependency check
        uses: dependency-check/Dependency-Check_Action@main
        with:
          project: 'TaktX'
          path: '.'
          format: 'HTML'
          
      - name: Upload test reports
        if: always()
        uses: actions/upload-artifact@v3
        with:
          name: test-reports
          path: '**/build/reports/tests/'
```

### 3.2 Release Workflow

File: `.github/workflows/release.yml`

```yaml
name: Release

on:
  release:
    types: [ published ]

jobs:
  build-and-publish:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
        with:
          fetch-depth: 0  # Fetch all history for Git-based versioning
      
      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: gradle
      
      - name: Extract version
        id: extract_version
        run: echo "VERSION=${GITHUB_REF#refs/tags/v}" >> $GITHUB_OUTPUT
      
      - name: Generate license change date
        run: |
          RELEASE_DATE=$(date +%Y-%m-%d)
          CHANGE_DATE=$(date -d "$RELEASE_DATE +4 years" +%Y-%m-%d)
          echo "This version will transition to Apache License 2.0 on $CHANGE_DATE" > LICENSE_CHANGE_DATE.txt
          echo "CHANGE_DATE=$CHANGE_DATE" >> $GITHUB_ENV
        
      - name: Build with Gradle
        run: ./gradlew build
      
      - name: Build Docker image
        run: |
          docker build \
            --build-arg VERSION=${{ steps.extract_version.outputs.VERSION }} \
            --build-arg CHANGE_DATE=${{ env.CHANGE_DATE }} \
            -t ghcr.io/${{ github.repository_owner }}/taktx-engine:${{ steps.extract_version.outputs.VERSION }} \
            -t ghcr.io/${{ github.repository_owner }}/taktx-engine:latest \
            .
      
      - name: Login to GitHub Container Registry
        uses: docker/login-action@v2
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      
      - name: Push Docker image
        run: |
          docker push ghcr.io/${{ github.repository_owner }}/taktx-engine:${{ steps.extract_version.outputs.VERSION }}
          docker push ghcr.io/${{ github.repository_owner }}/taktx-engine:latest
      
      - name: Publish to Maven Central
        run: ./gradlew publishToSonatype closeAndReleaseSonatypeStagingRepository
        env:
          MAVEN_USERNAME: ${{ secrets.MAVEN_USERNAME }}
          MAVEN_PASSWORD: ${{ secrets.MAVEN_PASSWORD }}
          SIGNING_KEY: ${{ secrets.GPG_PRIVATE_KEY }}
          SIGNING_PASSWORD: ${{ secrets.GPG_PASSPHRASE }}
```

## 4. License Handling

Since TaktX uses the Business Source License with a 4-year Change Date, we need to handle this during releases:

### 4.1 Dockerfile Updates

The Dockerfile should include build arguments for version and change date:

```dockerfile
FROM eclipse-temurin:17-jre-alpine

ARG VERSION=0.0.1
ARG CHANGE_DATE=2029-04-04

LABEL maintainer="TaktX B.V. <info@taktx.io>"
LABEL version="${VERSION}"
LABEL description="TaktX Engine - A high-performance BPMN engine"
LABEL license="TaktX Business Source License 1.1"
LABEL license.change_date="${CHANGE_DATE}"

WORKDIR /app

COPY takt-engine/build/libs/takt-engine-*.jar /app/takt-engine.jar
COPY LICENSE.md /app/LICENSE.md
COPY LICENSE_CHANGE_DATE.txt /app/LICENSE_CHANGE_DATE.txt

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/takt-engine.jar"]
```

### 4.2 LicenseInfoConfig Updates

Update the `LicenseInfoConfig.java` to read version from the JAR manifest:

```java
String version = getClass().getPackage().getImplementationVersion();
if (version == null) {
    version = "DEVELOPMENT";
}

// For change date, read from a resource or calculate based on build timestamp
String changeDate = "unknown";
try (InputStream is = getClass().getClassLoader().getResourceAsStream("META-INF/LICENSE_CHANGE_DATE.txt")) {
    if (is != null) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            changeDate = reader.readLine();
        }
    }
} catch (Exception e) {
    log.warn("Could not read license change date", e);
}
```

## 5. Release Process

### 5.1 Development Workflow

1. Develop features in feature branches
2. Create PRs to merge to `develop` branch
3. CI pipeline validates all PRs
4. When ready for release, merge `develop` to `main`

### 5.2 Creating a Release

1. Go to GitHub repository's "Releases" section
2. Click "Draft a new release"
3. Enter a tag following SemVer (e.g., `v1.0.0`)
4. Write detailed release notes
5. Publish the release

### 5.3 Automated Release Actions

The release workflow automatically:

1. Builds the project with the correct version
2. Calculates the license Change Date (4 years from release)
3. Creates and pushes the Docker image to GitHub Packages
4. Publishes the client library to Maven Central

### 5.4 Post-Release

After release:

1. Create a new version branch for subsequent development
2. Update the `CHANGELOG.md` with a new unreleased section
3. Start the next development cycle

## 6. Required GitHub Secrets

Set these secrets in the repository:

| Secret Name | Purpose |
|-------------|---------|
| `MAVEN_USERNAME` | Sonatype OSS username |
| `MAVEN_PASSWORD` | Sonatype OSS password |
| `GPG_PRIVATE_KEY` | GPG key for signing artifacts |
| `GPG_PASSPHRASE` | Passphrase for GPG key |

## 7. Verification Steps

After each release, verify:

1. Docker image is available on GitHub Container Registry
2. Client library is published to Maven Central
3. GitHub Release contains correct artifacts
4. License Change Date is correctly set in all artifacts
