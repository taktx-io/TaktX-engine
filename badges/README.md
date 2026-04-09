# Coverage Badges

This directory contains automatically generated coverage badges for the TaktX project.

## Badges

### Overall Project Coverage
![Coverage](coverage.svg)

### Module-Specific Coverage
- **TaktX Engine**: ![Engine Coverage](taktx-engine-coverage.svg)
- **TaktX Client**: ![Client Coverage](taktx-client-coverage.svg)
- **TaktX Client Quarkus**: ![Client Quarkus Coverage](taktx-client-quarkus-coverage.svg)
- **TaktX Client Spring Boot 3**: ![Client Spring Boot 3 Coverage](taktx-client-spring-boot-3-coverage.svg)
- **TaktX Client Spring Boot 4**: ![Client Spring Boot 4 Coverage](taktx-client-spring-boot-4-coverage.svg)
- **TaktX Shared**: ![Shared Coverage](taktx-shared-coverage.svg)

## How It Works

These badges are generated from JaCoCo XML coverage reports:

1. **Local Generation**: Run `./gradlew generateCoverageBadges` to create badges locally
2. **CI Generation**: Badges are automatically generated on every push to `main` via GitHub Actions
3. **Auto-Commit**: The `update-badges.yml` workflow commits updated badges back to the repository

## Using Badges in README

To use these badges in your README or documentation, use **relative paths**:

### From Root README
```markdown
![Coverage](./badges/coverage.svg)
```

### From Module READMEs
```markdown
![Coverage](../badges/taktx-engine-coverage.svg)
```

### All Module Badges
```markdown
![Engine Coverage](../badges/taktx-engine-coverage.svg)
![Client Coverage](../badges/taktx-client-coverage.svg)
![Client Quarkus Coverage](../badges/taktx-client-quarkus-coverage.svg)
![Client Spring Boot 3 Coverage](../badges/taktx-client-spring-boot-3-coverage.svg)
![Client Spring Boot 4 Coverage](../badges/taktx-client-spring-boot-4-coverage.svg)
![Shared Coverage](../badges/taktx-shared-coverage.svg)
```

**Note**: For private repositories, relative paths work correctly on GitHub. Raw URLs (raw.githubusercontent.com) require authentication tokens and won't display publicly.

## Coverage Summary

A JSON summary is also generated (`coverage-summary.json`) containing detailed coverage information for all modules.

## Color Scheme

- **🟢 Bright Green** (90-100%): Excellent coverage
- **🟢 Green** (80-89%): Good coverage
- **🟡 Yellow-Green** (70-79%): Acceptable coverage
- **🟡 Yellow** (60-69%): Needs improvement
- **🟠 Orange** (50-59%): Poor coverage
- **🔴 Red** (<50%): Critical - needs attention

## Manual Regeneration

To manually regenerate badges:

```bash
# Run tests and generate reports
./gradlew test jacocoTestReport

# Generate badges from reports
./gradlew generateCoverageBadges

# Or do both in one command
./gradlew testWithBadges
```

## Files

- `*.svg` - SVG badge files
- `coverage-summary.json` - Detailed coverage statistics in JSON format
- `README.md` - This file

