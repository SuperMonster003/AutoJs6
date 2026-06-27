# Phase 9 Performance Policy Gate

S9-15 through S9-20 move the Phase 8 performance work from standalone reports
into a Phase 9 policy gate. The gate verifies the current production-safe
performance surface without promoting unavailable runtime modes.

Run:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase9PerformancePolicy
```

Generated outputs:

- `build/reports/nodejs/phase9-performance-policy.json`
- `build/reports/nodejs/phase9-performance-policy.md`

## Current Decision

The gate is expected to report `bootstrap_only` until startup snapshots,
reusable prewarmed Node/V8 runtime pooling, numeric SLO thresholds, and packaged
launch metrics are implemented. `bootstrap_only` is intentional: it keeps the
Phase 9 plan moving while preventing the current Node 24.5 runtime from being
reported as fully performance-productionized.

## Covered Evidence

The gate checks source, tests, and documentation for:

- S9-15/S9-16 module compile cache policy:
  - app-private cache directory below `codeCacheDir`;
  - runtime, ABI, SDK, loader, feature-flag, project, and content scoped keys;
  - quota and pruning controls;
  - user environment filtering for internal cache controls;
  - hidden user-facing `node:module` compile-cache controls;
  - Node Doctor and focused instrumentation coverage.
- S9-17 startup snapshot boundary:
  - startup snapshots remain disabled;
  - no snapshot blob is generated, packaged, selected by script, or loaded;
  - normal bootstrap remains the only active path.
- S9-18 process-only prewarm:
  - explicit prewarm service action and Kotlin API;
  - single-use process-only policy;
  - no Node/V8 environment, capability, provider, or user module is loaded
    during prewarm;
  - focused instrumentation coverage.
- S9-19 memory pressure:
  - unused prewarm state expires on Android low-memory callbacks.
- S9-20 performance matrix:
  - compile-cache and process-prewarm axes remain measurable;
  - unavailable snapshot modes are reported explicitly.

## Remaining Work

The following remain intentionally outside the active production surface:

- startup snapshot generation and validated loading;
- reusable clean Node/V8 runtime pooling;
- V8 low-memory notification and per-runtime RSS hard quota;
- packaged launch-to-marker performance metric;
- enforced Phase 9 numeric SLO thresholds.

These items should move from `bootstrap_only` to `ready` only after new runtime
implementation, focused device evidence, packaged APK evidence, and security
regression coverage exist.
