# Architect Prompt — DLQ Coverage Boundary for TaktX Topics

We have revisited the DLQ topic scope and want to validate the boundary before continuing implementation.

## Current Proposal

Our proposed rule is:

- **DLQ all topics that are directly related to BPMN/DMN processing**
- **Do not use DLQ for meta/control-plane topics**

The motivation is:
- BPMN/DMN processing failures are usually the ones that benefit from forensic inspection, manual correction, and replay.
- Meta/control-plane topics likely need a different failure strategy (strict reject, audit log, alerting, operator incident, etc.), not necessarily DLQ.

## Proposed BPMN/DMN Topics That Should Have DLQ Coverage

We are currently leaning toward DLQ coverage for these topics:

- `process-definition-activation`
- `message-event`
- `schedule-commands`
- `process-instance`
- `definitions`
- `dmn-definitions`
- `signals`
- `usertasks-response`
- `dmn-definition-activation`

## Topics We Currently Consider Meta / Control-Plane

These are the topics we currently do **not** want to treat as DLQ candidates:

- `topic-meta-requested`
- `topic-meta-actual`
- `taktx-configuration`
- `taktx-signing-keys`
- `xml-by-process-definition-id`
- `xml-by-dmn-definition-id`
- `instance-update`
- `usertasks`
- possibly other projection/cache/materialization topics

## Questions for Architectural Guidance

We would appreciate explicit guidance on the following:

1. **Do you agree with the rule of “DLQ BPMN/DMN processing topics, but not meta/control-plane topics”?**

2. **Should activation topics really be DLQ-backed?**
   Specifically:
   - `process-definition-activation`
   - `dmn-definition-activation`
   
   These are BPMN/DMN-related, but they may also be seen as internal/derived activation/projection topics rather than true ingress boundaries.

3. **Should `schedule-commands` be DLQ-backed if it is strictly engine-generated?**
   If the producer is guaranteed to be internal/engine-only, should we:
   - still DLQ it,
   - treat it as incident/alert-only,
   - or make it environment/configuration dependent?

4. **Should `message-event` and `signals` always be DLQ-backed, or only when they are true external ingress boundaries?**
   In other words: should the classification be based on topic semantics, or based on who is allowed to publish?

5. **What should be the preferred failure mechanism for meta/control-plane topics if not DLQ?**
   For example:
   - reject + structured log
   - audit/security event topic
   - metrics + alerting only
   - incident state
   - operator runbook / manual recovery path

6. **Can you give us a durable classification rule we can apply to future topics?**
   For example, should we classify topics by one or more of these criteria:
   - external ingress vs engine-internal
   - replay value
   - user/business significance
   - control-plane vs processing-plane
   - derived/projection topic vs source command/event topic

## What We Need From This Decision

We want to use your answer to finalize:
- the DLQ topic map
- the backlog priorities
- which topics get replay tooling
- which topics should instead use non-DLQ operational handling

A crisp “include/exclude/conditional” recommendation per topic (or per topic class) would help us proceed confidently.

