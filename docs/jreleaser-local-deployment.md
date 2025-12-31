# Running jreleaserDeploy Locally

This guide explains how to run `jreleaserDeploy` locally to test the Maven Central deployment process before running it in the GitHub Actions pipeline.

## Prerequisites

### 1. Required Secrets/Environment Variables

Based on the GitHub Actions workflow, you need the following environment variables set:

```bash
# Maven Central credentials
export MAVEN_USERNAME="your-maven-central-username"
export MAVEN_PASSWORD="your-maven-central-password"

# GPG signing keys (same values for both jReleaser and GPG)
export JRELEASER_GPG_SECRET_KEY="your-gpg-private-key"
export JRELEASER_GPG_PASSPHRASE="your-gpg-passphrase"
export JRELEASER_GPG_PUBLIC_KEY="your-gpg-public-key"

# Maven Central credentials (duplicated for jReleaser)
export JRELEASER_MAVENCENTRAL_USERNAME="$MAVEN_USERNAME"
export JRELEASER_MAVENCENTRAL_PASSWORD="$MAVEN_PASSWORD"

# GPG credentials (duplicated)
export GPG_PRIVATE_KEY="$JRELEASER_GPG_SECRET_KEY"
export GPG_PASSPHRASE="$JRELEASER_GPG_PASSPHRASE"
```

### 2. Getting Your GPG Keys

If you don't have GPG keys set up yet:

#### Generate a new GPG key pair:
```bash
gpg --full-generate-key
```
Choose:
- RSA and RSA (default)
- 4096 bits
- Key does not expire (or set appropriate expiration)
- Enter your name and email (should match your Maven Central account)

#### Export your keys:

**Private Key (ASCII armored):**
```bash
gpg --armor --export-secret-keys YOUR_KEY_ID > private-key.asc
```

**Public Key (ASCII armored):**
```bash
gpg --armor --export YOUR_KEY_ID > public-key.asc
```

**Get your key ID:**
```bash
gpg --list-secret-keys --keyid-format=long
```

#### Upload public key to key servers:
```bash
gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID
gpg --keyserver keys.openpgp.org --send-keys YOUR_KEY_ID
```

### 3. Setting Up Environment Variables

Create a file named `.env.jreleaser` in your project root (this file is gitignored):

```bash
# .env.jreleaser
export MAVEN_USERNAME="your-username"
export MAVEN_PASSWORD="your-token"
export JRELEASER_GPG_PASSPHRASE="your-gpg-passphrase"

# For the GPG keys, you can either:
# Option 1: Reference the key files
export JRELEASER_GPG_SECRET_KEY="$(cat /path/to/private-key.asc)"
export JRELEASER_GPG_PUBLIC_KEY="$(cat /path/to/public-key.asc)"

# Option 2: Or paste the actual key content (wrapped in quotes)
# export JRELEASER_GPG_SECRET_KEY="-----BEGIN PGP PRIVATE KEY BLOCK-----
# ...
# -----END PGP PRIVATE KEY BLOCK-----"

# Duplicate for alternative variable names
export JRELEASER_MAVENCENTRAL_USERNAME="$MAVEN_USERNAME"
export JRELEASER_MAVENCENTRAL_PASSWORD="$MAVEN_PASSWORD"
export GPG_PRIVATE_KEY="$JRELEASER_GPG_SECRET_KEY"
export GPG_PASSPHRASE="$JRELEASER_GPG_PASSPHRASE"
```

**Important:** Add `.env.jreleaser` to your `.gitignore` file to prevent committing secrets.

## Running the Deployment Locally

### Step 1: Source the environment variables
```bash
source .env.jreleaser
```

### Step 2: Build and publish artifacts to local staging
```bash
./gradlew clean build publishMavenJavaPublicationToMavenRepository
```

This will:
- Build all modules
- Run tests
- Publish artifacts to `build/staging-deploy` directory

### Step 3: Verify the staged artifacts
```bash
ls -la build/staging-deploy/
```

You should see your artifacts organized by group/artifact/version.

### Step 4: Deploy to Maven Central (DRY RUN first)
```bash
./gradlew jreleaserConfig -Djreleaser.dry.run=true
```

This will show you the configuration without actually deploying.

### Step 5: Deploy to Maven Central
```bash
./gradlew jreleaserDeploy
```

Or deploy specific modules:
```bash
./gradlew :taktx-shared:jreleaserDeploy
./gradlew :taktx-client:jreleaserDeploy
./gradlew :taktx-client-spring:jreleaserDeploy
./gradlew :taktx-client-quarkus:jreleaserDeploy
```

## Testing Snapshot vs Release Deployment

### For Snapshot Deployment
Ensure your version in `build.gradle.kts` ends with `-SNAPSHOT`:
```kotlin
version = "0.0.11-alpha-5-SNAPSHOT"
```

This will deploy to the Nexus2 snapshot repository.

### For Release Deployment
Ensure your version in `build.gradle.kts` does NOT end with `-SNAPSHOT`:
```kotlin
version = "0.0.11-alpha-5"
```

This will deploy to Maven Central via the new Central Portal API.

## Troubleshooting

### Issue: `org/eclipse/jgit/lib/GpgObjectSigner` error

This is the classloader conflict we've been fixing. Make sure you have the latest changes with the `build-logic-jreleaser` separate build.

**Solution:**
```bash
./gradlew --stop
./gradlew clean jreleaserConfig
```

### Issue: GPG signing fails

**Check your GPG setup:**
```bash
gpg --list-secret-keys
```

**Test GPG signing manually:**
```bash
echo "test" | gpg --armor --sign
```

### Issue: Authentication fails

**Verify your credentials:**
- For Maven Central Portal: Use your portal token (not your Sonatype OSSRH password)
- Get token from: https://central.sonatype.com/account

### Issue: "Repository not found" or "Unauthorized"

Make sure:
1. You've verified your namespace on Maven Central Portal
2. Your group ID matches your verified namespace (e.g., `io.taktx`)
3. Your credentials are correct

## Understanding the Deployment Process

### What happens during `jreleaserDeploy`:

1. **Signing Phase:**
   - jReleaser signs all artifacts (JARs, POMs, etc.) with your GPG key
   - Creates `.asc` signature files

2. **Deployment Phase:**
   - For RELEASE versions: Uploads to Maven Central Portal (https://central.sonatype.com)
   - For SNAPSHOT versions: Uploads to Nexus2 snapshot repository (https://s01.oss.sonatype.org)

3. **Verification Phase:**
   - Maven Central validates:
     - All required files present (JAR, POM, sources, javadoc)
     - All files are signed
     - POM contains required metadata
     - Signatures are valid

## Useful Commands

### Check jReleaser configuration:
```bash
./gradlew jreleaserConfig
```

### List all jReleaser tasks:
```bash
./gradlew tasks --group jreleaser
```

### Run with debug output:
```bash
./gradlew jreleaserDeploy --info
```

### Check what will be deployed:
```bash
./gradlew jreleaserConfig -Djreleaser.dry.run=true
```

## Files Generated

After running `jreleaserDeploy`, check these locations:

- **Staged artifacts:** `build/staging-deploy/`
- **jReleaser output:** `build/jreleaser/`
- **Logs:** `build/jreleaser/trace.log`
- **Deployment info:** `build/jreleaser/deploy/`

## Security Best Practices

1. **Never commit secrets** - Use environment variables or a secrets file that's gitignored
2. **Use tokens instead of passwords** - Maven Central Portal provides API tokens
3. **Rotate credentials regularly** - Especially if they're exposed
4. **Test with dry-run first** - Always use `-Djreleaser.dry.run=true` before actual deployment
5. **Keep GPG keys secure** - Store private keys in a secure location (e.g., password manager, encrypted volume)

## References

- [jReleaser Documentation](https://jreleaser.org/)
- [Maven Central Portal](https://central.sonatype.com/)
- [GPG Signing Guide](https://central.sonatype.org/publish/requirements/gpg/)

