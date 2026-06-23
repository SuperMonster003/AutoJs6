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

- `tasks.compat`: P12-15 locks `compat.tasks` v1 to disposable one-shot
  migration over `work_manager`: `addDisposableTask({ path, date|delayMs, ... })`
  maps to `scheduleOnce`, `removeTask(id)` maps to `cancel`, and
  `queryTasks(options?)` maps to `list`. The design keeps JSON-safe ids/snapshots
  only, leaves runtime facade work to P12-16, defers daily/weekly user task
  database semantics to P12-17, defers intent/broadcast triggers to P12-18, and
  keeps raw `TimedTask` / `IntentTask` objects blocked by design.

## Current Rejected Surfaces

- None in the current Phase 12 surface manifest.

## Rules

- Every P12 task must map to exactly one surface group.
- Every `promoted` surface must list at least one evidence path or Gradle gate.
- Runtime surface promotion must update type declarations, status manifest,
  compatibility matrix, and task checklist evidence.
- Rejected surfaces must include stable denial behavior and migration guidance.
