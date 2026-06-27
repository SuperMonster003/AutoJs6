# Rhino Unsupported Utility Triage

Status: P12-31 unsupported utility-heavy triage landed.

P12-31 is a decision gate only. It does not promote multiple runtime modules in
one task. The goal is to split the remaining utility-heavy Rhino gaps into a
small P12-32 pilot candidate, deferred provider-heavy modules, and explicit
raw-parity non-goals.

## Decision Table

| Candidate | Current manifest status | Unsupported API count | Decision | Rationale | Next gate |
| --- | --- | ---: | --- | --- | --- |
| `s13n` | `unsupported` | 6 | `promote` | `s13n.bytes` is a low-risk pure JavaScript utility and does not require Android provider authority. | P12-32 promotes `s13n.bytes` first with runtime facade, types, host tests, status manifest, matrix, and docs. |
| `util` | `unsupported` | 50 | `defer` | The module mixes Node `util` aliases, pure type helpers, Android density helpers, logging helpers, and object helpers. Promoting it as one module would hide incompatible semantics. | Split into smaller pure/type/density batches after the `s13n` pilot. |
| `jsox.mathx` | `unsupported` | 25 | `defer` | The module is likely pure JavaScript, but it is a broad helper batch and is larger than the P12-32 pilot budget. | Revisit as a later pure utility batch after P12-32 proves the pattern. |
| `zip` | `unsupported` | 6 | `defer` | Archive operations require scoped paths, zip-slip denial, archive size budgets, compression-ratio budgets, overwrite policy, cleanup, and packaged smoke. | Design a scoped archive provider before runtime promotion. |
| `canvas` | `unsupported` | 1 | `defer` | A safe Node surface would need a JSON drawing provider and handle lifecycle rules. Exact raw Android `Canvas`, `Paint`, `View`, or Rhino wrapper parity is out of scope. | Design a JSON-only drawing provider; keep raw object parity as a permanent non-goal. |
| `recorder` | `unsupported` | 1 | `defer` | Recording needs microphone/screen permissions, foreground disclosure, privacy review, cleanup, and packaged behavior gates. | Design a provider with explicit permission and foreground-service policy. |
| `media` audio helpers | `partial` | 0 unsupported at module root | `defer` | S11-19 already covers screenshot/media_projection wrappers. Playback, audio manager, MediaStore, and recorder-style helpers need separate providers. | Keep screenshot wrappers; split audio/media helpers into dedicated provider tasks. |
| `mediainfo` | `unsupported` | 2 | `defer` | Media metadata reads require scoped path policy, provider-owned snapshots, and no raw `MediaMetadataRetriever` leakage. | Design a metadata provider with stable missing-file and unsupported-codec diagnostics. |
| `opencc` | `unsupported` | 34 | `defer` | S11-12 already returns stable unsupported errors because dictionary resources are not packaged as a Node text provider. | Promote only after dictionary packaging, lookup policy, and missing-resource diagnostics are designed. |
| `pinyin.fromPhrase` | `partial` module, one unsupported API | 1 | `defer` | The existing pinyin subset is fixture-backed. Full phrase, place-name, and surname dictionaries are resource-backed. | Keep the fixture-backed subset; promote full dictionary paths only through a text-resource provider. |

## P12-32 Pilot

P12-32 should implement only `s13n.bytes` first. The pilot is intentionally
small enough to verify the pure utility promotion path without adding Android
provider authority or raw object leakage.

Required P12-32 evidence:

- runtime facade for `require("s13n")` and `autojs6:compat.s13n.bytes`;
- TypeScript declarations;
- host tests for numeric, string, object, invalid, and boundary inputs;
- status manifest update for `s13n.bytes`;
- regenerated compatibility matrix;
- migration docs showing the Rhino-to-Node rewrite;
- stable unsupported or blocked-by-design notes for `s13n.color`,
  `s13n.point`, `s13n.throwable`, and `s13n.time` until each has its own
  contract.

## Non-Goals

P12-31 does not implement `zip`, `canvas`, `recorder`, `media`, `mediainfo`,
`opencc`, `pinyin.fromPhrase`, `util`, or `jsox.mathx`.

P12-31 also does not expose raw Android or Rhino objects. In particular, raw
`Canvas`, `Paint`, `View`, media recorder/player instances, metadata retrievers,
dictionary handles, archive file handles, Java exceptions, and Android context
objects must not cross into Node.

## Verification

The host gate is `verifyNodeRhinoCompatUnsupportedUtilityTriage`. It checks that
the decision table exists, every P12-31 candidate has a decision, `s13n.bytes`
is the sole P12-32 pilot, provider-heavy modules remain deferred, status
manifest notes are updated, the matrix advances the relevant next steps, the
surface manifest is design-locked rather than promoted, and the task checklist
records the evidence.
