# P13-36 VM Module Decision

P13-36 records `vm` module promotion as partial native compatibility with ready
Phase 13 closure evidence. The current Phase 13 decision is to expose Node's
native `vm` builtin for desktop npm compatibility while keeping the risk
boundary explicit in `autojs6:profile.vmProfile`.

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
- Memory and compiled-script limits: focused repeated-script evidence is ready.
- Packaged behavior: full packaged smoke exercises a fixture package that uses
  `vm.Script`, contexts, `compileFunction`, timeout behavior, and builtin
  shadow protection.

This report adds the callable native VM API because jsdom and similar desktop
npm packages require it. It does not claim VM contexts are an isolation boundary.

P13-36 focused closure evidence is ready.

## Evidence

- `NodeVmPhase13DecisionInstrumentationTest` verifies the frozen `vmProfile`,
  native `vm` / `node:vm` require and resolve behavior, `vm.Script`, context
  execution, and local shadow-package protection.
- `NodeVmPhase13DecisionInstrumentationTest#vmMemoryCompiledScriptBudgetAndTimeoutStressAreBounded`
  verifies repeated `vm.Script` and context workloads under a 32 MiB heap-delta
  budget, native timeout interruption, and non-injection of AutoJs6 bridge
  globals, `require`, and `process` into new VM contexts.
- `NodeVmDesignGateInstrumentationTest` verifies registry/profile state,
  native builtin availability, and shadowing protection.
- `NodeCoreBuiltinCompatibilityInstrumentationTest` verifies core builtin
  aliasing for `vm` and `node:vm`.
- `EmbeddedNodePackagedApkSmokeTest` keeps `module.isBuiltin("node:vm")`
  visible in packaged smoke coverage and adds `packaged-vm-user`, a packaged
  fixture package that exercises `vm.Script`, contexts, `compileFunction`,
  timeout behavior, and local `node_modules/vm` shadow-package protection.
- `docs/nodejs/design/VM.md` records the current non-sandbox boundary and
  follow-up evidence.
- `verifyNodePhase13VmModuleDecision` records the host gate and writes
  `phase13-vm-module-decision.json` and `.md`.

## Closure

The v1.1 gate closes because `vmProfile` diagnostics, native `vm` /
`node:vm` require and resolve behavior, `vm.Script`, contexts,
`compileFunction`, local shadow-package protection, repeated compiled-script
budget evidence, timeout stress, bridge/process/global non-injection probes,
packaged fixture smoke coverage, docs, desktop matrix, surface manifest,
TypeScript declarations, and Gradle gate are synchronized.

## Future Hardening

- Broaden repeated VM stress durations and package corpus coverage.
- Add stricter memory controls only if a future profile treats VM as more than
  a native compatibility surface.
- Keep documenting that VM contexts are not an AutoJs6 security boundary.

The docs, release matrix, desktop matrix, surface manifest, and Gradle gate are
evidence checks only. They do not add a second blocker for the same VM
compatibility evidence.

## Rollback

Restore `vm` to `disabledBuiltinModuleNames`, remove it from
`limitedBuiltinModuleNames`, restore the C++ denylist branch, and revert the
P13-36 tests/docs to the disabled design gate. This would break npm packages
that depend on Node's native VM APIs.
