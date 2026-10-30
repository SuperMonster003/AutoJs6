# Script API coverage checks

`RuntimeWebMediaDeviceTest` covers actual video/audio file capture across Node
main-thread imports, CJS/ESM/nested Workers and Bun. It verifies browser uploads,
media digests, video-frame/AAC decoding, native audio limits, rotation/background
stop, picker timeout and Worker exit while a parent View remains open. Run
`RuntimeWebMediaPermissionDeviceTest` separately with RECORD_AUDIO revoked, then
restore the original permission flags; revocation kills the host process.
`RuntimeWebMediaPackagedDeviceTest` produces and exercises genuine Node, nested
Worker and Bun APKs. Only numeric media evidence is retained. Preserve original
APKs, permission flags, volume and rotation before running these device suites.

`extract_exports.py` reads `script-modules.json`, the metadata the KSP processor
of the script module layer writes (`app/build/generated/ksp/appDebug/resources/autojs6/`,
built by `gradlew :app:kspAppDebugKotlin`; `--metadata` picks another file):
modules and their aliases, functions with aliases and placement, getters,
properties and Java classes. Names defined by `@ScriptInstall` hooks come from the
module's `installs` list, or until the processor writes it, from the Kotlin
registrations of the current `augment` package (rows marked `"metadata": "kotlin"`).
Without the file (no KSP build) it falls back to the `ScriptRuntime` mounts and
explicit `Augmentable` assignments and warns; `--source ksp|kotlin` forces one,
and `--compare-kotlin` writes the differences of the two sources (augment roadmap
T10). `check.py` compares those names with the associated
documentation pages, resolves their real TypeScript symbols, and queries the
plugin's actual semantic and static completion providers.

A module root listed in `policy.json` `deferredNameGate` (a decided deferral:
`imgui` until P5.2f-2, roadmap Q115 (a)) keeps its documentation, d.ts and Ace
gaps out of the failures. `summary.json` `deferredNameGate` counts them,
`surfaces.json` lists the paths as `deferred` (with the module aliases, `$imgui`),
`MATRIX.md` names the root, and the matrix columns still record the gaps. The
entry fails as stale once its root has no gap left, or no exports.

The structural checks `check-runtime-ai`, `-ai-agent`, `-bridges`, `-epub`, `-mail`,
`-speech` and `-tts` (`.mjs`) take the host's registered names from the `exports.json`
of the same `check.py` run (`host-exports.mjs`; `AUTOJS6_API_COVERAGE_EXPORTS` selects
another file), not from Kotlin sources: run `check.py` first. They fail when the file
is missing, was not read from `script-modules.json` or is older than it. CI runs them
after `check.py` in the same step. The `ai.agent` roots are not in the metadata (the
object is built by `AiAgent.createObject`) and stay listed in the script (Q46).

`node tools/api-coverage/check-runtime-bridges.mjs` compares the real Node client
exports and Bun SDK method table with the host's Pinyin, Pinyin4j, coordinate and
console control routes, all OpenCC names and image routes. OpenCC checks invoke
every real client alias and require an asynchronous host dispatch; image checks
also match the development catalog and explicitly account for client helpers.
It uses sibling Node/Bun repositories by default; two
optional positional paths select other checkouts. It also guards the Node type
index entries. This focused cross-repository check does not treat unsupported
stubs, no-ops, native alternatives or Rhino delegation as full API coverage.
Run the Node wrapper regression, Bun client tests and the host's
`RuntimeApiBridgeDeviceTest` separately for argument, error and device behavior.
`OpenccImageBridgeDeviceTest` additionally covers real CJS/ESM/Bun conversions,
ARGB pixels and alpha, transforms, encoded and PFD byte round trips, bounded JSON
results, revoked grants and execution-owned handles. Workers use their documented
`autojs6:bridge` entry in that baseline suite. `CaptureWorkerBridgeDeviceTest`
adds the real CJS/ESM/nested Worker facades, large byte transfers, Bun consent
and actual pixels across rotation, fixed dimensions, denial, timeout, cancellation
and grant checks. The screen tests use an owned color fixture and real Android UI.
The same suite checks Worker pinyin constants and compact results, native console
output plus host window controls, and parent-owned coordinate bases in both
orientations. Bun recording checks inspect actual H.264 tracks, MP4 duration,
sample timestamps and decoded fixture pixels, then verify capture restoration,
existing-file rejection and partial-file cleanup. Video sample presentation times
may be reordered by B frames; the test does not confuse decode order with display
order. `PinyinOptionsTest` covers integer-valued JSON/Rhino number representations.
`RecordingAudioDeviceTest` decodes real AAC, measures an owned tone against a
video pulse, checks microphone/playback/mixed modes and source opt-out policy,
and takes four-quadrant screenshots across rotation while recording. It also
checks notification stop, execution cancellation, independent grants and cleanup.
Run `RecordingAudioPermissionDeviceTest` separately with RECORD_AUDIO denied;
revoking that Android permission kills the host process. Restore the previous
permission and media volume after testing. The fixture discards all recorded
files and preserves only numeric evidence, not microphone recordings.

`NodeScreenRecordingDeviceTest` reuses the AAC/rotation fixtures on the Node main
thread and exercises all four audio modes in real CJS/ESM/nested Workers. It checks
concurrent parent/Worker screenshots, audio-only recorder forwarding, cross-thread
stop, stale-ID protection, Worker exit cleanup and raw capability rejection.
`NodeScreenRecordingPermissionDeviceTest` runs separately with Android RECORD_AUDIO
denied, checking that silent recording and screenshots remain usable. Run the Node
`test-screen-recording.js` and `test-opencc-images.js` wrapper regressions as well:
they cover abort/startup races and keep consent options out of image.captureScreen.
The structural bridge check includes all ten current Worker modules and compares
screen_recorder methods and catalog records with the host and Bun method sets.

`AdvancedImageBridgeDeviceTest` validates advanced images through Node CJS/main ESM,
CJS/ESM/nested Workers and Bun, using owned images and a local HTTP fixture. It checks
filters and straight-alpha pixels, HSV channel round trips, circles, multi-color
searches, similarity metrics, SIFT/ORB ROI/scale coordinates, no-match nulls, opaque
Mat/feature lifetimes, PNG bytes/quality/resource metrics and descriptor-backed file
output. It also verifies file/network/processing grants, shared image/Mat/feature
quotas, execution isolation and the async capture alias/options. Inputs, outputs
and temporary quantization files are removed; only numeric evidence is retained.
The bridge check compares all 81 host image function registrations with the Node,
Bun and Worker exports, and checks the shared portable adapter bodies for drift.
The Node test-advanced-images.js and Bun host-client tests exercise public dispatch,
Frame reconstruction, Buffer restoration and client cleanup separately from Android.

The extractor also resolves Mail's explicit default-client forwarding table,
including its synchronous-only methods and generated Async names. List suffixes,
mutation chains and dynamic aliases that are not understood must be reported as
unresolved, including changes to referenced forwarding constants.
It resolves two more fixed table shapes: tables mapped to their lower-cased
simple class names (`colors.android` / `css` / `web` / `material`) and Compose's
lazy function map (its explicit keys plus `ComposeError`; the generated NODE
catalogue stays excluded, as in the KSP `installsComputed`). Any other shape
stays unresolved. `policy.json` `dynamicRegistrations` holds reviewed unresolved
registrations only; an entry that matches none fails the check.

`check.py --source ksp` requires `script-modules.json` (the CI job builds it with
`:app:kspAppDebugKotlin` first), `--source kotlin` forces the Kotlin parser, and
the default `auto` reads the file when it exists. The Kotlin path stays a
fallback until augment P5.2 removes the old `augment` package; `summary.json`
reports the data source and the unresolved count.

Keep the host, documentation, declarations and Ace plugin checkouts beside one
another, or supply their locations explicitly:

```powershell
python tools/api-coverage/check.py `
  --documentation ../AutoJs6-Documentation `
  --declarations ../AutoJs6-TypeScript-Declarations `
  --ace ../AutoJs6-Plugin-Ace-Editor
```

The check uses Ace's bundled TypeScript compiler. `--typescript` can select a
different compiler for the canonical declarations. `--skip-ace` explicitly
limits a run to documentation and canonical types; it is not a complete pass.

Outputs are written under `build/reports/api-coverage` by default:

- `exports.json`: registered names, module aliases and unparsed expressions.
- `documentation.json`: name evidence from the associated source pages.
- `surfaces.json`: real type resolution, default/all-group semantic completion
  and receiver-aware static completion for each queried path.
- `summary.json`: scope, counts and failures.

LSP bundles are generated into an owned temporary directory for each check;
the check does not rewrite plugin source or build assets. The plugin's selected
declarations and static index must already have been synchronized. See the
commands in `tools/ace-completion/README.md` for synchronization and freshness.

Run the extractor's regression tests with:

```text
python -m unittest discover -s tools/api-coverage -p "test_*.py"
```

The tests cover nested expressions, comments and strings, aliases, ignored
exports, global assignment, unknown registration forms and scoped document
lookup; `test_script_modules.py` covers the `script-modules.json` reader
(placements, aliases, install hooks and their Kotlin fallback, the automatic
source) and, with a KSP build present, that every Kotlin registration is still
exported. Newly unparsed registration expressions fail the full gate instead of
silently reducing its denominator. `policy.json` contains narrowly named
internal/standard exceptions, explanations for dynamic registrations, page
mappings and selected returned-object probes. Public API gaps must be fixed at
their source rather than added to the internal exceptions.

This is a name and type-resolution gate, not a proof of every runtime overload.
Java prototype inventories, color tables, generated Compose catalogues and
conditional JSOX extensions have dedicated checks. The declarations repository's
`tools/verify-api-coverage.mjs` also tests invalid calls, all handwritten
declaration bodies, and isolated opt-in JSOX augmentation. The Ace repository's
`tools/ace-lsp/verify-api-coverage.mjs` tests default returned-object inference,
JSOX activation and false positives. Coordinate behavior is tested in the host's
`CoordinateScaleTest` against portrait/landscape dimensions, explicit modes,
partial base configuration and numeric boundaries.

The `Script API coverage` workflow checks related repository heads by default.
Its manual inputs can select coordinated branch names or commits when reviewing
a change spanning all four repositories. A pass against one set of revisions
does not certify a different set of published artifacts.

## Runtime coverage matrix

`runtime_matrix.py` (api-coverage roadmap P0.2, definition in section 4 of
`docs/api-coverage/ROADMAP.md`) gives every explicit export one row with a state
for Rhino, documentation, declarations, Ace, the Node main thread, Node Workers
and Bun. It writes `build/reports/api-coverage/runtime-matrix.json` (the CI
artifact) and `docs/api-coverage/MATRIX.md` (checked in, generated, never edited
by hand). Manual annotations (module and member aliases, exclusion reasons,
runtime equivalents) live only in `runtime-policy.json`.

```text
python tools/api-coverage/check.py --matrix                   # also fails when MATRIX.md is stale
python tools/api-coverage/check.py --write-matrix             # regenerate MATRIX.md
python tools/api-coverage/check.py --write-pending-baseline   # shrink pending-baseline.json
```

`--matrix` builds the matrix from the same run's `exports.json`,
`documentation.json` and `surfaces.json` (with P0.3's arity statuses). It fails
when the checked-in `MATRIX.md` differs from the generated one, when a Bun
`capability-gated` cell has no capability (`summary.missingCapabilities`, Q12 (a)),
when an exclusion has no valid reason (`summary.missingReasons`; the exclusion
list was decided on 2026-10-29, roadmap D12 / Q1, appendix D) and when the
pending cells differ from `pending-baseline.json` (roadmap Q40 (a)): the baseline
lists, per runtime column, the row ids allowed to be pending after P1.4, so a
pending cell it does not list fails (a new export, a lost runtime entry, a removed
exclusion), and so does a listed cell that is no longer pending, until
`--write-pending-baseline` shrinks the file. That option refuses to add cells;
`--write-pending-baseline --allow-new-pending` writes them after they are
registered as a pending decision on the api-coverage board;
`--pending-decision "Q122: ..."` (repeatable) names that decision in the file's
`decisions` list, which later rewrites keep. Pending counts stay
in `summary.json` `matrix.findings`. A new exclusion goes through the same
board first (D12). A
`MATRIX.md` generated from another data source (ksp versus kotlin) is reported as
`skipped`, not stale, and so is any `MATRIX.md` when the matrix lacks a column:
with `--skip-ace` the `ace` column stays `null` (`surfaces.json` `aceChecked`
false), without `check.py` reports `doc` / `dts` / `ace` do; `matrix.findings`
says why it was not compared. `MATRIX.md` is only written with every column, so
`--write-matrix` rejects `--skip-ace`. `python tools/api-coverage/runtime_matrix.py` rebuilds the
matrix from an existing report directory. `MATRIX.md` contains no commit ids or
dates, so it only changes when a state changes; the runtime checkout commits are
recorded in the JSON `sources`.

### In CI

The `Script API coverage` workflow runs `check.py --source ksp --matrix
--strict-arity --strict-types --docs-support` (roadmap Q14 / Q16 (a), P2.5, Q187 (a)) and uploads
`runtime-matrix.json` and `summary.json` as the `runtime-coverage-matrix` artifact
(the whole report directory, `docs-support.json` included, stays in
`script-api-coverage`). The documentation tables are checked against the
documentation repository the job checks out (its default branch unless
`documentation_ref` says otherwise). It then checks, against the same
matrix, the Ace plugin's runtime completion (`generate-runtime-api-queries.mjs
--check --host ../AutoJs6` and `verify-runtime-api-coverage.mjs --fresh`: every
covered Node main / Worker / `$autojs` global / Bun entry must complete, a
returned member on a value of its handle type) and the
declarations repository's runtime digests (`sync-runtime-declarations.mjs --check
--ace` and `verify-runtime-declarations.mjs --ace`; roadmap P0.6, D16).

`MATRIX.md` and the Ace `runtime-api-queries.json` follow the Node and Bun
declarations the job checks out (their default branches unless the manual inputs
say otherwise). A change there that adds, removes or reclassifies an entry, and a
host change to the exports or returned objects, makes the job fail until the
derived files are regenerated and committed: `python tools/api-coverage/check.py
--write-matrix` in the host (with `--write-pending-baseline` when cells stopped
being pending), then `node tools/ace-lsp/generate-runtime-api-queries.mjs`
in the Ace plugin (it reads the host report by default) when a covered entry changed,
and `node generator/runtime-support.js` (then its usual generation) in the
documentation repository when a page's table changed.
`--strict-arity` fails only on d.ts overloads disjoint from the implementation
arity; `wider` / `narrower` stay warnings in `arity.json`. `--strict-types` fails
only on the unsafe type levels that `type-map.json` `reviewed` does not cover
(see "Types"); the Node declarations comparison is reported only.

Runtime entries are declared entries, not behavior proofs (Rhino-equivalent
behavior is reviewed in P1). The sources, all at the paths the roadmap names:

- Node: `list-runtime-surfaces.mjs` lists, with the TypeScript checker (Ace's
  bundled compiler unless `--typescript`), the members of every ambient module of
  `docs/nodejs/types/autojs6-node/index.d.ts` (one nested level as dotted names,
  `converter.bytes.toBytes`) and the `$autojs.<module>` entries. `autojs6:<name>`
  is tried before the bare name. Module statuses come from `docs/HOST-API.md`:
  the tables of sections 1-3 are bridged (detail kept as `hostApi`), the list in
  section 4 is local-shim; modules it does not list are bridged. An entry in a
  module listed in `runtime-policy.json` `node.nativeModules` (Node's own
  `timers`, `http`, `util`, ...) is `not-portable` / `runtime-native` with the
  native name as `equivalent`. `node-capability-catalog.json` adds the declared
  Node capabilities.
- Node Worker: the `hostModuleMethods` table of `app/src/main/cpp/node_bridge_sources.cpp`
  is the set of modules a Worker resolves (`Object.keys(...)` of the main-thread
  facade, or an explicit member list such as `database: ["open"]`); Node's
  native modules are available as well. Other modules (local shims, `autojs6:compat`)
  are pending in Workers.
- Bun: members of the `HostClient` namespaces of `host-client.d.mts`, plus client
  methods (`toast`). Every entry is `capability-gated`; the capability is the
  related grant (same prefix, or `bun.namespaceCapabilities`) whose method table
  (`HOST_API_METHODS`, read from `host-client.mjs`, which `host-client.d.mts` only
  types) contains the member, else the only related grant, else the explicit
  `bun.memberCapabilities` mapping (`"images.read": {"capability": "image.files",
  "basis": "calls readImage"}`) for client-side helpers and renamed wrappers whose
  namespace has several grants (`images`: `image.process` / `image.files` /
  `screen.capture`). A mapping naming an unknown grant leaves the cell without a
  capability; an unused mapping is a policy conflict. A namespace without
  any related grant holds client-side helpers and is `local-shim` (`colors`,
  `util`, `jsox`), except its members `bun.memberCapabilities` maps to the grant
  they call the host through (`util.java.array` -> `java.interop`), which are
  `capability-gated`.

A row is looked up in its placement module, then through `runtime-policy.json`
module aliases (`automator` -> `accessibility`; a dotted Bun alias names an object
nested in a namespace, `Mathx` -> `jsox.mathx`), a global-object row through its
owner module, then through the other placements of the same function, then the
`globalModules` / `globalNamespaces` and the opt-in `autojs6:compat` facade. Only
cells still pending take an exclusion rule; a rule matching a covered cell is
reported as shadowed, a rule matching nothing as unused (`override: true` forces
a rule). A rule with `memberSource` matches only returned-member rows of that
source: `java-prototype-objects` (Q17 (a)) excludes the members of `kotlin-class`
objects (Node / Bun have no such Java object), `augment-prototype` handles stay
pending unless `returnedObjects` names their runtime handle type (below). Prototype
members (`script-prototype`, Q18) are module members: they are looked up as
explicit exports are, and only rules without `memberSource` (or with
`script-prototype`) apply to them.

An entry whose every call signature returns `never` or `Promise<never>`
(`list-runtime-surfaces.mjs` marks it `rejects`) is an explicit refusal, not
coverage (roadmap Q26 (a)): the lookup prefers any other entry of the row, and
when only rejecting entries exist the cell is `pending` with `rejects: true` and
the entry kept (`app.launchDual`, `console.input`, `files.open`, `storages.names`).
Such a row leaves pending only through an exclusion rule with a reason; the
excluded cell keeps `rejects`. `summary.rejects` counts the cells per column.

`runtime-policy.json` `returnedObjects` maps an `augment-prototype` object to the
handle type its runtime producers return (roadmap Q27 (a)), and the members are
compared one by one: a member the type declares is covered, others stay pending.

```jsonc
"Flow": {"node": {"module": "autojs6:flow", "type": "Flow<unknown>"},     // type in the module's namespace
         "bun": {"namespace": "flow", "type": "RuntimeFlow.Flow<unknown>",  // type exported by host-client.d.mts
                 "capability": "flow.control"}}                             // grant of the producing call
```

`list-runtime-surfaces.mjs --policy` reads the mappings and lists the members of
each type (type arguments only serve the Ace queries). Node cells take the
module's status (`bridged` / `local-shim`) and are available in Workers when the
module is a Worker host module (`storages` is not); Bun cells are
`capability-gated` with the mapping's capability, else the namespace's only
related grant. A type that does not resolve fails the run; a mapping for an
object without rows is an unused policy conflict.

### Row ids

Row ids are stable; later checks, the documentation "runtime support" tables and
the Ace runtime queries join on them.

- An explicit export's id is its `exports.json` `records[].api`: `<module>.<name>`
  for a module member (`images.read`, `files.batchRename.apply`), `<name>` for a
  member of the global object (module `""`: `toast`, `sleep`), `<target>.<name>`
  for a name an install hook places on a global value (`wait.async`). The id does
  not change when the denominator switches from the Kotlin registrations to
  `script-modules.json` (augment T10). Each placement of a function is its own row
  (`captureScreen` and `images.captureScreen`); the row lists the other placements
  of the same owner, implementation and kind in `aliases`.
- A member of a module prototype (Q18 (a), "Module prototypes" below) has the id
  `<module>.<member>` of the module path, as scripts reach it (`files.isFile`,
  `device.getBattery`); the module object's own names shadow the prototype, so
  these ids never repeat an export id.
- A member of a returned object (P0.4) has the id `<Object>#<member>`
  (`UiObject#click`, `ImageWrapper#getWidth`). `#` never occurs in export ids.

### runtime-matrix.json (schema `autojs6-runtime-matrix-v1`)

```jsonc
{
  "schema": "autojs6-runtime-matrix-v1",
  "generatedBy": "tools/api-coverage/runtime_matrix.py",
  "dataSource": {"kind": "ksp", "file": "app/build/.../script-modules.json"}, // exports.json dataSource
  "sources": {"node": {"root": "...", "commit": "...", "files": []}, "bun": {...}, "typescript": "6.0.3", "documentation": true, "surfaces": true},
  "columns": ["rhino", "doc", "dts", "ace", "node_main", "node_worker", "bun"],
  "summary": {
    "rows": 1368, "exports": 1368, "prototypeMembers": 0, "returnedMembers": 0,
    "byColumn": {"node_main": {"bridged": 0, "pending": 0}},             // every column, every value
    "pending": {"node_main": 0, "node_worker": 0, "bun": 0},
    "rejects": {"node_main": 0, "node_worker": 0, "bun": 0},  // cells of entries that only reject (Q26)
    "missingReasons": [],      // excluded cells without a valid reason / equivalent
    "missingCapabilities": [], // Bun capability-gated cells without a capability (fails --matrix)
    "policyConflicts": []      // exclusion rules shadowed by a real runtime entry, unused rules
  },
  "rows": [ROW]                // sorted by id
}
```

```jsonc
// ROW
{
  "id": "images.read",
  "kind": "export",            // or "prototype-member" (Q18), "returned-member" (P0.4)
  "module": "images",          // placement, "" = global object; null for returned members
  "member": "read",
  "ownerModule": "images",     // module that defines the export
  "aliases": [],               // other row ids with the same owner, implementation and kind
  "exportKind": "Functions",   // Functions / Getters / Properties / GettersAndSetters / JavaClasses
  "registration": "@ScriptFunction",
  "source": "app/src/main/java/.../ImagesModule.kt", "line": 63,
  "metadataSource": "ksp",     // or "kotlin": records[].metadata (D9)
  "surface": "public",         // or "internal" / "standard" (policy.json internalExports / standardExports)
  "arity": null,               // reserved for P0.3: copied verbatim from records[].arity
  "object": null, "producers": [], "memberSource": null,  // returned members (P0.4); "script-prototype" for prototype members
  "rhino": "native",
  "doc": "heading",            // none / mention / heading; signature / example reserved (P2)
  "dts": "name",               // none / name; arity (P0.3) / typed (D9 step 2) reserved
  "ace": "static",             // none / semantic (default and all-group LSP) / static (semantic + static index)
  "node_main": CELL, "node_worker": CELL, "bun": CELL
}
```

```jsonc
// CELL: status is bridged / local-shim / capability-gated / not-portable / denied / pending
{"status": "bridged", "module": "autojs6:images", "member": "read", "async": true, "via": "same-name", "hostApi": "bridged"} // Node
{"status": "capability-gated", "namespace": "images", "member": "getSize", "capability": "image.process", "async": false, "via": "same-name"} // Bun
{"status": "not-portable", "reason": "runtime-native", "equivalent": "node:timers.setTimeout", "module": "timers", "member": "setTimeout", "via": "owner-module"}
{"status": "not-portable", "reason": "sync-only", "equivalent": "Node worker_threads / Bun Worker with message passing", "rule": "sync-threads"}
{"status": "bridged", "module": "autojs6:flow", "type": "Flow<unknown>", "member": "then", "async": true, "via": "handle-type"} // returned member
{"status": "pending", "rejects": true, "module": "app", "member": "launchDual", "via": "same-name"} // entry only rejects
{"status": "pending"}
```

`member` is `"()"` when the module value itself is the callable entry. `async` is
true when every declared signature of the entry returns a thenable (Promise, Flow
chain). `via` tells how the entry was found: `same-name`, `owner-module` (a
global-object row in its owner module), `alias` (another placement of the same
function), `module-alias` / `member-alias` (a `runtime-policy.json` mapping),
`global-module`, `compat` (`autojs6:compat`), `client-member` (Bun), `handle-type`
(a returned member through `returnedObjects`). Cells set by an
exclusion rule carry `rule` (and `note` when the rule has one) instead.
`reason` is one of `security`, `sync-only`, `java-prototype`, `runtime-native`,
`runtime-builtin`, `rhino-only`, `ui-thread`; a `runtime-native` cell must carry
`equivalent`.

### Inputs from P0.3 and P0.4

- P0.3 (arity): `extract_exports.py` adds `arity` to `records[]`; the matrix copies
  it unchanged. `check.py` adds `rows[].arity.status` to `surfaces.json` (`arity.py`
  statuses); `dts` becomes `arity` for `match` and `wider`, where every count the
  implementation accepts is declared, and stays `name` otherwise.
- P0.4 (returned objects): `exports.json` gains a top-level `returnedObjects` list;
  each member becomes a `returned-member` row with the id `<object>#<name>`:

```jsonc
"returnedObjects": [{
  "object": "UiObject", "class": "org.autojs.autojs.core.automator.UiObject",
  "producers": ["selector.findOne", "findOne"],   // row ids returning the object
  "memberSource": "kotlin-class",                 // or "augment-prototype" / "policy"
  "members": [{"name": "click", "kind": "Functions", "source": "...", "line": 42, "arity": null}]
}]
```

  Until the list exists the matrix has no returned-member rows. Their runtime
  cells come from the `runtime-policy.json` `returnedObjects` handle types and
  exclusions (`java-prototype` is allowed); unmapped members are `pending`.

### Documentation runtime support tables (P0.5)

Module pages of the documentation repository carry a generated "运行时支持" table
at the top (roadmap D13), between `<!-- runtime-support:begin <roots> -->` and
`<!-- runtime-support:end -->`. The renderer lives with the other documentation
generators (`AutoJs6-Documentation/generator/runtime-support.js`, roadmap Q7 (a);
usage and the reader-facing wording in that repository's `generator/README.md`);
the host only checks:

```text
python tools/api-coverage/check.py --docs-support    # also with --matrix, --skip-ace or --source
```

`--docs-support` builds `runtime-matrix.json` as `--matrix` does (comparing
`MATRIX.md` only when `--matrix` is given too), runs the generator's `--check`
against it, cross-checks every block's row count with the matrix rows of its roots
(`docs_support.py`) and writes the generator's report to `docs-support.json`
(`summary.json` `docsSupport`). The documentation checkout is `--documentation`,
else `AUTOJS6_DOCUMENTATION_ROOT`, else the sibling `AutoJs6-Documentation`.

- A block names module roots, the root the documentation gate looks pages up by:
  the first segment of the placement module, the owner module for a member of the
  global object (`captureScreen` -> `images`), else `global`. Prototype members
  count as exports of their module (Q22 (a)).
- Returned-object members (`<Object>#<member>`, Q20 (a)) belong to the object's
  first documentation page (the matrix row's `page`). On a type page the block
  names the object with a trailing `#` (`<!-- runtime-support:begin ImageWrapper# -->`)
  and shows its members like a module; a module page's block lists the objects
  documented on it in a "返回对象" table, one line per object (a column of Java
  object members all "不适用 (需要 Java 对象)" reads as one cell). The cross-check
  also compares that table's member count (`objectRows`).
- The documentation name gate ignores block contents (`docs_support.strip_blocks`,
  line numbers kept), so a table never turns a missing name into a mention.
- A block generated from the other export source (ksp / kotlin, recorded on its
  second line) is `skipped`, as `MATRIX.md` is.
- Every module root and returned object has a table since P2.5 (83 pages): the
  check runs the generator with `--require-all`, so a module root without a block,
  or an object whose first documentation page has neither its `<Object>#` root nor
  a module block, fails (`uncoveredRoots` / `uncoveredObjects`). A new module or
  returned object gets one with `node generator/runtime-support.js --insert
  <page>:<root>` in the documentation repository.
- After a matrix change that touches a page with a block, run
  `node generator/runtime-support.js` in the documentation repository (then its
  usual generation) and commit both repositories.

## Arity

`arity.py` (api-coverage roadmap P0.3, decision D9) gives every function record
and every callable or constructable module of `exports.json` its argument-count
ranges: `records[].arity` and `modules[].invokeArity` / `constructArity` are
`{source, overloads: [{min, max, basis, guard?}], reason?}`, `max` null meaning
unbounded; `arityStats` counts them.

- `source: "ksp"` (rows read from `script-modules.json`): `basis: "declared"` is
  the `arity` a single raw vararg overload declares (or the one of an install
  function), `basis: "params"` the parameter list (required parameters to all
  parameters; a vararg parameter leaves `max` unbounded), `basis: "vararg"` a
  single raw vararg overload without a declared arity, whose body checks the count
  itself (`[0, n)`, not compared).
- `source: "ensureArguments"` (Kotlin registrations): the `ensureArguments*` guard
  that starts the implementation body, after runtime checks such as
  `checkRuntime(runtime)` and lambda wrappers such as `guarded {`, following one
  same-file delegation (`guard`: OnlyOne / Length / LengthInRange / AtMost /
  AtLeast / IsEmpty / NotEmpty; `unwrapArguments` is an unchecked vararg).
- `source: "unknown"` with a `reason`: any other body (checks in another class,
  a guard inside a branch, a computed bound), the mail forwarding table and the
  Compose function map.

`dts-signatures.mjs` gives `verify-surfaces.mjs` the ranges of the d.ts call and
construct signatures of each query (`declaration.signatures.call` /
`construct`; a rest parameter of tuple type counts its elements). After the
surface check `check.py` compares the two and writes `arity.json`
(`rows`, `counts`, `stats`, `crossCheck`), adds `rows[].arity` to
`surfaces.json` and puts the counts into `summary.json` `arity`:

| status | rule |
|---|---|
| `fail` | a d.ts overload no implementation overload intersects (`disjoint`) |
| `wider` (warning) | d.ts overloads accept counts outside every implementation range (`rejected`) |
| `narrower` (warning) | an end of the implementation ranges is not accepted by any d.ts overload (`undeclared`): the lowest count, or the highest finite count, or an unbounded end that is not an undeclared vararg parameter |
| `match` | none of the above |
| `vararg` / `unknown` / `no-dts` | not compared: unchecked vararg, unknown implementation range, no explicit d.ts call signature |

Only the ends are compared for `narrower` because metadata ranges are spans: a
declared arity or optional parameters may cover counts the body rejects
(`images.clip` accepts `(image, region)` and `(image, x, y, width, height)`, its
parameters span 2-5). Counts between two implementation overloads are real gaps,
so `wider` and `fail` use every count. The matrix `dts` column is `arity` for
`match` and `wider`. The comparison reports only; `--strict-arity` (passed in CI)
makes `fail` rows gate failures. With `script-modules.json` as the data source, `crossCheck`
lists the APIs whose ksp ranges differ from the `ensureArguments*` guard of the
current Kotlin registration (`differs`), and undeclared varargs whose Kotlin
implementation has a guard (`vararg-undeclared`, possibly a missing annotation);
the new module layer may narrow arities on purpose, so these are leads for the
augment roadmap, not failures.

## Types

`kotlin_types.py` (roadmap P2.4, the second step of decision D9) reads the Kotlin
type script-modules.json gives every parameter, return value and accessor of a
`@ScriptFunction`, `@ScriptGetter` and `@ScriptProperty`, and the invoke /
construct overloads of callable modules, and maps each with `type-map.json` to a
descriptor: a TypeScript type (`kotlin`, `collections`), a class of
`returned-objects.json` (compared with the d.ts type of its probe), another class
(looked up by its qualified name in the d.ts, `org.opencv.core.Point`), or
`unmapped` with a reason (`kotlin.Any?`, dynamic `ProxyObject`, a Java `Set`).
`exports.json` carries them as `typeRows`; `@ScriptInstall` names have no types
(`typeStats.notTyped`). For parameters the table separates the `nominal` type
(what the d.ts is expected to say) from the `accepted` one (what the `ScriptArgs`
converter takes: numbers for a String parameter, numeric strings for a Double);
a nullable Kotlin type adds `null`, and `undefined` for parameters; `open` marks
types without detail (`object`, `Function`, `{}`, arrays, classes), inside which a
more specific d.ts type is a refinement.

`verify-surfaces.mjs` declares those TypeScript types in its query file and
`dts-types.mjs` compares them with the d.ts parameter types of each position (over
all overloads; the `undefined` of an optional parameter is left to the arity
comparison) and the d.ts return types (a `void` result is `undefined`). It writes
`types.json`; `check.py` adds the summary, the per-module counts and the strict
failures, and puts the counts into `summary.json` `types`:

| level | parameter | return value / accessor |
|---|---|---|
| `match` | equal (assignable both ways) | equal |
| `refined` | inside the nominal type, with its null | a refinement: literals of a primitive, an interface in `object`, a subclass, a tuple or array of a known element |
| `coerced` | outside the nominal type but accepted by the converter | - |
| `narrow` | rejects values the implementation takes (usually `null`; harmless) | omits values the implementation returns (usually `null`; unsafe) |
| `wide` | takes values the implementation rejects (unsafe) | declares values never returned (harmless) |
| `error` | disjoint | disjoint |
| `unmapped` | not compared: no Kotlin type, an undeclared class, or a d.ts `any` / `unknown` / unresolved type parameter (`dts-any`) | same |

A row is `typed` when at least one position was compared and none differs,
`differs` when one is `narrow`, `wide` or `error`; `no-dts` rows have no d.ts
signature. The matrix `dts` column is `typed` for a `typed` row whose arity is
`match` / `wider` (accessors have no arity), and MATRIX.md gives the typed /
compared counts per module (column "类型 (ksp)").

`--strict-types` (in CI since Q187 (a)) fails on the unsafe directions,
`type-map.json` `strictLevels`: parameters `error` / `wide`, return values `error` /
`narrow`. `type-map.json` `reviewed` lists known differences with an owner or a
decision (fnmatch patterns of `<api> parameter <n>`, `<api> return`,
`<api> value`, each for one level): they do not fail, and an entry no position
matches any more is stale and fails. Entries on non-strict levels only document
the difference (`types.json` `reviewed`).

With `--matrix` (or `--docs-support`) `check.py` also compares the Node runtime
declarations (Q186 (a); `verify-runtime-types.mjs`): the rows the matrix covers in
Node main (bridged / local-shim with a module and member) against the same Kotlin
types in `docs/nodejs/types/autojs6-node`, return values and accessors awaited
(the bridge returns promises). Classes have no Node declaration and stay unmapped;
the counts go to `summary.json` `types.node` and `runtime-types.json`, reported
only. `node tools/api-coverage/test-dts-types.mjs` checks the levels on a small
declaration file, `test_kotlin_types.py` the mapping, rows and strict failures.

## Returned objects

`returned_objects.py` (P0.4) lists the objects script functions return and the
members scripts reach on them; `returned-objects.json` names each object (the
matrix row prefix), its class, the member list source, the d.ts probe expression
and the documentation pages. `extract_exports.py` writes them as
`returnedObjects` (the schema of "Inputs from P0.3 and P0.4" above, plus
`documentation`, `probe`, `complete`, `externalBases` / `dynamic`) and
`returnedObjectsStats` (counts, incomplete objects, configuration problems, and the
return types of `script-modules.json` that are host classes not listed yet
(`unlistedHostTypes`) or classes outside the host sources (`externalTypes`,
Java objects whose members are not enumerated)).

- Producers: export rows whose `script-modules.json` overloads or getters return
  the class, plus the `producers` of the entry (functions declared as
  `kotlin.Any`; `X.()` / `X.new()` for a callable or constructable module).
- `kotlin-class`: a Java object seen through Rhino's NativeJavaObject: public
  methods (overloads merged, Kotlin default parameters optional only with
  `@JvmOverloads`), public fields, Java record components, and Kotlin properties
  as their name with the JVM accessors as `aliases` (`isEnabled`: `isEnabled`,
  `setEnabled`, `enabled`), declared by the class and its supertypes in the host
  sources. Supertypes outside the sources are listed in `externalBases`; their
  members (Android `Paint`, androidx `AccessibilityNodeInfoCompat`, ...) are not
  enumerated. `java.lang.Object` members are left out, and so are `exclude`.
- `augment-prototype`: a Scriptable the runtime builds: names registered with
  string literals by `defineProperty` (getter / getter and setter / value /
  function), `defineFunction`, `defineOp` (with its range), `function`,
  `method` (with its aliases), `listenerFunction`, `defineFlagShortcut`;
  `helperSuffixes` adds the variants a helper also defines (`defineOp`:
  `nameAsync`); name arrays passed to `defineFunctionProperties` or listed in
  `nameArrays`; collections iterated into a helper
  (`X.keys.forEach { name -> method(name) ... }`, `for (e in listOf(...))`) when X
  resolves to a literal list or map in the sources; constants such as
  `StringReadable.KEY`. Nested classes, helper definitions and `skipFunctions`
  belong to other objects. Other computed registrations are listed in `dynamic`
  and make the object incomplete; `dynamic` in the entry describes run-time
  forwarding the scanner cannot see (`Color` forwards any colors function).
- `policy`: the `members` of the entry.
- `plugin:<name>`: the list `catalog` of a plugin contract source (`policy.json` `pluginExports`, below):
  the Compose catalogue modifiers of `ComposeModifier`, its shortcut accessors of `ComposeNode`;
  `catalogProducers` makes every row the source adds a producer.

`check.py` checks every member (and its aliases) by name against the
documentation pages, the d.ts type of `<probe>.<member>` and Ace completion, in a
second surface run under `returned-objects/`, and writes `returned-objects.json`
(`counts`, per-object probe state, `failures`, `rows` with `doc` heading /
mention / missing, `dts` explicit / missing / no-probe, `ace` default /
all-groups / none). Members of Java objects are completed from the generated
Java declaration groups, which the default Ace library set leaves out: `ace`
`all-groups` is counted (`aceAllGroupsOnly`) but is not a failure there, while
the matrix `ace` column keeps its definition (both library sets, so `none`).
Since P2.3 every member in `failures` (and in `prototypes.failures`, below) fails
the gate ("Returned object member below the name gate: ..."), and so does every
configuration problem of `returnedObjectsStats` / `prototypesStats` (an `exclude`
name matching no member, an unknown module). The matrix gets `<Object>#<member>`
rows with the object's first documentation `page`; their runtime cells stay
`pending` until `runtime-policy.json` maps or excludes them.

Members of Java facade objects that are not script API are excluded with an
`excludeNote` (Q57): Android framework callbacks the system calls on the wrapped
object (the `Dialog` `on*` / `dispatch*` members), overrides of members of an
external base whose members are not enumerated (`UiObject.getChild`), the host's
lifecycle and scheduling side (`ScriptEngine.init`, `TimedTask.createPendingIntent`),
static factories and runtime references. `probe` must resolve to the object type:
`ai.session.().await` takes the fulfilled value of a Promise (Ace completes it as
`(await ai.session()).`), `ocr.detect.().[]` an element of an array result (Ace completes
`ocr.detect()[0].`), and an overloaded producer whose first signature returns
another type is probed through the class (`probeNote`).

### Module prototypes (Q18 (a))

22 modules declare `@ScriptPrototype` (script-modules.json `prototype`): the module
object's prototype is a Java object (`files` -> `runtime.files`, `device` ->
`runtime.device`), a script object (`continuation`) or a name lookup (the global
Java classes, the WebSocket constants). Scripts call its members as module
members, so `returned-objects.json` `prototypes` lists one entry per module (module
path as in `exports.json`, `""` for the global object) and `extract_exports.py`
writes the members as `prototypes` (with `prototypesStats`); with
`script-modules.json` a `@ScriptPrototype` module missing from the list is a
problem in `summary.json` `returnedObjects.problems`.

- `memberSource` `kotlin-class` / `augment-prototype` / `policy` read the members as
  for returned objects. Names the module object defines itself (its explicit
  exports) shadow the prototype and are left out (`shadowed`).
- `exclude` drops public Java members that are not script API (`excludeNote` says
  why: Android objects, callbacks the host calls, runtime bookkeeping, the state
  behind documented setters); an `exclude` name matching no member is a problem.
- `skipConstants` drops upper-case fields (`mime`'s MIME type constants), and
  `notEnumerated` describes what the prototype holds without listing it: the
  color tables (`Color`, `colors`), `util.versionCodes`, the WebSocket constants and
  the 156 global Java classes, as the color constants are outside the name gate.

Each member is a matrix row `<module>.<member>` (kind `prototype-member`,
memberSource `script-prototype`, registration `@ScriptPrototype`), checked like
an export: its module's documentation pages (`policy.json` `documentationModules`,
or the entry's `documentation`, such as `eventEmitterType` for `events`), the d.ts
type of `<module>.<member>` and Ace completion in the default and all-group
library sets and the static index. The surface run is the returned-object one;
`returned-objects.json` reports them under `prototypes`. Members declared only on
the Java class are not in the default Ace library set, so the module types of the
d.ts declare them explicitly. In the documentation runtime support tables they
count as exports of their module (Q22 (a)).

### Plugin contract exports (P5.2c, Q75 (a))

`plugin_exports.py` adds the names a plugin contract defines although no host module registers
them one by one; `policy.json` `pluginExports` lists the sources. The only kind,
`compose-ui-catalog`, reads the Compose UI catalogue the compose module installs at run time
(the KSP `installsComputed` of `compose`) from the contract sources in the host,
`plugin-api/compose-ui-api` (`V1Catalog`, `ComposeUiV2`, `AndroidViewInteropV1` and the
vocabulary constants they reference); the 3-Block Compose plugin only renders it:

- the NODE components of the interop catalogue (55: V1 without the command-only `Snackbar`, V2,
  `AndroidView`) become export records `compose.<Name>` with `metadata` / matrix `metadataSource`
  `plugin:compose` and registration `pluginExports` (no arity: `unknown`);
- the modifiers (20) are the members of the returned object `ComposeModifier` (`compose.modifier()`),
  the shortcuts (8, `w` / `h` / `padding` / ...) the accessors of `ComposeNode`, which every node
  factory returns (`returned-objects.json` entries with `memberSource` `plugin:compose`).

A constant of the vocabulary objects that the catalogue functions no longer reference, or a
reference that is not a constant, is a problem and fails the check. `root` is relative to the host
or absolute, and `rootEnvironment` names a variable overriding it. Without the sources the
source is `skipped` (`summary.json` `pluginExports`, `exports.json` `dataSource.pluginExportsSkipped`):
its rows and objects are left out, `MATRIX.md`, the pending baseline and the documentation tables are
reported as skipped instead of compared, and neither `MATRIX.md` nor the baseline is written.

Runtime cells follow `runtime-policy.json` as for other rows: the component functions are members of
the Node / Bun `compose` facades; `ComposeModifier` and `ComposeNode` map to the handle types
`RuntimeCompose.Modifier` / `RuntimeCompose.Node` (Bun) and `RuntimeCompose.Modifier` / `Node` (Node).
Node's `RuntimeCompose` is a global namespace of the compose declarations that `autojs6:compose` does not
export: the mapping says `"typeScope": "global"`, the type is looked up among the global namespaces of the
module's declaration file, and the cell keeps `typeScope` so the Ace queries name the type bare.

## Language runtime APIs (Q77)

The Python, Java, Kotlin and Lua runtime plugins give their scripts own APIs (PLUGIN-APIS.md 2.4: Python
46 functions with 54 constants and 10 exception classes, Java 17 methods of 7 types, Kotlin 13 of 6, Lua 12).
They have no Node / Bun columns, so they stay out of the runtime matrix; `language_runtimes.py` keeps them
and their documentation pages (`api/pythonRuntime.md`, `javaRuntime.md`, `kotlinRuntime.md`,
`luaRuntime.md`) in step (roadmap P5.2d, appendix C Q77 (a)):

```text
python tools/api-coverage/language_runtimes.py            # check, one line per runtime
python tools/api-coverage/language_runtimes.py --write    # after a plugin API change: rewrite the name list
```

`policy.json` "languageRuntimes" names per runtime the plugin checkout (`repository`, a sibling directory
name or a local path; `--plugin NAME=PATH` overrides it), the registration source and its reader (`kind`),
the documentation page and the name prefix used there. `language-runtimes.json` is the committed name list
both checks compare with:

- source: the names the checkout registers. `python-package`: `__all__` of `autojs6/__init__.py` and of
  each submodule it lists (functions with their signatures, constants with their values, classes; a value is
  a literal or `+ - * //` of earlier constants, anything else is listed as `...`). `jvm-entry-aar`: the
  plugin's frozen `protocol/jvm-source-api.aar` (Java is at Entry API 4, Kotlin at Entry API 2, so the
  host's current `JvmEntryApi.kt` cannot stand for both), every public type and non-synthetic method
  reachable from `AutoJsJvmEntry` through the parameter, return and thrown types of its package.
  `lua-module`: the table `install_autojs_module` builds, by following its `lua_newtable` /
  `lua_setfield` stack operations. A missing checkout prints `source skipped` and does not fail.
- doc: every function needs a `### <prefix><name>(<parameters>)` heading; Python parameter names, `*`
  markers and which ones have defaults must match the signature, JVM parameter counts the descriptor.
  Constants, classes, types and values need a heading or a table row whose first cell is
  `` `<prefix><name>` ``. Names the page lacks, API headings or rows the list lacks and a missing page
  fail (without a documentation checkout the doc part prints `skipped`); with
  the empty JVM prefix every dotted `###` heading and every table row starting with a code name counts,
  so the Java and Kotlin pages keep other tables (error codes) without that form.

- stubs (roadmap P5.2e): each policy entry's `stubs` maps a surface to a path, `plugin` in the plugin
  checkout and `ace` / `ace-index` in the Ace Editor checkout (`--ace`, else `AUTOJS6_ACE_EDITOR_ROOT`,
  else the sibling directory). The stub must name exactly the listed API: Python
  `docs/python/types/autojs6/*.pyi` and its Ace copy (public definitions of `__init__.pyi` and the
  submodules it re-exports, signatures with defaults written as `...`, constants with the plugin's value as
  `NAME: Final = 64`, appendix C Q142 (b)), Lua `docs/types/autojs.lua` and its
  Ace copy (LuaLS `---@meta`: `function autojs.x(...)` and assigned values), the Java Entry API jar Ace gives
  ECJ (read like the AAR) and the `AutoJsJvmEntry` / `Jvm*` modules and class globals of Ace's Java and
  Kotlin indices (parameter counts). A missing checkout prints `stubs skipped`, a checkout without the stub fails.
- host (appendix C Q109 (c)): `hostApi` names the host's `JvmEntryApi.kt`; every type and method of the
  plugin's frozen AAR must be declared there with the same parameter count (`host FAIL` otherwise). Host
  methods an older Entry API lacks are printed as `host-only` (Kotlin, at Entry API 2, lacks `args` and
  `clipboard`).

CI checks out the four public plugin repositories and the Ace Editor and runs the script after the unit
tests.

## Physical-device checks

Build and install matching current host and instrumentation APKs with `adb install
-r -t`, plus the current Ace plugin APK. Select APKs using `output-metadata.json`
and record their hashes; an unchanged version name does not prove the installed
bytes match the current checkout. Do not clear application data.

The existing device runner checks every selected test's completion identity and
rejects failures, skips and missing results even when `am instrument` exits zero:

```text
python tools/sdk37/physical_device.py instrument --serial SERIAL --suite coordinate-api --expected-target 37 --output build/reports/device-coordinates
python tools/sdk37/physical_device.py instrument --serial SERIAL --suite ace-api --expected-target 37 --output build/reports/device-ace-api
```

Each output directory must be new. Coordinate tests temporarily rotate an owned
UI script through 0/90/180/270/0, measure Android's actual display independently,
and restore the original rotation settings. They use a fresh Rhino runtime per
base configuration and also retain one UI runtime throughout the rotations.
Setter failure recovery and the established integer-conversion behavior are
separate cases. Detailed results are written to the host's external-files
`coordinate-scale-device.json`.

The host Ace test loads the installed plugin through the production ABI, restores
the previous settings after testing the genuine defaults, and queries the public
LSP completer with its normal budgets. It checks returned-object completion and
JSOX activation/removal, saving `ace-api-host-device.json` in the same directory.
The plugin's `AceApiCoverageDeviceTest` additionally checks actual popup geometry
and captures screenshots, so a correct candidate list cannot hide an overlapping
documentation tooltip. These are physical-device results only when run on an
identified physical serial; desktop jsdom checks are complementary.

The runtime bridge check also runs `check-ui-compose.mjs`. It compares the 54
portable Compose factories plus AndroidView, the 12
host operations with the development catalog/provider discovery, Node/Bun facade
bytes and Worker window cleanup. Node's `tools/nodejs/api/test-ui-compose.js`
executes real desktop CJS/ESM Workers and checks state/ref/memo, bindings, late
creation replies and cancellation. `RuntimeUiComposeDeviceTest` covers native
interaction, dialogs, Snackbar, floating geometry, grants, Worker cleanup and
Activity recreation in the real runtime processes. Full evidence and the native
interop boundaries are in `docs/dev/runtime-ui-compose-2026-10-18.md`.
The subsequent `RuntimeUiInteropDeviceTest` checks XML attachments, native Views,
factory retention, execution ownership, detached-container cleanup and rotation.
`RuntimeInteropPackagedDeviceTest` builds actual Node/Bun project APKs, verifies
their binary Manifest permissions and exercises the installed outputs. The eight
native View operations are checked against the permission catalog as well.
See `docs/dev/runtime-ui-interop-2026-10-18.md` for this batch's evidence.

`check-runtime-views.mjs` compares all 58 writable View properties across native
handlers, getters, shared Node/Bun accessors and declarations. It also checks 16
additional XML widget factories and that property proxies stay out of serialized
View identities. The desktop tests exercise ordered assignments, flush/recovery,
array snapshots and protection against retargeting a wrapped token. Ace checks
XML literals only for the typed runtime inflater, including Promise signatures.
`RuntimeXmlViewDeviceTest` exercises native controls, actual layout geometry,
selection/progress callbacks, updated list items, error recovery, rotation and
copied image pixels. Packaged tests mix Card/Spinner/SeekBar/property assignments
with Compose. See `docs/dev/runtime-xml-views-2026-10-18.md` for accepted results.

The gate also checks all 12 WebView/Drawer/Toolbar/ViewPager commands against
native handlers and both declaration sets. Desktop tests verify commands and
assignments share a queue, including CJS/ESM Workers and Bun's granted transport.
`RuntimeViewComponentsDeviceTest` exercises real HTML/URL loading, decoded page
JavaScript results, navigation history, Drawer/Toolbar events, offscreen page
lookup, Activity recreation and WebView disposal in all six runtime modes.
The packaged fixture exercises the same four components before its Compose
rotation flow. See `docs/dev/runtime-view-components-2026-10-18.md` for results.


`check-webview-callbacks.mjs` compares 40 callback names and 10 public methods
across native handlers, shared clients and Node/Bun declarations. It executes
both facades against controlled transport, including callback replacement,
asynchronous evaluation, early file results and download bytes. Desktop Workers
exercise termination and conditional client cleanup. `RuntimeWebViewDeviceTest`
uses actual WebViews, DocumentsUI, a standalone document provider, HTTP/TLS and
DownloadManager; it does not substitute native callback results. Evidence and
limits are recorded in `docs/dev/runtime-webview-callbacks-2026-10-18.md`.

The file extension gate also checks all four chooser modes, readDocument and opaque
download identifiers. `test-web-download-stream.mjs` executes the real page helper
with binary data, backpressure, cancellation and failure cases. Native
`RuntimeWebFilesDeviceTest` exercises real system camera capture, SAF creation/tree
grants, blob/data link notifications, byte integrity, cancellation and navigation.
The packaged fixture saves and hashes an actual camera upload, creates a tree child,
and verifies the embedded page helper with a binary blob. See
`docs/dev/runtime-webview-files-2026-10-18.md` for accepted results and limits.

WebView streaming media uses the same `check-webview-callbacks.mjs` shared-facade check, strict Node/Bun declaration smoke files and Ace semantic queries. On supported devices, `RuntimeWebStreamsDeviceTest` covers awaited results, rejection/timeouts/navigation, real audio/video chunks, pause/resume, encoded output, and nested Worker microphone release. `RuntimeWebStreamsPermissionDeviceTest` runs separately with media permissions revoked. `RuntimeWebStreamsPackagedDeviceTest` builds and executes actual Node, nested Worker and Bun APKs. Evidence: `docs/dev/runtime-webview-streams-2026-10-18.md`.

Display/background coverage: `test-web-display-stream.mjs` executes the actual page adapter contract; `check-webview-callbacks.mjs` checks the 19 public View media methods. Device suites are `RuntimeWebDisplayDeviceTest`, `RuntimeWebDisplayLifecycleDeviceTest`, `RuntimeWebNativePermissionDeviceTest` (fresh Android grants/denials) and `RuntimeWebDisplayPackagedDeviceTest`. They validate real changing screen frames, background media in a distinct foreground UID, native file output, clone/cancellation lifetimes and genuine APK Builder output. Evidence: `docs/dev/runtime-webview-display-2026-10-18.md`.

The display/background audio fixtures play a bounded 700 ms, 660 Hz pulse only after the recording app is covered by the test activity. The pulse uses approximately 1/15 of the device's media-volume scale, restores the previous volume on completion/failure, and is absent from display/Compose checks. The microphone decoding assertions remain enabled. `-e proofSuffix quiet` stores targeted acoustic retests separately from the full runtime matrix; `-e webMode node` selects one runtime for host tests. Older playback latency/policy fixtures retain their separately calibrated tone behavior.

Bun automation coverage: `check-bun-automation.mjs` compares 32 public native routes with the Node provider and checks reproducible public adapters/declarations using `tools/host-client/sync-automation.mjs` in Bun Runtime. Bun's 35 client tests include grants, selector/Intent normalization, bounded concurrency and cancellation. `BunAutomationDeviceTest` compares actual Node/Bun host operations; `BunAutomationPackagedDeviceTest` builds and runs genuine APKs. `BunAutomationUnavailableDeviceTest` requires `-e expectedServiceState unavailable` and verifies an unavailable service explicitly; it is not a successful action gate. These tests do not play audio. Device scope and limitations are recorded in `docs/dev/bun-automation-2026-10-19.md`.


Bun file/storage coverage: `check-bun-storage.mjs` compares all 15 usable Node file names, the six native storage routes and reproducible declarations. `BunStorageDeviceTest` compares actual Node/Bun encoding and file semantics, cross-execution storage persistence, cross-runtime sharing, directory isolation, grants and payload rejection. `BunStoragePackagedDeviceTest` produces a genuine APK and verifies file/storage persistence after a force-stop and relaunch. Ace checks actual default-setting file/store completion popups. These tests do not play audio.


Database coverage: `check-bun-database.mjs` checks the common main/Worker/Bun factory, its declarations and seven native methods. Node has facade and real Worker lifecycle desktop tests; Bun includes database SDK tests in CI. `BunDatabaseDeviceTest` checks real SQL types, counts, rollback, scope, grants, cancelled opens and Worker cleanup. `BunDatabasePackagedDeviceTest` produces actual Bun and Node Worker APKs and verifies persistence after force-stop/relaunch. These tests do not play audio.


Sensors coverage: `check-bun-sensors.mjs` checks five native routes and the shared main/Worker/Bun factory and declarations. Node facade and real Worker tests cover cancellation, timeouts, late registration and termination. Bun SDK tests cover negotiated grants and lifecycle. `BunSensorsDeviceTest` and `BunSensorsPackagedDeviceTest` check real sensor data, cleanup and packaged runtime identity without audio.


Shell process coverage: `check-shell-contract.mjs` compares all 25 script-visible
ShellProcess properties/methods and the three event names with the native object,
checks constructor/spawn/default option keys and verifies canonical/Ace declaration
copies. `check.py` includes it and follows process and shell-result return chains.
The declarations repository's `shell-process-smoke.ts` checks event payloads,
nullable results, constructor compatibility and invalid arguments with TypeScript
5.1 and 6. Ace checks default and optional declaration groups, constructor options,
result fields and actual popup signatures. `tools/shell-stream/device_shell_stream_test.py`
executes owned commands with no audio; it reports Root/Shizuku modes only when they
are available and checks explicit Shizuku denial otherwise. Preserve device APKs
and settings, use an isolated fixture path, and clean only owned files.


Host Shell coverage: `check-runtime-shell.mjs` verifies ten native routes and the shared Node/Worker/Bun factory and declarations. Node and Bun SDK tests cover streams, backpressure, binary chunk limits and cancellation. `NodeShellBridgeInstrumentationTest` checks raw routes, privilege requirements, quota cleanup and PTY EOF. `RuntimeShellDeviceTest` checks ordinary/Shizuku main and Worker execution; `RuntimeShellPackagedDeviceTest` produces and verifies real Bun/Worker APKs. All commands operate on owned processes and use no acoustic signal.

Run `node tools/api-coverage/check-runtime-events.mjs` for event/notification host routes and the shared Node/Worker/Bun factories and types. Behavior is covered by the client/Worker suites and RuntimeEventsNativeTest/RuntimeEventsDeviceTest/RuntimeEventsPackagedDeviceTest.

## Runtime device controls and dialogs

Run `node tools/api-coverage/check-runtime-device-dialogs.mjs` to compare the native device/dialog routes and shared Node/Bun factories and types. Behavior is covered by Node facade/real Worker tests, Bun device-dialogs.test.mjs, declaration smoke programs, Ace default popup checks and RuntimeDeviceDialogsNativeTest/DeviceTest/PackagedDeviceTest. The fixtures keep media volume unchanged and play no audio. Full host API counts describe documentation/type/completion coverage, not complete Node/Bun implementation parity.

## Runtime Android objects and calendar tasks

Run `node tools/api-coverage/check-runtime-android-tasks.mjs` to compare the native object/task routes with the shared Node/Bun factories and declarations. Node real Worker tests, Bun client tests, type smoke fixtures and Ace default popup tests cover the public surface. CalendarTaskTimesTest checks short/long days and gap/overlap rules. RuntimeAndroidTasksNativeTest/DeviceTest/PackagedDeviceTest verify real Android objects, persistent task CRUD, filtered broadcasts, actual child execution and packaged relaunch. Fixtures use owned task sources and verify task removal without playing test audio.


## Java/Android object interop

Run `node tools/api-coverage/check-runtime-java-interop.mjs` for the 19 native Java routes, the shared Node/Bun facade and synchronized types. Node uses `test-java-interop.js` and `test-worker-java-interop.js`; Bun uses `java-interop.test.mjs`. These verify transport serialization, exact long values, reserved ESM exports, cancellation and Worker ownership. The full Android contract is exercised by `RuntimeJavaInteropNativeTest`, `RuntimeJavaInteropDeviceTest`, `NodeJavaInteropBridgeInstrumentationTest` and `RuntimeJavaInteropPackagedDeviceTest`. Packaged checks use a genuine Runtime Kit and run the generated Bun and Node/Worker applications before and after relaunch. No audio stimulus is used. The coverage counts describe explicit routes and declared facade members, not all possible Java classes, all overloads or Rhino parity.


## Java callbacks and class loading

Run `node tools/api-coverage/check-runtime-java-callbacks.mjs` for the shared callback/load routes and class-generation wiring. Node uses test-java-callbacks.js/test-worker-java-callbacks.js and Bun uses java-callbacks.test.mjs. RuntimeJavaSubclassGeneratorTest validates generated JVM bytecode, constructor callbacks, protected methods, superclass calls, inherited default methods and valid class names. Build owned JAR/DEX inputs with `python tools/api-coverage/build-java-callback-fixtures.py --output <ignored-directory> --java-home <JDK-17-or-newer>`; --sdk can select the installed Android SDK. Physical suites are RuntimeJavaCallbacksNativeTest, RuntimeJavaCallbacksDeviceTest and RuntimeJavaCallbacksPackagedDeviceTest, alongside the prior Java interop regression. Tests use sound-disabled Android clicks and contain no audio stimulus. Code-loading and return-valued callbacks are verified inside genuine generated applications as well as the host.


Run `node tools/api-coverage/check-runtime-tts.mjs` to compare all 31 registered Rhino TTS method names with the shared Node/Bun/Worker facade, declarations and documentation. Node uses test-tts.js and test-worker-tts.js, while Bun uses tts.test.mjs. RuntimeTtsDeviceTest verifies real engines, WAV output, task events, grants and Worker ownership; RuntimeTtsPackagedDeviceTest uses genuine Runtime Kit applications. Playback uses volume zero, and file synthesis produces no speaker output. The TtsUtteranceTest regression keeps Rhino isDone a boolean. See docs/dev/runtime-tts-2026-10-20.md for accepted evidence and remaining checks.
