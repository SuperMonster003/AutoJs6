# P13-38 Controlled WASI Profile Decision

P13-38 records controlled WASI profile as partial. The current Phase 13
decision is to expose diagnostics through `autojs6:profile.wasiProfile` while
keeping raw `require("wasi")`, `require("node:wasi")`,
`require.resolve("wasi")`, and `require.resolve("node:wasi")` denied by
default.

## Decision

- Pure WebAssembly: available through the existing V8 `WebAssembly` global.
- Raw `node:wasi`: denied.
- Controlled facade: deferred.
- Target profile: future `desktop_compat_opt_in` only.
- Preopens: must map through scoped or explicitly authorized roots.
- Working-directory preopen: deferred.
- Authorized roots: required before enablement.
- Args: explicit only.
- Env: empty by default.
- fd table: virtualized before enablement.
- stdio: bounded before enablement.
- clock/random: limited before enablement.
- Network and process exit: denied.
- Symlink escape: must deny.
- Worker integration: deferred.
- Packaged behavior: not promoted.
- Raw host paths and raw fd numbers: denied.

This report does not add a callable `autojs6:wasi` facade or a script-visible
raw WASI module. It records the contract a future controlled WASI
implementation must satisfy before any preopen or WASI import surface can be
promoted.

## Evidence

- `NodeWasiPhase13DecisionInstrumentationTest` verifies the frozen
  `wasiProfile`, pure WebAssembly signal, raw `wasi` / `node:wasi` denial,
  resolve denial, and local shadow-package protection.
- `NodeWasmWasiInstrumentationTest` continues to verify pure WebAssembly
  compile/instantiate, scoped `.wasm` file loading, and raw WASI denial.
- `docs/nodejs/reports/phase9/WASM_WASI.md` remains the current design source
  for the scoped-fs preopen direction and raw WASI denial.
- `verifyNodePhase13ControlledWasiProfileDecision` records the host gate and
  writes `phase13-controlled-wasi-profile-decision.json` and `.md`.

## Remaining Blockers

- Design and implement the controlled `autojs6:wasi` facade or a limited
  `node:wasi` profile API.
- Implement scoped/authorized preopen mapping with lexical and realpath/symlink
  escape denial.
- Define args, env, virtual fd table, stdio, clock, random, process-exit, and
  network-denial policies with instrumentation.
- Add resource budgets for module size, memory pages, stdout/stderr, timeout,
  and concurrent instances.
- Add packaged `.wasm`/WASI asset manifest, tamper checks, and packaged
  behavior evidence.
- Add hostile WASI corpus and worker/WASM integration evidence before
  promotion.

## Rollback

Remove `wasiProfile`, the focused instrumentation test, the P13-38 verifier,
and the Phase 13 docs/report references. Runtime behavior falls back to the
existing Phase 9/10 WASM/WASI gates: pure WebAssembly remains available, raw
WASI stays disabled with stable denial, and `node_modules/wasi` shadowing
remains blocked.
