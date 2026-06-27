# P13-36 VM Module Decision

P13-36 records `vm` module promotion as partial. The current Phase 13 decision
is to expose Node's native `vm` builtin for desktop npm compatibility while
keeping the risk boundary explicit in `autojs6:profile.vmProfile`.

## Decision

- Runtime module: native available.
- `require("vm")` and `require("node:vm")`: native available.
- `require.resolve("vm")` and `require.resolve("node:vm")`: resolve to `vm`.
- Local `node_modules/vm`: cannot shadow the builtin.
- `vm.Script`, context APIs, `compileFunction`, `SyntheticModule`,
  `SourceTextModule`, dynamic import hooks, and native timeout options: native
  available.
- Target profiles: `desktop_compat_opt_in` and `debug_unsafe_lab` remain the
  diagnostic risk labels.
- Bridge modules and private bindings: not injected by AutoJs6.
- Raw Android host objects: not injected by AutoJs6.
- Node `vm` contexts: not a security boundary.
- Memory and compiled-script limits: follow-up evidence required.
- Packaged behavior: native builtin available, focused npm smoke evidence still
  required.

This report adds the callable native VM API because jsdom and similar desktop
npm packages require it. It does not claim VM contexts are an isolation boundary.

## Evidence

- `NodeVmPhase13DecisionInstrumentationTest` verifies the frozen `vmProfile`,
  native `vm` / `node:vm` require and resolve behavior, `vm.Script`, context
  execution, and local shadow-package protection.
- `NodeVmDesignGateInstrumentationTest` verifies registry/profile state,
  native builtin availability, and shadowing protection.
- `NodeCoreBuiltinCompatibilityInstrumentationTest` verifies core builtin
  aliasing for `vm` and `node:vm`.
- `EmbeddedNodePackagedApkSmokeTest` keeps `module.isBuiltin("node:vm")`
  visible in packaged smoke coverage.
- `docs/nodejs/design/VM.md` records the current non-sandbox boundary and
  follow-up evidence.
- `verifyNodePhase13VmModuleDecision` records the host gate and writes
  `phase13-vm-module-decision.json` and `.md`.

## Remaining Blockers

- Prove memory and compiled-script budgets for repeated VM workloads.
- Add packaged APK smoke evidence for packages that exercise `vm`.
- Expand bridge/process/global leakage probes so the native boundary remains
  documented and testable.
- Add timeout and lifecycle stress evidence for long-running VM code.

The docs, release matrix, desktop matrix, surface manifest, and Gradle gate are
evidence checks only. They do not add a second blocker for the same memory,
compiled-script, lifecycle, leakage, and packaged VM evidence.

## Rollback

Restore `vm` to `disabledBuiltinModuleNames`, remove it from
`limitedBuiltinModuleNames`, restore the C++ denylist branch, and revert the
P13-36 tests/docs to the disabled design gate. This would break npm packages
that depend on Node's native VM APIs.
