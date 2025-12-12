# Codecov Configuration

This project uses [Codecov](https://codecov.io/) for test coverage reporting and tracking.

## Setup

### 1. Repository Configuration

The project is configured to upload coverage reports for all modules:
- **taktx-engine** (BSL-licensed engine)
- **taktx-client** (Apache 2.0 licensed SDK)
- **taktx-client-quarkus** (Apache 2.0 licensed SDK)
- **taktx-shared** (Apache 2.0 licensed SDK)

### 2. Required Secret

Add the `CODECOV_TOKEN` secret to your GitHub repository:
1. Go to [Codecov](https://codecov.io/) and sign in with your GitHub account
2. Add your repository to Codecov
3. Copy the upload token from Codecov
4. In your GitHub repository, go to Settings → Secrets and variables → Actions
5. Create a new repository secret named `CODECOV_TOKEN` with the token value

### 3. Coverage Reports

Coverage reports are automatically generated and uploaded on every push to main, feature branches, and pull requests.

#### Location of JaCoCo XML Reports:
- `taktx-engine/build/reports/jacoco/test/jacocoTestReport.xml`
- `taktx-client/build/reports/jacoco/test/jacocoTestReport.xml`
- `taktx-client-quarkus/build/reports/jacoco/test/jacocoTestReport.xml`
- `taktx-shared/build/reports/jacoco/test/jacocoTestReport.xml`

#### Location of JaCoCo HTML Reports (for local viewing):
- `taktx-engine/build/reports/jacoco/test/html/index.html`
- `taktx-client/build/reports/jacoco/test/html/index.html`
- `taktx-client-quarkus/build/reports/jacoco/test/html/index.html`
- `taktx-shared/build/reports/jacoco/test/html/index.html`

## Local Testing

To generate coverage reports locally:

```bash
# Generate coverage for all modules
./gradlew test jacocoTestReport

# Generate coverage for specific module
./gradlew :taktx-engine:test :taktx-engine:jacocoTestReport
./gradlew :taktx-client:test :taktx-client:jacocoTestReport
./gradlew :taktx-client-quarkus:test :taktx-client-quarkus:jacocoTestReport
./gradlew :taktx-shared:test :taktx-shared:jacocoTestReport
```

## Coverage Targets

The project aims for the following coverage targets:
- **Project Overall**: 70% minimum
- **Per Module**: 70% minimum
- **Patch Coverage**: 70% minimum (for new code in PRs)

## Codecov Features

### Flags
Each module uploads coverage with a specific flag to track coverage independently:
- `taktx-engine`
- `taktx-client`
- `taktx-client-quarkus`
- `taktx-shared`

### Components
Coverage is tracked per component, allowing you to see coverage trends for each module independently.

### PR Comments
Codecov will automatically comment on pull requests with:
- Overall coverage change
- Coverage diff (lines added/removed with/without coverage)
- Component-level coverage changes
- Flag-level coverage changes

## Configuration File

The Codecov configuration is defined in `codecov.yml` at the project root. Key settings:

- **Precision**: 2 decimal places
- **Range**: 70-100% (green zone starts at 70%)
- **Carryforward**: Enabled (if a module isn't changed, previous coverage is used)
- **Ignore patterns**: Test files, build artifacts

## CI Integration

The GitHub Actions workflow (`.github/workflows/ci.yml`) automatically:
1. Runs all tests
2. Generates JaCoCo XML reports
3. Uploads each module's coverage to Codecov with appropriate flags
4. Continues even if upload fails (non-blocking)

## Viewing Coverage

1. **Online**: Visit [codecov.io/gh/YOUR_ORG/TaktX-engine2](https://codecov.io/gh/YOUR_ORG/TaktX-engine2)
2. **Local HTML**: Open any of the HTML reports listed above in your browser
3. **IDE**: Many IDEs support viewing JaCoCo coverage inline

## Troubleshooting

### Upload Fails
- Verify `CODECOV_TOKEN` is set correctly in GitHub Secrets
- Check that the XML reports are being generated (look in build directories)
- Review the CI workflow logs for specific error messages

### Coverage Not Showing
- Ensure tests are actually running (`./gradlew test --info`)
- Verify JaCoCo plugin is applied in module's `build.gradle.kts`
- Check that `jacocoTestReport` task is configured with `xml.required = true`

### Wrong Coverage Numbers
- Ensure the paths in `codecov.yml` match your source structure
- Verify ignore patterns aren't excluding too much
- Check that test files are properly excluded

