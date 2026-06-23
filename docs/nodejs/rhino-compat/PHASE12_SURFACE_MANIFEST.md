# Phase 12 Surface Manifest

`phase12-surface-manifest.json` tracks the promotion state of each Phase 12
runtime or decision surface. It is separate from the Rhino API status manifest:
the status manifest records API-level migration state, while this file records
which Phase 12 workstream owns the next implementation or rejection.

## Status Vocabulary

- `planned`: the surface is in the Phase 12 checklist but has not started.
- `design_locked`: the policy boundary is documented, but runtime promotion or
  permanent rejection has not been selected.
- `provider_poc`: a fake or live provider proof of concept exists, but the
  surface is not fully promoted.
- `promoted`: runtime/docs/types/tests or decision evidence have landed and the
  matching Phase 12 gate passes.
- `rejected`: the surface is intentionally unavailable, with stable denial,
  documentation, and migration guidance.

## Current Promoted Surfaces

- `phase12.baseline`: Phase 12 checklist and baseline verifier.
- `phase12.surface_manifest`: this manifest and its verifier.
- `events.keys`: pure Rhino key constants through `autojs6:compat`.
- `tasks.compat`: P12-15 locks `compat.tasks` v1 to disposable one-shot
    migration over `work_manager`; P12-16 promotes
    `require("autojs6:compat").tasks` with `addDisposableTask({ path,
    date|time|delayMs, ... })` over `scheduleOnce`, `removeTask(id)` over
    `cancel`, and `queryTasks(options?)` over `list`. The runtime returns
    JSON-safe ids/snapshots only, keeps `require("tasks")` and global `tasks`
    absent, and P12-17 adds stable `addDailyTask()` / `addWeeklyTask()`
    unsupported stubs plus `queryTimedTasks()`, `getTimedTask(id)`, and
    `removeTimedTask(id)` aliases for Node-owned one-shot snapshots. The
    daily/weekly user task database provider remains rejected. P12-18 adds
    stable `addIntentTask()` / `queryIntentTasks()` / `getIntentTask(id)` /
    `removeIntentTask(id)` unsupported stubs without receiver registration,
    manifest mutation, custom broadcast authority, or raw `Intent` exposure.
    P12-19 adds checked examples in `docs/nodejs/examples/rhino-compat/09-tasks.*`
    plus `RHINO_TASKS_COMPAT.md`, bucketing `supported_async`, `unsupported`,
    and `blocked_by_design` cases. Raw `TimedTask` / `IntentTask` objects remain
    blocked by design.

## Current Provider POC Surfaces

- `input_observer`: P12-04 provider v1 contract, P12-05 fake-source
  `require("input_observer")` POC, and P12-06 accessibility live-source gate.
  P12-07 adds cleanup/security diagnostics and packaged/background rejection
  evidence.
- `ui.compat`: P12-08 locks the Rhino UI to Node JSON layout descriptor subset
  with schema, fixtures, and a host verifier. P12-09 adds constrained
  `autojs6:compat.ui` over the existing `require("ui")` provider. P12-10 adds
  fake/device instrumentation evidence, a packaged smoke marker, and
  `UI_SESSION` cleanup reporting; live packaged UI remains unpromoted.
- `floaty.overlay`: P12-11 selects the Provider path for a controlled JSON-only
  overlay provider. P12-12A locks the provider v1 contract in
  `OVERLAY_PROVIDER_V1.md`; P12-13 adds the `require("ui.overlay")` provider
  POC over fake transport as a provider POC, with runtime module, types, capability preflight,
  fake-provider instrumentation, and `OVERLAY_SESSION` registry evidence.
  P12-14 adds the live gate for Android permission/settings dry-run,
  `SYSTEM_ALERT_WINDOW` packaged metadata, foreground disclosure requirements,
  closeAll cleanup summaries, and fail-closed live overlay creation. Exact Rhino
  `floaty.window()` / `floaty.rawWindow()` raw-wrapper parity remains
  `blocked_by_design` and must not be exposed as raw Android/Rhino objects.

## Current Design-Locked Surfaces

- `threads.replacement`: P12-20 keeps Rhino `threads` as a
  `blocked_by_design` compatibility surface. `worker_threads` remains
  default-off and native-availability gated, `worker_computation` remains
  reserved, `second_execution_slot` remains open, and no `require("threads")`,
  `compat.threads`, global `threads`, global `sync`, or same-runtime Rhino
  thread/shared-object parity is claimed. P12-21 publishes
  `RHINO_THREADS_MIGRATION_COOKBOOK.md` plus dual-engine `threads-start`,
  `threads-disposable`, and `threads-sync-queue` cases. The cookbook uses
  `async` / `await`, `AbortController`, explicit `engines` slots,
  `work_manager`, bounded async queues, Promise queues, and MessageChannel-style
  handoffs. P12-22 defers the compute/process-worker replacement API for Phase
  12: no runtime API is promoted, future APIs must be separately named, and
  worker/process permissions, scoped script path, message size, cleanup, nested
  worker denial, bridge module isolation, resource stress, packaged behavior,
  and `second_execution_slot` gates remain required.
- `rhino.json_only_shim`: P12-23 establishes the default-disabled POC gate for
  the explicit `rhino.run({ source, timeoutMs, args })` / scoped `path` API.
  P12-24 reserves `require("rhino")` as a disabled facade:
  `rhino.run()` rejects with `ERR_AUTOJS6_RHINO_SHIM_DISABLED`,
  `rhino.install()` throws `ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED`,
  `require("node:rhino")` remains unavailable, and local `node_modules/rhino`
  cannot shadow the controlled module. Release/default Safe Node Profile still
  exposes no executable `rhino.run()`, no supported `rhino.install()`,
  no `compat.rhino`, and no Rhino globals. Future POC execution requires an
  explicit experimental flag or test metadata, and JSON validator/no-globals
  requirements remain locked before P12-25 and P12-26.

## Current Rejected Surfaces

- None in the current Phase 12 surface manifest.

## Rules

- Every P12 task must map to exactly one surface group.
- Every `promoted` surface must list at least one evidence path or Gradle gate.
- Runtime surface promotion must update type declarations, status manifest,
  compatibility matrix, and task checklist evidence.
- Rejected surfaces must include stable denial behavior and migration guidance.
