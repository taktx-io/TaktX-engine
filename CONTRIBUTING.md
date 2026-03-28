# Contributing to TaktX Engine

Thank you for your interest in contributing! TaktX Engine is an open-source project and we welcome contributions of all kinds — bug reports, documentation improvements, test coverage, and feature development.

## Table of Contents

1. [Code of Conduct](#code-of-conduct)
2. [Getting Started](#getting-started)
3. [Development Environment](#development-environment)
4. [Making Changes](#making-changes)
5. [Coding Standards](#coding-standards)
6. [Testing](#testing)
7. [Submitting a Pull Request](#submitting-a-pull-request)
8. [Commit Message Conventions](#commit-message-conventions)
9. [Developer Certificate of Origin (DCO)](#developer-certificate-of-origin-dco)
10. [Reporting Bugs](#reporting-bugs)

---

## Code of Conduct

This project adheres to the [Contributor Covenant Code of Conduct](CODE_OF_CONDUCT.md). By participating, you are expected to uphold this code. Please report unacceptable behaviour to **conduct@taktx.io**.

---

## Getting Started

1. **Fork** the repository on GitHub.
2. **Clone** your fork locally:
   ```bash
   git clone https://github.com/<your-username>/TaktX-engine.git
   cd TaktX-engine
   ```
3. Add the upstream remote:
   ```bash
   git remote add upstream https://github.com/taktx-io/TaktX-engine.git
   ```

---

## Development Environment

### Prerequisites

| Tool | Minimum Version | Notes |
|---|---|---|
| Java (JDK) | 23 | Temurin/Eclipse build recommended |
| Docker | 24+ | Required for Testcontainers integration tests |
| Apache Kafka | 3.x | A running broker is needed for `quarkusDev` |

### Build

```bash
# Full build including all tests
./gradlew build

# Build without integration tests (faster)
./gradlew build -x test

# Run a specific module's tests
./gradlew :taktx-client:test

# Run the engine in development mode (live reload)
# Requires a running Kafka broker — see docker/docker-compose.yaml
./gradlew :taktx-engine:quarkusDev
```

### Starting Kafka locally

```bash
# Start Kafka (and supporting services) with Docker Compose
docker compose -f docker/docker-compose.yaml up -d
```

---

## Making Changes

1. **Create a branch** from `main` for your change:
   ```bash
   git checkout -b feat/my-feature
   # or
   git checkout -b fix/issue-123
   ```

2. Make your changes.

3. **Apply code formatting** before committing:
   ```bash
   ./gradlew spotlessApply
   ```

4. **Run tests**:
   ```bash
   ./gradlew test
   ```

5. Commit your changes (see [Commit Message Conventions](#commit-message-conventions)).

---

## Coding Standards

All Java code is formatted with **Google Java Format** enforced by [Spotless](https://github.com/diffplug/spotless). CI will fail if formatting is incorrect.

- Run `./gradlew spotlessApply` before committing to auto-format.
- Run `./gradlew spotlessCheck` to verify without modifying files.

**License headers**: Every new Java source file must start with the Apache 2.0 header:

```java
/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
```

Spotless enforces this automatically — `spotlessApply` will add or correct headers.

---

## Testing

- **Unit tests**: Located in `src/test/java`. Run with `./gradlew test`.
- **Integration tests**: Use [Testcontainers](https://testcontainers.com/) and require Docker to be running. They are included in `./gradlew test`.
- **Coverage**: JaCoCo reports are generated at `<module>/build/reports/jacoco/`. Coverage badges in `badges/` are auto-updated in CI.

When adding a new feature, please include corresponding tests. When fixing a bug, add a regression test.

---

## Submitting a Pull Request

1. Push your branch to your fork:
   ```bash
   git push origin feat/my-feature
   ```

2. Open a Pull Request against the `main` branch of `taktx-io/TaktX-engine`.

3. Fill in the **pull request template** — describe what the change does, why it's needed, and include any relevant issue numbers.

4. Ensure all CI checks pass:
   - `spotlessCheck` (formatting)
   - `test` (unit + integration tests)

5. A maintainer will review your PR. Please respond to review comments promptly. Once approved, a maintainer will merge it.

### What gets merged

- Changes should be focused and atomic — one logical change per PR.
- Breaking API changes require discussion in an issue before a PR is opened.
- All public API changes should be accompanied by documentation updates.

---

## Commit Message Conventions

We follow [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <short summary>

[optional body]

[optional footer(s)]
Signed-off-by: Your Name <your@email.com>
```

**Types:** `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`, `ci`

**Examples:**

```
feat(engine): add timer boundary event support
fix(client): handle null response in startProcess
docs(contributing): add testcontainers requirement note
```

---

## Developer Certificate of Origin (DCO)

By contributing to TaktX Engine, you agree to the [Developer Certificate of Origin v1.1](https://developercertificate.org/). This is a lightweight way to certify that you wrote the contribution or have the right to submit it under the Apache 2.0 license.

**Every commit must be signed off.** Add the `-s` flag when committing:

```bash
git commit -s -m "feat(client): add retry configuration option"
```

This adds a `Signed-off-by: Your Name <your@email.com>` trailer to your commit. The DCO check in CI will fail without it.

---

## Reporting Bugs

Please use the [bug report template](https://github.com/taktx-io/TaktX-engine/issues/new?template=bug_report.yml) on GitHub Issues. Include:

- TaktX Engine version
- Java version and distribution
- Kafka version
- A minimal reproduction (BPMN file + client code if applicable)
- The full stack trace or error output

For **security vulnerabilities**, please do **not** open a public issue. See [SECURITY.md](SECURITY.md) for the responsible disclosure process.

