# TaktX DLQ Feature Matrix: Community vs Premium

**Document Version**: 1.0  
**Date**: May 7, 2026  
**Status**: Active  
**Depends on**: DLQ-019, `docs/security.md`

---

## Purpose

This document defines which DLQ operations and console capabilities are available in the **Community** edition and which require a **Premium** ops-console subscription.

The boundary is designed to be:
- **Consistent with the security model** in `docs/security.md` — ENGINE role keys and platform trust anchors are always enforced regardless of tier.
- **Transparent** — community users have full programmatic access to all DLQ data; only rich interactive UI is Premium.
- **Non-restrictive on data access** — the `dlq`, `dlq.replay`, and `dlq.replay-results` Kafka topics are accessible to any authorised consumer.

---

## Feature Matrix

### Epic E1 — Foundation (Topics, Envelope, Publisher)

| Feature | Community | Premium |
|---|---|---|
| `dlq` append-only topic with DELETE retention | ✅ | ✅ |
| `dlq.replay` and `dlq.replay-results` topics | ✅ | ✅ |
| `DlqEnvelope` JSON format with reason code, severity, lineage | ✅ | ✅ |
| Direct Kafka consumption of DLQ topics (any client/toolset) | ✅ | ✅ |

### Epic E2 — Rejection Capture Coverage

| Feature | Community | Premium |
|---|---|---|
| Capture of all 8 ingress surfaces into `dlq` | ✅ | ✅ |
| `captureStage` tagging (DESERIALIZER / PROCESSOR) | ✅ | ✅ |
| Stable `DlqReasonCode` enumeration | ✅ | ✅ |
| `severity` field (LOW / MEDIUM / HIGH / CRITICAL) | ✅ | ✅ |
| Excluded-topic incident logging and metrics | ✅ | ✅ |

### Epic E3 — Replay Pipeline

| Feature | Community | Premium |
|---|---|---|
| Submit `DlqReplayCommand` to `dlq.replay` via `taktx-client` | ✅ | ✅ |
| Dry-run replay (validate without forward) via `taktx-client` | ✅ | ✅ |
| Read `DlqReplayResult` from `dlq.replay-results` via `taktx-client` | ✅ | ✅ |
| `DlqReplayCommandBuilder` helper (lineage auto-population) | ✅ | ✅ |
| ENGINE-signed forwarded records | ✅ | ✅ |
| `STRICT` validation policy | ✅ | ✅ |
| `OPERATOR_OVERRIDE` validation policy | ✅ | ✅ |
| Destination topic safety enforcement (engine-side) | ✅ | ✅ |
| Lineage headers (`X-DLQ-Lineage-Ref`, `X-DLQ-Correction-Id`, `X-DLQ-Source-Offset`) | ✅ | ✅ |

### Epic E4 — Observability

| Feature | Community | Premium |
|---|---|---|
| Prometheus metrics (`taktx.dlq.entries`, `taktx.dlq.replay.outcomes`) | ✅ | ✅ |
| Alert rules in `prometheus-dlq-alerts.yaml` | ✅ | ✅ |
| Structured audit logs in engine stdout/JSON logging | ✅ | ✅ |
| Retention policy configuration (per-environment) | ✅ | ✅ |
| Custom Grafana dashboards for DLQ explorer | ❌ | ✅ |
| RBAC-aware audit dashboard | ❌ | ✅ |

### Epic E5 — Console Contract (This Epic)

| Feature | Community | Premium |
|---|---|---|
| `taktx-client` DLQ API (`registerDlqEntryConsumer`, `submitReplayCommand`, `registerReplayResultConsumer`) | ✅ | ✅ |
| `DlqReplayCommandBuilder` fluent API | ✅ | ✅ |
| **DLQ Explorer** — rich filter UI (reason code, severity, time range, process ID) | ❌ | ✅ |
| **Payload Inspector** — CBOR decode + JSON display + schema mismatch warnings | ❌ | ✅ |
| **Correction UI** — JSON/form editor for payload and headers | ❌ | ✅ |
| **Replay Approval Workflow** — multi-step review, approvals, RBAC | ❌ | ✅ |
| **Lineage Visualization** — interactive graph (original → DLQ → correction → replay) | ❌ | ✅ |
| **Batch Replay** — multiple DLQ entries selected and replayed in one operation | ❌ | ✅ |
| **RBAC Audit Dashboard** — operator action history with sign-off trails | ❌ | ✅ |
| **Console dry-run pre-flight UI** — visual display of pass/fail before live replay | ❌ | ✅ |

---

## Community Tier Summary

Community users have full **programmatic access** to the entire DLQ system:

1. **Read** all DLQ entries from the `dlq` topic — `registerDlqEntryConsumer(groupId, handler)`.
2. **Build** well-formed replay commands — `DlqReplayCommandBuilder.from(envelope)…build()`.
3. **Submit** dry-run and live replay commands — `submitReplayCommand(command)`.
4. **Read** replay results — `registerReplayResultConsumer(groupId, handler)`.
5. **Monitor** via Prometheus/Grafana using the published metric names.

The programmatic API uses standard Kafka ACLs for access control. No additional licensing gate is required.

---

## Premium Tier Summary

Premium adds a **rich ops console** on top of the Community programmatic API:

- An interactive **DLQ Explorer** with faceted filtering, full-text search, and time-range queries.
- A **Payload Inspector** that CBOR-decodes envelopes, renders them as JSON, and highlights schema version mismatches.
- A **Correction UI** with both JSON-patch and form-based editing, pre-flight diff view, and RBAC approvals.
- **Dry-run pre-flight visualization** — shows which validation gates pass/fail before committing a live replay.
- A **Lineage Graph** that chains source record → DLQ entry → correction → replayed record with clickable nodes.
- **Batch replay** with progress tracking and success/failure rate reporting.
- An **RBAC Audit Dashboard** that shows operator action history, override justifications, and sign-off trails.

Premium console features communicate exclusively through the `taktx-client` DLQ API and the three DLQ Kafka topics — they introduce no additional engine API surface.

---

## Security Model Consistency

The following security properties apply **regardless of tier**:

| Property | Enforced by |
|---|---|
| Destination topic must be in the 8-surface whitelist | Engine (`DlqReplayProcessor`) |
| Destination must match engine's tenant + namespace | Engine |
| Replayed messages always signed by ENGINE role key | Engine |
| `OPERATOR_OVERRIDE` requires explicit `overrideReason` field | Client (`DlqReplayCommandBuilder`) + Engine |
| Kafka ACL guards on `dlq.replay` input topic | Kafka broker (configured outside engine) |
| `operatorId` is a plain string (no JWT gate in base tier) | N/A — JWT gate is a Premium ops-console concern |

See `docs/security.md` and `docs/security-future-development-plan.md` for the full security model.

---

## Future Premium Extensions (Backlog)

These features are not yet scheduled but fit the Premium boundary:

- **Operator JWT requirement** for `DlqReplayCommand.operatorId` validated against an OIDC IdP.
- **SLA alerting** — alert when DLQ entry age exceeds a configured remediation SLA.
- **Multi-environment console** — cross-namespace/tenant DLQ explorer with federated view.
- **Auto-remediation rules** — pattern-matched auto-replay for deterministic error classes (e.g. expired JWT auto-refresh).
- **Cold-archive integration** — export DLQ entries older than retention threshold to object storage (see `docs/dlq-retention-policy.md`).

