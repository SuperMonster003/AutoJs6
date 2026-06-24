# P13-36 VM Module Decision

P13-36 records `vm` module promotion as partial. The current Phase 13 decision
is to expose diagnostics through `autojs6:profile.vmProfile` while keeping
`require("vm")`, `require("node:vm")`, `require.resolve("vm")`, and
`require.resolve("node:vm")` denied by default.

## Decision

- Runtime module: denied.
- `vm.Script`: deferred.
- Context APIs: deferred.
- `compileFunction`, `SyntheticModule`, `SourceTextModule`, and dynamic import
  hooks: denied.
- Target profiles: future `desktop_compat_opt_in` or `debug_unsafe_lab` only.
- Timeout: required before enablement.
- Bridge modules, private bindings, host objects, future-context process
  access, raw global access, and scoped-fs bypasses: denied.
- Memory and compiled-script limits: not defined.
- Packaged behavior: not promoted.

This report does not add a callable VM API. It records the contract a future
implementation must satisfy before any limited `vm.Script` or context surface
can be promoted.

## Evidence

- `NodeVmPhase13DecisionInstrumentationTest` verifies the frozen `vmProfile`,
  default denial, `node:vm` denial, resolve denial, and local shadow-package
  protection.
- `NodeVmDesignGateInstrumentationTest` continues to verify the existing
  design-gated disabled state.
- `docs/nodejs/design/VM.md` remains the source of the future enablement
  acceptance criteria.
- `verifyNodePhase13VmModuleDecision` records the host gate and writes
  `phase13-vm-module-decision.json` and `.md`.

## Remaining Blockers

- Define whether the first opt-in surface is compile-only `vm.Script`,
  current-context execution, or separate context execution.
- Prove deterministic timeout and lifecycle cancellation.
- Prove context cleanup and memory/compiled-script budgets.
- Add escape-denial tests for private bindings, bridge globals, host objects,
  process internals, disabled builtins, and scoped filesystem wrappers.
- Add packaged APK behavior and diagnostics.

## Rollback

Remove `vmProfile`, the focused instrumentation test, the P13-36 verifier, and
the Phase 13 docs/report references. Runtime behavior falls back to the existing
VM design gate: `vm` stays disabled with stable denial and shadowing protection.
