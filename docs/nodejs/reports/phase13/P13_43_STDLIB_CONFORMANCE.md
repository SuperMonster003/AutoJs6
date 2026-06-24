# P13-43 Stdlib Conformance

P13-43 records Node stdlib conformance as partial.

The current landing adds `autojs6:profile.stdlibProfile` as diagnostic metadata
for the Safe Node stdlib subset. It does not claim raw desktop Node parity.
Instead it records the representative modules covered by the current Safe
runtime, the Android-specific differences that remain stable, and the gates
that keep broader Desktop behavior future work.

## Covered Subset

- Modules recorded by `stdlibProfile.conformanceModules`: `crypto`, `stream`,
  `stream/promises`, `stream/consumers`, `stream/web`, `zlib`, `buffer`, `url`,
  `os`, `readline`, `readline/promises`, `tty`, `perf_hooks`, and
  `async_hooks`.
- `moduleStatus` records the representative modules as `partial` and keeps
  `zlib/promises` as `disabled`.
- CJS aliases use bare and `node:` prefixes; ESM imports remain partial.
- Builtin precedence is preserved, while `process.getBuiltinModule` remains
  denied.
- Android differences are documented as stable for `os.setPriority`, real
  network interfaces, raw TTY mode, `process.getBuiltinModule`,
  `zlib/promises`, and `node:test/reporters`.

## Evidence

- `NodeStdlibPhase13DecisionInstrumentationTest` exercises the profile and a
  representative device subset across `crypto`, streams, zlib, Buffer, URL,
  OS, readline, TTY, `perf_hooks`, and `async_hooks`.
- Existing `NodeCoreBuiltinCompatibilityInstrumentationTest` continues to
  verify builtin aliases, shadowing, denials, AsyncLocalStorage, perf hooks,
  diagnostics channel, `node:test`, ESM builtin imports, and Web API promise
  behavior.
- Existing `NodeRealNpmCompatInstrumentationTest` keeps the Phase 9 real npm
  corpus baseline at 30 fixtures within the 50-fixture corpus; P13-43 does not
  raise that corpus floor.
- Existing `NodeModernCoreDifferentialInstrumentationTest` keeps modern-core
  partial evidence for AsyncLocalStorage, `perf_hooks`, stream promises,
  WebCrypto, diagnostics, timers promises, and `node:test`.
- `NODE_BUILTIN_PARITY_MATRIX.md` now records the P13-43 representative
  stdlib modules as partial rather than suggesting promotion to full desktop
  behavior.

## Remaining Work

- Broader module-by-module conformance needs hostile inputs, more edge cases,
  and Android-specific unsupported behavior notes.
- The real npm corpus still needs additional stdlib-dependent packages and
  pass-rate deltas before P13-43 can satisfy the original corpus-growth
  acceptance criterion.
- Full desktop parity still needs future policy for raw native handles, broad
  terminal behavior, unrestricted network/OS surfaces, `process.getBuiltinModule`,
  and `zlib/promises`.

## Verification

```powershell
node tools\nodejs\phase13\verify-stdlib-conformance-decision.js --repo-root . --report-dir build\reports\nodejs
.\gradlew.bat --console=plain :app:verifyNodePhase13StdlibConformanceDecision
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeStdlibPhase13DecisionInstrumentationTest"
```

## Rollback

Remove `stdlibProfile`, the P13-43 instrumentation test, the verifier, the
Gradle gate entry, and the related docs/surface evidence. The runtime will
return to the pre-P13-43 state where existing stdlib wrappers still work but no
Phase 13 stdlib conformance decision is recorded.
