# Code-generation task groups, format 1

A `*.cgtask.json` file describes one fixed generation intent across one or more
captured layouts. Candidate search uses only `captureState`. Other states supply
verification labels; they are never candidate-search or feature inputs.

```json
{
  "format": 1,
  "groupId": "fixture-list-42-buy-item-2",
  "source": "fixture",
  "app": "org.autojs.test.codegen.fixture",
  "screenFamily": "list",
  "request": {
    "scope": "single",
    "attributes": ["id", "text", "desc"],
    "action": "click",
    "receiver": "auto"
  },
  "captureState": 0,
  "states": [
    {
      "stateId": "s0",
      "snapshot": "s0.layout.json",
      "mutations": [],
      "targets": [[0, 2, 1]]
    },
    {
      "stateId": "s1",
      "snapshot": "s1.layout.json",
      "mutations": ["shuffle"],
      "targets": [[0, 4, 1]]
    }
  ]
}
```

## Fields and validation

| Field | Meaning |
| --- | --- |
| `format` | Integer `1`; unsupported versions are rejected |
| `groupId` | Nonblank identifier, unique throughout one dataset |
| `source` | Exactly `fixture` or `real` |
| `app` | Application package name |
| `screenFamily` | Stable layout family used for leakage-free fixture splits |
| `request.scope` | `single` or `collection` |
| `request.attributes` | Unique subset of `id`, `text`, `desc`; an empty list is valid |
| `request.action` | `none`, `click`, `long-click`, `set-text`, `scroll-forward`, or `scroll-backward` |
| `request.receiver` | `auto` or `self`; default `auto` |
| `captureState` | Zero-based index into `states`; default `0` |
| `states` | Nonempty array; automatic survival labeling requires at least two states |
| `states[].stateId` | Nonblank identifier, unique inside this task group |
| `states[].snapshot` | Existing `.layout.json` file, relative to the task file's directory |
| `states[].mutations` | Unique nonblank change names, empty for an unchanged state |
| `states[].targets` | Nonempty array of node paths, with no duplicate paths |

Every node path is an array of nonnegative 32-bit child indices. `[]` identifies
the root; `[0, 2, 1]` means root child 0, then child 2, then child 1. Paths use the
captured child array, not the recorded `indexInParent` metadata. Every path must
exist. Each state must have the same number of targets; `single` requires exactly
one. Collection targets are sets for verification; their order does not change
the intent. Count-changing or target-absent tasks are outside this format's
current training contract, even though application queries can return no result.

Snapshot references use forward slashes and can name descendants of the task
directory. Absolute paths, drive names, backslashes, empty components, `.` and
`..` components, and links escaping the task directory are rejected. Snapshots
use the existing layout-file envelope with a `root` object; the JVM loader is
`SnapshotNode`, sharing the application's selector semantics.

All fields except `captureState` and `request.receiver` are required. Unknown
fields, duplicate JSON keys, incorrect JSON types, duplicate attributes, duplicate
state identifiers, and duplicate mutations are rejected. String fields cannot be
blank or contain control characters. The Kotlin entry point is
`TaskGroup.load(File)`; `requireLabelable()` rejects single-state queue entries.

The action records only the required capability. Runtime text, query timing,
timeouts, and classic/Flow style do not affect locating and are deliberately
omitted. For JVM verification `set-text` becomes `SET_TEXT("")`; its payload is
never executed. `receiver` preserves the application's `AUTO` versus explicit
`SELF` behavior. With `auto`, action support changing after capture can produce a
verification failure; explicit `self` preserves the application's deliberate
attempt semantics. Neither case changes the task label manually.

## Automatic labels

The explicitly enabled JVM `CodeGenDatasetExportTask` recursively reads all
`*.cgtask.json` files. Groups are sorted by `groupId`, and retained candidates by
`planKey`. Candidate generation and simplification use the normal search budget:
at most 2,000 verification attempts and 200 retained candidates per group. The
search and the rule baseline only inspect `captureState`.

Each retained plan is executed with the same `PlanVerifier` against every other
state, using that state's annotated targets. Its label is:

```text
survival = number of passing non-capture states / number of non-capture states
```

For example, a plan passing one of two later states has `survival: 0.5`. The label
is an observed fraction, not a heuristic score or a calibrated probability.
Search is not rerun on later states, and future target properties never modify
the original plan. Passing requires the exact target identity/set and the same
attribute and action-receiver constraints as application generation.

`labels.jsonl` contains one object per retained candidate:

```json
{"format":1,"groupId":"fixture-list-42-buy-item-2","planKey":"<LocatePlan.planKey>","family":"SELF_INDEX","survival":0.0,"ruleSelected":false,"failedStates":[{"stateId":"s1","mutations":["shuffle"],"reason":"WRONG_TARGET","extraCount":1,"missingCount":1}]}
```

`family` is one of `SELF`, `SELF_INDEX`, `ANCHOR_PATH`, `ANCHOR_SEARCH`, and
`CONTAINER_ITEMS`. `planKey` is the existing stable plan identity, not source
code. `reason` is the verifier's `RejectionReason` enum name. Exactly one row in
every nonempty candidate group has `ruleSelected: true`, selected by the real
`RuleRanker` on the capture state before survival is examined.

`rules.jsonl` contains one row for **every** task group, including no-candidate
groups. It records `format`, `groupId`, `source`, `app`, `screenFamily`,
`captureState` (state ID), `stateCount`, `targetCount`, `candidateCount`,
`attemptedCount`, `budgetExhausted`, `rulePlanKey`, `ruleSurvival`, and
`closestRejection`. If the search finds no candidates, `rulePlanKey` and
`ruleSurvival` are `null` and no label is fabricated. Coverage must be reported
alongside ranking metrics instead of silently dropping such groups.

`export-manifest.json` records the exporter/format versions, optional generator
commit, input and output SHA-256 values, search limits, total groups/candidates,
groups with and without candidates, and distinct snapshot count. `inputSha256`
hashes a UTF-8 stream of `relativePath + NUL + fileSha256 + LF` for all task files
and referenced snapshots, sorted by forward-slash relative path. It contains no
absolute paths, timestamps, machine identity, or layout contents. Collection
provenance such as devices and fixture version belongs in `collection.json` and
the later dataset manifest.

Output uses UTF-8, LF, and stable field/record ordering. The same frozen inputs,
code, and optional generator commit produce byte-identical output. Shared layout
trees are reused through a bounded cache of 16 snapshot files. The exporter
streams labels and rules group by group and stages all artifacts before
replacement. Parse or verification exceptions preserve the previous export;
replacement failures restore previous files, retaining recovery backups if that
restoration itself fails. The manifest checksums detect an interrupted update.

Keep captures unchanged while an export is running. Input checksums are recorded
before parsing and checked again before installing the output; a changed input
aborts the export. Malformed tasks abort the export so no partial dataset is
mistaken for a successful one. A valid task with
no candidates is recorded as a coverage failure. Actual layout contents, labels,
and any private real-application task groups stay in ignored/local directories.
Only the small handwritten test groups under `app/src/test/resources/codegen/`
are committed.

Run commands and collection workflow are in [README.md](README.md). Feature
columns and training splits are added in P3; this exporter does not compute or
approximate features in Python.
