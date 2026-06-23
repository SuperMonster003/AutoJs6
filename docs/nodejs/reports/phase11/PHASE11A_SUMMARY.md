# Phase 11A Summary: Rhino Augment Compatibility

Status: S11-31 published summary.

Phase 11A establishes a tracked compatibility layer for Rhino `augment` APIs in the Embedded Node engine. The result is not a claim of full Rhino runtime parity. It is a complete planning baseline: every known Rhino augment module has an inventory entry, a migration status, a matrix row, and either a supported Node path, a partial/deferred path, or a blocked-by-design decision.

## Current Totals

- Rhino augment modules: 73
- Rhino API entries: 879
- Node bridge modules with evidence: 23
- Node type modules with evidence: 46
- Rhino modules with bridge evidence: 24
- Rhino modules with type evidence: 37
- Rhino modules without Node surface evidence: 35

Status record totals from the generated compatibility matrix:

| Status | Count |
| --- | ---: |
| `supported` | 134 |
| `supported_async` | 52 |
| `partial` | 332 |
| `renamed` | 1 |
| `unsupported` | 388 |
| `blocked_by_design` | 45 |

The canonical machine-readable inputs remain:

- `build/reports/nodejs/rhino-augment-api-inventory.json`
- `build/reports/nodejs/rhino-node-compat-gap.json`
- `docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md`
- `docs/nodejs/rhino-compat/status-manifest.json`

## Completed Scope

Phase 11A now has host gates for the compatibility loader, inventory, gap/matrix, low-risk facades, automation-core facades, design-only blocked surfaces, examples, dual-engine corpus, packaged APK smoke, and resource cleanup/security.

Supported or partially usable migration surfaces:

- Basic globals: `toast`, `toastLog`, `sleep`, `setClip`, `getClip`
- Scoped files facade: path, join, read, write, exists, listDir, remove, copy, move
- Pure utilities: base64, colors, formatter, converter, mime, nanoid
- Text utilities: OpenCC, pinyin, pinyin4j strategy
- Storage/database: `storages`, `storage`, `database`, `sqlite`
- Console/timers/error helpers
- Selector/accessibility DSL and core actions
- App/device/shell bridge facades
- Images and screenshot wrappers with explicit recycle/stop behavior
- OCR text facade over controlled image/OCR bridge
- Migration examples for common Rhino script patterns
- Dual-engine compatibility corpus for selected supported_async and blocked_by_design cases
- Packaged APK smoke for loader, permissions, cleanup, and core compat paths
- Resource cleanup/security gate for image, selector, ui, db, websocket/fetch, events, and floaty boundaries

## Compatibility Rules

The migration model is intentionally explicit:

- Node does not receive default Rhino globals unless a script opts in through `autojs6:compat`.
- Rhino blocking APIs migrate to Promise-returning Node facades.
- Java/Rhino/Android objects do not cross into V8 as raw objects.
- Native resources cross as JSON snapshots or opaque handles.
- Lifecycle-heavy APIs must define stop, timeout, close, destroy, and packaged behavior before promotion.
- Denial paths must be stable and must not dispatch provider work when capability manifests are missing.

## Blocked Decisions

These decisions should stay blocked unless a later task adds a provider, policy, and cleanup gate:

| Area | Current decision | Reason |
| --- | --- | --- |
| `events` / input observers | `blocked_by_design` / design-gated | Raw Rhino events bind native listeners and threading semantics to Rhino objects. |
| `ui.run` / `ui.post` / raw View lookup | `blocked_by_design` | Node JavaScript must not run on Android main looper or receive raw `View` / `NativeView`. |
| `floaty.window` / overlays | `blocked_by_design` | Overlay permission, foreground disclosure, packaged manifest, and close paths are not yet defined as a Node provider. |
| `tasks` raw TimedTask/IntentTask | design-gated | WorkManager does not model Rhino user task database and broadcast trigger semantics. |
| `threads` | `blocked_by_design` | Rhino Java threads and shared mutable runtime objects conflict with Safe Node Profile. |
| Rhino JSON-only shim | design-gated | Any Rhino-in-Node bridge must remain explicit and JSON-only; no Rhino globals may be installed into V8. |
| `barcode` / `qrcode` | provider gap | A JSON-safe barcode provider and result contract still need to be implemented. |

## Migration Paths

For script authors, the recommended path is:

| Rhino pattern | Node path |
| --- | --- |
| Small global helpers | `require("autojs6:compat")`, optionally `compat.install({ globals: true })` |
| File/storage scripts | `compat.files`, `compat.storages`, `require("storage")`, `require("database")` |
| Selector scripts | `compat.text(...)`, `compat.desc(...)`, `require("accessibility")` |
| Image scripts | `compat.images.*`, explicit `recycle()`, explicit screen-capture stop |
| OCR scripts | `compat.ocr(...)` over path/handle/capture inputs |
| UI scripts | `require("ui")` JSON descriptors and opaque `UiHandle` updates/events |
| Long-running work | `work_manager`, explicit engine/process slot, or async workflow |
| Threads | async/await, `AbortController`, process slot, or future worker policy |
| Floaty/overlay | no direct migration yet; wait for overlay provider or keep Rhino engine |

## Next Phase

The next phase can be split directly from this list:

| Task | Priority | Scope | Exit gate |
| --- | --- | --- | --- |
| P12-01 | P0 | Promote `events.keys` constants or an `input_observer` provider POC. | Bounded event queue, manifest capabilities, and zero active observers after destroy. |
| P12-02 | P0 | Split UI compat runtime facade from raw Rhino UI non-goals. | `UiHandle`-only examples and gates; no `ui.run`, `ui.post`, or raw View leakage. |
| P12-03 | P0 | Decide floaty/overlay provider or permanent non-goal. | Overlay permission, foreground disclosure, packaged manifest, and closeAll/destroy cleanup. |
| P12-04 | P1 | Decompose `tasks` into WorkManager, user-task DB, and broadcast triggers. | No raw TimedTask/IntentTask objects; explicit ownership per subtrack. |
| P12-05 | P1 | Define thread replacement policy after worker/process-slot maturity. | Examples for common thread patterns without default `worker_threads`. |
| P12-06 | P1 | Prototype explicit Rhino JSON-only shim. | `rhino.run({ source, timeoutMs, args })` POC with JSON-only IO and timeout/destroy cleanup. |
| P12-07 | P1 | Implement barcode/qrcode JSON-safe provider. | Result schema, fixtures, types, image handle cleanup, and packaged smoke. |
| P12-08 | P2 | Triage unsupported utility-heavy modules. | Per-module status decision or permanent non-goal. |
| P12-09 | P2 | Promote useful dual-engine cases to device instrumentation. | Device result fields populated for selected `supported_async` cases. |

## Generated Report

Run:

```powershell
.\gradlew.bat --console=plain :app:generateNodeRhinoCompatPhase11Summary
```

This writes:

- `build/reports/nodejs/phase11a-summary.json`
- `build/reports/nodejs/phase11a-summary.md`

The JSON report uses schema `autojs6-node-rhino-compat-phase11a-summary-v1` and verifies that S11-00 through S11-31 are complete, required host reports are present and passing, and this summary contains the anchors needed for Next Phase planning.
