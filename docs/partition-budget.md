# TaktX — Partition Budget Architecture

**Date:** March 2026  
**Status:** Approved — ready for implementation  
**Scope:** `taktx-engine`, `taktx-client`, `taktx-shared`

---

## Contents

1. [Problem Statement](#1-problem-statement)
2. [Current Model & Its Limitations](#2-current-model--its-limitations)
3. [Proposed Model: Total Partition Budget](#3-proposed-model-total-partition-budget)
4. [Topic Inventory & Fixed Costs](#4-topic-inventory--fixed-costs)
5. [License Tier Values](#5-license-tier-values)
6. [Worker Partition Configuration](#6-worker-partition-configuration)
7. [Repartitioning Constraints](#7-repartitioning-constraints)
8. [Enforcement Architecture](#8-enforcement-architecture)
9. [Implementation Plan](#9-implementation-plan)

---

## 1. Problem Statement

The current license enforcement caps Kafka partitions **per topic**. This is a coarse control:
it prevents throughput tuning (every topic is capped at the same value) and provides no natural
limit on the number of worker topics a user can register.

Switching to a **total partition budget** — the sum of all partitions across all engine-managed
topics — solves both problems simultaneously:

- Operators can allocate their budget freely: e.g. give high-throughput fixed topics more
  partitions while keeping worker topics at 1.
- The number of worker topics is naturally bounded by the budget. A free-tier user with budget 60
  and fixed topics consuming 30 partitions has 30 partitions left for workers.
- The license value becomes a single meaningful number rather than a per-topic cap that silently
  prevents any tuning at all.

---

## 2. Current Model & Its Limitations

### Where enforcement happens today

| Location | Check | Behaviour on violation |
|---|---|---|
| `TopicBootstrapper.bootstrapManagedTopics()` | `taktConfiguration.getPartitions() > licenseManager.getMaxAllowedPartitions()` | `Runtime.halt(1)` |
| `DynamicTopicManager.scanRequest()` | `topicMeta.getNrPartitions() <= licenseManager.getMaxAllowedPartitions()` | `Runtime.halt(1)` |
| `DynamicTopicManager.adaptToExternalChanges()` | `actualTopicMeta.getNrPartitions() > licenseManager.getMaxAllowedPartitions()` | `Runtime.halt(1)` |

### Problems with the current model

1. **No throughput tuning** — every managed topic is forced to the same partition count. An
   operator who wants 6 partitions on `process-instance` but 1 on `usertasks` cannot do so.

2. **No worker count control** — per-topic enforcement does not limit how many worker topics
   can be created, only how many partitions each one can have.

3. **`halt(1)` in `adaptToExternalChanges()`** — this runs on a background scheduler thread
   every 10 seconds. Halting the JVM from a scheduler thread is unsafe and prevents graceful
   shutdown. Should be replaced with a non-fatal warning + metric.

4. **`TopicBootstrapper` check is misplaced** — it fires before `cachedActualTopicMetaMap` is
   populated, so it cannot know the real total. It only knows the configured `partitions` value
   for the managed fixed topics.

---

## 3. Proposed Model: Total Partition Budget

`maxKafkaPartitions` in the license becomes the **maximum total number of partitions** the engine
is allowed to manage across all its topics, fixed and dynamic.

```
budget = maxKafkaPartitions (from license)

total = Σ partitions(fixed managed topics)      ← set by taktConfiguration.getPartitions()
      + Σ partitions(dynamic worker topics)     ← set per-worker by requestExternalTaskTopic()
      + 4                                        ← constant: 4 initial fixed topics × 1 partition

Enforcement: reject any new topic/partition change that would push total > budget
```

### What changes for the operator

- `taktConfiguration.getPartitions()` controls all **fixed managed topics** uniformly (unchanged).
- Each worker calls `requestExternalTaskTopic(externalTaskId, partitions, ...)` and can now
  specify its own partition count (1–N), subject to the remaining budget.
- The engine tracks the running total and rejects requests that would exceed the budget.
- No halt on violation — log a warning and reject the request gracefully.

---

## 4. Topic Inventory & Fixed Costs

### Initial fixed topics — always 1 partition (hardcoded, not tracked in `cachedActualTopicMetaMap`)

| Topic | Partitions |
|---|---|
| `topic-meta-requested` | 1 |
| `topic-meta-actual` | 1 |
| `taktx-configuration` | 1 |
| `taktx-signing-keys` | 1 |
| **Subtotal** | **4** |

These 4 partitions are a constant that is simply added as a fixed offset in the budget check.
They are never published to `topic-meta-actual`, so they do not appear in
`cachedActualTopicMetaMap`. A hardcoded `INITIAL_FIXED_TOPIC_PARTITION_COST = 4` constant
handles this cleanly without requiring topology changes.

### Managed fixed topics — partition count = `taktConfiguration.getPartitions()`

| Topic | Notes |
|---|---|
| `xml-by-process-definition-id` | |
| `process-definition-activation` | |
| `message-event` | |
| `schedule-commands` | |
| `instance-update` | highest throughput — most likely to need more partitions |
| `process-instance` | highest throughput — most likely to need more partitions |
| `definitions` | |
| `signals` | |
| `usertasks` | |
| `usertasks-response` | |
| **Count: 10 topics** | cost = 10 × `taktConfiguration.getPartitions()` |

### Dynamic worker topics — partition count per-worker

| Topic pattern | Partitions |
|---|---|
| `external-task-trigger-<externalTaskId>` | Worker-specified (default: 1) |

---

## 5. License Tier Values

Budget sizing rationale: a community user who runs all fixed topics at 3 partitions
(30 partitions) and registers 10 worker topics at 1 partition each (10 partitions) uses
44 total (including the 4 initial fixed). A budget of 60 leaves meaningful headroom.

| License tier | `maxKafkaPartitions` | Supports |
|---|---|---|
| **COMMUNITY** | **60** | 10 fixed × 3 + ~10 worker topics at 1–2 partitions |
| **STANDARD** | **180** | 10 fixed × 3 + 5 process types × 10 workers × 3 partitions |
| **ENTERPRISE** | `null` (unlimited) | No cap |

### Community edition worker capacity (illustrative)

With `taktConfiguration.getPartitions()=3` and budget=60:

```
Fixed cost:  4 (initial) + 10 × 3 (managed) = 34 partitions
Remaining:   60 - 34 = 26 partitions for workers

Workers at 1 partition each:  26 worker topics
Workers at 2 partitions each: 13 worker topics
Workers at 3 partitions each:  8 worker topics
```

This provides meaningful flexibility while keeping the free tier bounded.

---

## 6. Worker Partition Configuration

### Current state

`TaktXClient.requestExternalTaskTopic(String externalTaskId, int partitions, CleanupPolicy, short replicationFactor)`
already accepts a `partitions` parameter — workers can technically request any count today.
However the current per-topic enforcement silently caps it at `maxKafkaPartitions` (currently 3).

### Proposed change

Workers specify their desired partition count when calling `requestExternalTaskTopic`. The engine
enforces the **remaining budget** rather than a per-topic cap:

```java
// Worker side — explicitly request partition count
taktXClient.requestExternalTaskTopic("invoice-processor", 2, CleanupPolicy.DELETE, (short) 1);

// Default convenience overload (new) — uses 1 partition
taktXClient.requestExternalTaskTopic("invoice-processor");
```

Add a default overload to `TaktXClient` and `ExternalTaskTopicRequester` that uses
`partitions=1`, `cleanupPolicy=DELETE`, `replicationFactor=1` — covers the common case where
the worker does not need to tune throughput.

### Kafka's partition increase constraint

Kafka allows **increasing** partition count but **never decreasing** it. For worker topics
(`external-task-trigger-*`) this is safe:

- Workers are **stateless** — no partition-keyed state exists on these topics.
- A partition increase simply spreads the work across more consumer threads.
- A worker can request more partitions on a subsequent start and `DynamicTopicManager` will
  attempt to increase them (via `adaptToExternalChanges` reconciliation).

For **managed fixed topics** the situation is different — see §7.

---

## 7. Repartitioning Constraints

### Worker topics — safe to increase

Worker topics carry only in-flight task triggers. They have no associated Kafka Streams state
stores and no partition-keyed semantics. Increasing partition count:

- Requires the worker consumer group to rebalance (brief pause, automatic).
- Does not affect ordering guarantees (task triggers are keyed by process instance ID, not
  worker partition).
- Is safe to do while the engine and workers are running.

**Decreasing** is not possible in Kafka and is not supported.

### Fixed managed topics — high risk

The 10 managed fixed topics (`process-instance`, `instance-update`, etc.) feed directly into
Kafka Streams topologies with **stateful processors** (instance state stores, signal
subscriptions, schedule commands). Changing their partition count:

- **Invalidates all Kafka Streams internal changelog topics** — the KTable/store changelog is
  partitioned 1:1 with the input topic. A mismatch causes the consumer to fail on restart.
- **Breaks co-partitioning** — Kafka Streams joins and co-located processing require all topics
  in a sub-topology to have the same partition count.
- **Requires a full state store rebuild** — all engine-managed state must be replayed from
  scratch after a partition change.

**Policy: fixed managed topics cannot be repartitioned at runtime.**

The `DynamicTopicManager.adaptToExternalChanges()` reconciliation loop already detects
partition count differences between `cachedRequestTopicMetaMap` and the actual Kafka broker
state. The current behaviour is `halt(1)`. The new behaviour should be:

- **For worker topics**: if actual partitions > requested → update `cachedActualTopicMetaMap`
  to reflect reality (accept the increase). If actual < requested and budget allows → attempt
  to increase via `adminClient.createPartitions()`.
- **For fixed managed topics**: if actual ≠ configured → log a `WARN` and emit a metric.
  Do **not** halt. Do **not** attempt to change the partition count. Operators must handle
  this manually (drain → delete topics → restart → rebuild state).

---

## 8. Enforcement Architecture

### Budget tracking

`DynamicTopicManager` maintains the authoritative running total using existing maps:

```
total = INITIAL_FIXED_TOPIC_PARTITION_COST (= 4, constant)
      + sum(cachedActualTopicMetaMap.values().stream().mapToInt(TopicMetaDTO::getNrPartitions))
```

`cachedActualTopicMetaMap` is populated by `scanActual()` consuming `topic-meta-actual`.
`TopicBootstrapper` publishes all managed fixed topics to `topic-meta-actual` at startup.
So by the time `scanRequest()` processes the first worker topic request, the fixed topic
cost is already in the map.

### Check point: `scanRequest()`

When a worker topic request is processed:

```java
int currentTotal = computeCurrentTotal(); // from cachedActualTopicMetaMap + constant
int requested = topicMeta.getNrPartitions();

if (currentTotal + requested > licenseManager.getMaxAllowedPartitions()) {
    log.warn("Partition budget exceeded: current={} requested={} budget={} — topic {} rejected",
        currentTotal, requested, licenseManager.getMaxAllowedPartitions(),
        topicMeta.getTopicName());
    // Do NOT halt. Publish a null to topic-meta-actual so the requestor knows it was rejected.
    publishTopicMetaActual(topicMeta.getTopicName(), null);
    return;
}
```

No `halt(1)`. The worker's topic simply does not get created. The engine continues running.
Worker processes that attempt to consume a non-existent topic will log a warning and retry.

### Check point: `adaptToExternalChanges()`

Replace the existing per-topic cap check with a total check, and remove `halt(1)`:

- Worker topic has more partitions than requested (operator manually increased via Kafka UI) →
  accept and update `cachedActualTopicMetaMap`. Log an info.
- Worker topic has fewer partitions than requested and budget allows → call
  `adminClient.createPartitions()` to increase. Log an info.
- Fixed managed topic has wrong partition count → log a `WARN`. Do not modify. Do not halt.
- Total across all topics exceeds budget (e.g. after a license downgrade) → log a `WARN`.
  Do not halt. New topic requests will be rejected until the total comes back within budget.

### Check point: `TopicBootstrapper.bootstrapManagedTopics()`

The current check (`taktConfiguration.getPartitions() > maxAllowed`) becomes:

```java
int fixedCost = Topics.managedFixedTopics().size() * taktConfiguration.getPartitions()
              + INITIAL_FIXED_TOPIC_PARTITION_COST;
if (fixedCost > licenseManager.getMaxAllowedPartitions()) {
    log.error("Fixed topic partition cost ({}) exceeds license budget ({}). "
        + "Reduce taktx.engine.partitions or upgrade license.",
        fixedCost, licenseManager.getMaxAllowedPartitions());
    Runtime.getRuntime().halt(1); // Cannot start — no topics means no engine
}
```

This check stays as `halt(1)` because without the fixed topics the engine cannot function at all.
The error message tells the operator exactly what to do.

### Removal of `halt(1)` from `adaptToExternalChanges()`

The scheduler runs every 10 seconds on a Quarkus scheduler thread. Calling `halt(1)` from
that thread bypasses `@PreDestroy` handlers, leaves Kafka producers open, and corrupts
in-flight records. Replace with a warning log and graceful degradation.

---

## 9. Implementation Plan

### Step 1 — `LicenseFeatures` + `LicenseManager`
- Rename `getMaxAllowedPartitions()` → `getPartitionBudget()` (clearer semantics).
- Update `DefaultLicenseManager`, `TestLicenseManager`, `LicenseManager` interface.

### Step 2 — `DynamicTopicManager`
- Add `INITIAL_FIXED_TOPIC_PARTITION_COST = 4` constant.
- Add `computeCurrentTotal()` private method summing `cachedActualTopicMetaMap` + constant.
- Replace per-topic check in `scanRequest()` with total budget check. Remove `halt(1)`.
- Replace per-topic check in `adaptToExternalChanges()` / `compareAndPublishChanges()` with
  total check. Remove `halt(1)`. Distinguish between fixed and worker topics.
- Add `adminClient.createPartitions()` call for worker topics where actual < requested.

### Step 3 — `TopicBootstrapper`
- Replace per-topic check with fixed-cost check (as described in §8).
- Keep `halt(1)` here only — the engine cannot start without fixed topics.

### Step 4 — `TaktXClient` + `ExternalTaskTopicRequester`
- Add default overload: `requestExternalTaskTopic(String externalTaskId)` → partitions=1,
  cleanupPolicy=DELETE, replicationFactor=1.
- Update Javadoc on the existing overload to document the partition budget semantics.

### Step 5 — License file update
- Update the community license: `maxKafkaPartitions:INT=60`.
- Update `DefaultLicenseManagerTest` default free-tier value from 3 to 60.

### Step 6 — Documentation
- Update `docs/security.md` enforcement table: change "3" to "60" and update description
  from "per topic" to "total across all managed topics".
- Update `LICENSE.md` free-tier description.

