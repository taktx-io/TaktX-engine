# DMN Result References in TaktX

This note documents the **current implemented and tested** behaviour for how required decision
results are made available to downstream decisions during DRG (Decision Requirements Graph)
evaluation in TaktX.

## Scope and wording

- This topic is governed by **DMN / FEEL semantics**, not BPMN.
- This document describes the **current runtime contract** in TaktX.
- TaktX currently binds required decision results into downstream evaluation scope by
  **decision id**.
- DMN validation behaviour is configurable through `taktx.engine.dmn.validation-mode` or, at
  runtime, via cluster configuration updates.
- TaktX intentionally uses a **scalar vs context** result-shaping model:
  - a decision with a **single output column** is exposed as a scalar value
  - a decision with **multiple output columns** is exposed as a context/object whose entries are
    the output names
- Validation strictness is controlled by the configured mode; see [Validation modes](#validation-modes).

## Validation modes

TaktX supports three DMN validation modes:

| Mode | Behaviour | Recommended use |
|---|---|---|
| `PERMISSIVE` | Preserve best-effort behaviour. Validation issues fall back to `null` / `false` where possible. | Default; backward-compatible production behaviour |
| `WARN` | Perform the same validations as strict mode, but log warnings and continue with best-effort results. | Migration / staging environments |
| `STRICT` | Fail immediately on invalid direct references, type mismatches, or FEEL parse/evaluation failures. | Development, CI, and tightly governed production environments |

### Configuration

Bootstrap property:

```properties
taktx.engine.dmn.validation-mode=PERMISSIVE
```

Supported values are:

```text
PERMISSIVE
WARN
STRICT
```

If cluster-wide runtime configuration is published, its DMN validation mode overrides the bootstrap
property.

## Hit policy result shapes

| Hit policy | Result shape in TaktX | Notes |
|---|---|---|
| `FIRST` | Single result | Scalar for single-output decisions, context/object for multi-output decisions |
| `UNIQUE` | Single result | If multiple rows match, TaktX logs a warning and returns the first matched result |
| `ANY` | Single result | TaktX currently returns the first matched result |
| `PRIORITY` | Single result | Returns the first matched result |
| `COLLECT` without aggregator | List of results | List of scalars for single-output decisions, list of contexts/objects for multi-output decisions |
| `COLLECT` with aggregator (`SUM`, `MIN`, `MAX`, `COUNT`) | Aggregated scalar | Aggregation is applied to the first output column |
| `RULE ORDER` | List of results | List of scalars for single-output decisions, list of contexts/objects for multi-output decisions |
| `OUTPUT ORDER` | List of results | Same structure as `RULE ORDER` in the current implementation |

## Decision-id scoping rule

Required decision results are currently added to the evaluation scope by the **required decision
id** from:

```xml
<requiredDecision href="#categoryDecision" />
```

That means downstream expressions must start from:

```text
categoryDecision
```

and **not** directly from a bare output name such as:

```text
category
```

This avoids accidental name collisions and keeps DRG evaluation deterministic.

## Summary table

| Previous required decision | Result shape in TaktX | How a later decision should reference it |
|---|---|---|
| Single output column | Scalar value | `decisionId` |
| Multiple output columns | Context / object | `decisionId` or `decisionId.outputName` |
| List-producing decision (`COLLECT`, `RULE ORDER`, `OUTPUT ORDER`) | List | Explicit list handling, e.g. `decisionId[1]` or `decisionId[1].outputName` |

## Detailed combinations

| Previous decision outputs | Previous result shape | Example previous decision id | Example downstream reference | Notes |
|---|---|---|---|---|
| Single | Scalar | `categoryDecision` | `categoryDecision` | The previous decision result is flattened to a scalar value. |
| Multiple | Context / object | `categoryDecision` | `categoryDecision` | The full decision result can be passed downstream as a context/object. |
| Multiple | Context / object | `categoryDecision` | `categoryDecision.category` | Use `decisionId.outputName` to access a specific output field. |
| Single or multiple | List | `discountCandidatesDecision` | `discountCandidatesDecision[1]` | List-producing decisions must be handled explicitly as lists. |
| Multiple inside a list | List of contexts / objects | `discountCandidatesDecision` | `discountCandidatesDecision[1].category` | Use explicit indexing before field access. |

## Examples

### 1. Previous decision with a single output column

```xml
<decision id="categoryDecision" name="Category Decision">
  <decisionTable hitPolicy="FIRST">
    <output name="category" typeRef="string" />
  </decisionTable>
</decision>
```

Downstream reference:

```xml
<inputExpression typeRef="string">
  <text>categoryDecision</text>
</inputExpression>
```

### 2. Previous decision with multiple output columns

```xml
<decision id="categoryDecision" name="Category Decision">
  <decisionTable hitPolicy="FIRST">
    <output name="category" typeRef="string" />
    <output name="baseDiscount" typeRef="double" />
  </decisionTable>
</decision>
```

Downstream references:

```xml
<inputExpression typeRef="string">
  <text>categoryDecision.category</text>
</inputExpression>
```

```xml
<literalExpression typeRef="Any">
  <text>categoryDecision</text>
</literalExpression>
```

### 3. Previous decision with a list result

For list-producing hit policies such as `COLLECT`, `RULE ORDER`, or `OUTPUT ORDER`, downstream
expressions should treat the result as a list explicitly.

Examples:

```text
discountCandidatesDecision[1]
discountCandidatesDecision[1].category
```

TaktX does **not** implicitly unwrap list-valued decision results.

## Null and no-match semantics

Current behaviour in TaktX:

| Situation | Current behaviour |
|---|---|
| No rule matches | Returns `null` |
| Required decision result is `null` | Downstream FEEL evaluation sees that result as `null` |
| Missing output field on a context/object | FEEL resolves it as `null` |
| Missing required decision | Evaluation fails |

This means TaktX currently supports null-safe chaining semantics for the common no-match and
missing-field cases. In `STRICT` mode, these same invalid situations fail immediately instead of
silently degrading to `null` / `false`.

## Practical guidance

| Situation | Use |
|---|---|
| Previous decision has one output | `decisionId` |
| Previous decision has multiple outputs and you need one field | `decisionId.outputName` |
| Previous decision has multiple outputs and you need the full context | `decisionId` |
| Previous decision returns a list | Handle it explicitly as a list, e.g. `decisionId[1]` |
| Previous decision returns a list of contexts | Index first, then access a field, e.g. `decisionId[1].outputName` |

## Tested behaviour in this repository

This behaviour is covered by tests in:

- `taktx-engine/src/test/java/io/taktx/engine/dmn/DmnEvaluatorImplTest.java`
- `taktx-engine/src/test/java/io/taktx/engine/pi/integration/BusinessRuleTaskDrgTest.java`

The tests cover:

- single-output DRG chaining via `categoryDecision`
- multi-output DRG chaining via `categoryDecision.category`
- passing the full multi-output context via `categoryDecision`
- explicit indexing into list-valued required decision results
- missing-field access returning `null`
- no-match required decision results propagating as `null`

## Caveat

This document should not be read as a blanket claim of full formal conformance for every DMN
feature. It documents the implemented DRG result-reference behaviour and current tested semantics
in TaktX, including the configurable validation-mode behaviour.


