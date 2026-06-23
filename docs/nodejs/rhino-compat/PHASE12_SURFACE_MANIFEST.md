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

## Current Design-Locked Surfaces

- `ui.compat`: P12-08 locks the Rhino UI to Node JSON layout descriptor subset
  with schema, fixtures, and a host verifier. The runtime `compat.ui` facade
  remains planned for P12-09/P12-10.

## Rules

- Every P12 task must map to exactly one surface group.
- Every `promoted` surface must list at least one evidence path or Gradle gate.
- Runtime surface promotion must update type declarations, status manifest,
  compatibility matrix, and task checklist evidence.
- Rejected surfaces must include stable denial behavior and migration guidance.
