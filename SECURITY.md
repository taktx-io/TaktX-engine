# Security Policy

**Last updated:** 2026-04-27
**Status:** Active policy
**Audience:** Security researchers, operators, and maintainers

This document defines how to report vulnerabilities and the repository security support policy.

## Related Security Documents

- Implemented architecture and controls: [`docs/security.md`](docs/security.md)
- Planned follow-up roadmap (DLQ, telemetry, threat model): [`docs/security-future-development-plan.md`](docs/security-future-development-plan.md)

## Supported Versions

TaktX Engine is currently in **beta**. Security fixes are applied to the **latest release only**. Pre-1.0.0 versions do not receive backported security patches.

| Version | Supported |
|---|---|
| 0.3.x (latest beta) | ✅ Active |
| 0.2.x and earlier | ❌ No longer supported |

Once `1.0.0` is released, a formal long-term support policy will be published.

---

## Reporting a Vulnerability

**Please do not open a public GitHub issue for security vulnerabilities.**

We use GitHub's [private security advisory](https://github.com/taktx-io/TaktX-engine/security/advisories/new) feature. To report a vulnerability:

1. Go to **[Security → Report a vulnerability](https://github.com/taktx-io/TaktX-engine/security/advisories/new)** on this repository.
2. Fill in a description of the vulnerability, steps to reproduce, and any proof-of-concept code.
3. We will acknowledge your report within **3 business days**.
4. We aim to release a patch within **14 calendar days** for high/critical severity issues.

Alternatively you can reach us at **security@taktx.io**.

---

## Disclosure Policy

- We follow [coordinated vulnerability disclosure](https://en.wikipedia.org/wiki/Coordinated_vulnerability_disclosure).
- Once a patch is released, the advisory will be published publicly.
- We credit reporters in the advisory unless you prefer to remain anonymous.
- We do not operate a bug bounty program at this time.

---

## Scope

**In scope:**
- Remote code execution, authentication bypass, or privilege escalation in `taktx-engine`
- Cryptographic weaknesses in the Ed25519 signing or RS256 JWT verification paths
- Unauthorized access to process state via the Kafka topic ACL design
- Dependency vulnerabilities with a direct exploit path (not merely theoretical)

**Out of scope:**
- Vulnerabilities in third-party dependencies with no known exploit path
- Issues requiring physical access to the Kafka cluster
- Denial of service via unbounded resource allocation when running without resource limits (i.e. misconfigured deployments)
- Issues in example Docker Compose files intended only for local development

---

## Security Architecture

For a detailed description of TaktX's cryptographic design (Ed25519 signing, RS256 JWT command authorization, trust anchors, and key distribution), see [`docs/security.md`](docs/security.md).

Operationally, production deployments are expected to:

- enable anchored trust instead of relying on community/open trust mode
- publish countersigned signing keys for engine, workers, and platform JWT issuers
- restrict writes to `taktx-signing-keys` with Kafka ACLs even when anchored mode is enabled

Community mode remains useful for local development, but it should be treated as insecure for production.

