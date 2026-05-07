# DLQ Retention and Storage Policy

Date: 2026-05-07  
Status: Authoritative — applies to all TaktX deployments.

---

## Topology recap

TaktX uses **three namespace-scoped Kafka topics** shared across all ingress surfaces
(see `docs/dlq-engine-design.md` — "Topology Decision"):

| Kafka topic | Purpose | Cleanup policy |
|---|---|---|
| `<prefix>.dlq` | **Single** append-only sink for all rejection captures | `DELETE` |
| `<prefix>.dlq.replay` | Operator-submitted replay commands | `DELETE` |
| `<prefix>.dlq.replay-results` | Replay outcome audit records | `DELETE` |

There is **one physical `dlq` Kafka topic** — not one per ingress surface.  
Per-surface routing is carried entirely inside the envelope:
`DlqEnvelope.sourceTopic` is the classifier field that identifies which of the eight
ingress surfaces (`process-instance`, `message-event`, `signals`, `definitions`,
`dmn-definitions`, `process-definition-activation`, `dmn-definition-activation`,
`usertasks-response`) a given DLQ entry originated from.

Excluded topics (`schedule-commands`, `topic-meta-*`, `taktx-configuration`,
`taktx-signing-keys`, `instance-update`, `usertasks`, `xml-by-*`) have no DLQ retention
requirement. Their failure handling is described in `docs/dlq-engine-design.md` (Part 6 — DLQ-008A).

---

## Scope

This document governs the retention configuration for the **three DLQ Kafka topics** listed above.
Retention is configured at the Kafka broker level per topic. The TaktX engine creates these topics
with `cleanup.policy=delete`; the `retention.ms` value must be set separately per environment via
your IaC / Helm values.

## Per-environment Retention Matrix

| Environment | Retention (`retention.ms`) | Approximate human value | Notes |
|---|---|---|---|
| **Development** | `604800000` (7 days) | 7 days | Sufficient for local debugging cycles |
| **Staging** | `2592000000` (30 days) | 30 days | Supports regression + replay testing |
| **Production** | `7776000000`–`15552000000` | 90–180 days | Compliance window; align with your SLA. Default recommendation: 90 days. |

### Setting enforcement

Apply via the Kafka admin API at topic creation or via `kafka-configs.sh`.
Run once per DLQ topic per environment:

```bash
# Example: set 90-day retention in production (run for each of the 3 DLQ topics)
for TOPIC in dlq dlq.replay dlq.replay-results; do
  kafka-configs.sh --bootstrap-server $KAFKA_BOOTSTRAP \
    --alter --entity-type topics \
    --entity-name "${TAKTX_TOPIC_PREFIX}.${TOPIC}" \
    --add-config retention.ms=7776000000
done
```

`DynamicTopicManager` (TaktX engine) creates topics with `cleanup.policy=delete`.  
Retention `ms` must be set separately per environment via your IaC/Helm values.

---

## Storage Sizing Guidance

- DLQ entries are small (`DlqEnvelope` JSON, typically < 4 KB including `valueBytes`).
- Replay commands and results are even smaller (< 1 KB each).
- Baseline estimate: 1 000 DLQ entries/day × 4 KB × 90 days ≈ **360 MB** for `dlq`.
- Adjust based on observed throughput from `taktx_dlq_entries_total` in Grafana.

---

## Recommended AlertManager Rule (Near-Capacity)

Add a capacity warning in your Kafka cluster monitoring if any of the three DLQ topics approaches
its retention byte limit. The `<prefix>.dlq` topic is the most likely to grow large since it
receives all rejection captures; `dlq.replay` and `dlq.replay-results` stay small under normal
operator load.

```yaml
- alert: DlqTopicApproachingRetention
  # Match exactly the three DLQ topics by their bare suffix (anchored with $).
  # `<prefix>.dlq` carries all failure captures; the replay topics are monitored
  # for completeness but stay small under normal operator load.
  expr: |
    kafka_log_log_size{topic=~".*\\.dlq$|.*\\.dlq\\.replay$|.*\\.dlq\\.replay-results$"} /
    kafka_log_log_retention_bytes{topic=~".*\\.dlq$|.*\\.dlq\\.replay$|.*\\.dlq\\.replay-results$"}
    > 0.80
  for: 10m
  labels:
    severity: warning
  annotations:
    summary: "DLQ topic {{ $labels.topic }} is over 80% of its retention limit"
    description: >
      One of the three DLQ topics is approaching its retention byte limit.
      topic={{ $labels.topic }}
      Consider increasing retention.bytes or reducing retention.ms for this environment.
```

---

## Future Cold-Archive Strategy

When 180-day on-broker retention is insufficient (e.g. regulatory audit requirements):

1. **Export via Kafka Connect**: use the S3 Sink Connector (or equivalent) to stream entries from
   `<prefix>.dlq` to object storage (S3, GCS, Azure Blob) before the retention window expires.
2. **Partition boundary**: use `DlqEnvelope.rejectionTimestampMs` as the object storage partition
   key (e.g. `year=YYYY/month=MM/day=DD/`).
3. **Per-surface partitioning**: because all ingress surfaces share the single `<prefix>.dlq` topic,
   use `DlqEnvelope.sourceTopic` as a secondary partition key (e.g. `source=process-instance/`)
   to keep surface-specific entries queryable without needing separate Kafka topics.
4. **Deduplication key**: `DlqEnvelope.sourceMessageHash` (SHA-256 of original value bytes) serves
   as a stable dedup key across the hot (Kafka) and cold (object storage) tiers.
5. **Replay from cold**: operator tooling should re-publish from cold storage to `<prefix>.dlq.replay`
   using the preserved `DlqReplayCommand` schema; no engine changes required.

---

## Excluded Topics — No Retention Requirement

| Topic group | Handling | Rebuild strategy |
|---|---|---|
| `schedule-commands` | Incident log + counter (`taktx_excluded_topic_failures_total`) | Fix engine defect + redeploy |
| `taktx-configuration`, `taktx-signing-keys` | `ContinueOnDeserializationErrorHandler` skip + counter (`taktx_excluded_topic_deserialization_errors_total`) | Republish correct record |
| `topic-meta-*` | `DynamicTopicManager` seek-past error handler | Republish correct meta record |
| `instance-update`, `usertasks`, `xml-by-*` | Engine output — write failures trigger stream restart | Automatic rebuild on restart via `process-instance` reprocessing |



