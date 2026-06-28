# P13-43 Stdlib Conformance

P13-43 records the v1.1 representative Node stdlib conformance gate as ready
while full desktop stdlib parity remains partial.

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
- Builtin precedence is preserved. `process.getBuiltinModule` is now a
  controlled partial API: allowed builtin names return the same AutoJs6 limited
  wrappers as `require()`, unknown builtin probes return `undefined`, and
  disabled builtins still fail through the builtin policy gate.
- The `buffer` wrapper is a frozen ordinary object, so legacy npm packages can
  use `require("buffer").hasOwnProperty(...)` and `Buffer.hasOwnProperty(...)`
  probes without loading `node_modules/buffer` shadows.
- `globalThis.TextDecoder` and `require("util").TextDecoder` include a no-ICU
  Unicode fallback for `fatal: true` with `utf-8`, `utf-16le`, and `utf-16be`;
  this is npm compatibility support, not full ICU coverage.
- Normal one-shot Embedded Node scripts default to a `30000` ms timeout rather
  than the `3000` ms probe timeout, reducing false timeouts while large pure-JS
  npm module graphs initialize.
- Android differences are documented as stable for `os.setPriority`, real
  network interfaces, raw TTY mode, `zlib/promises`, and `node:test/reporters`.

## Evidence

- `NodeStdlibPhase13DecisionInstrumentationTest` exercises the profile and a
  representative device subset across `crypto`, streams, zlib, Buffer, URL,
  OS, readline, TTY, `perf_hooks`, and `async_hooks`.
- Existing `NodeCoreBuiltinCompatibilityInstrumentationTest` continues to
  verify builtin aliases, shadowing, denials, AsyncLocalStorage, perf hooks,
  diagnostics channel, `node:test`, ESM builtin imports, and Web API promise
  behavior.
- `NodeRealNpmCompatInstrumentationTest` now keeps the Phase 13 real npm
  corpus at 60 fixtures, including five fixed stdlib-dependent package
  snapshots for `crypto` + `stream/consumers`, `perf_hooks` + `async_hooks`,
  `URL` + `Buffer`, `util` + `events`, and `path` + `querystring`, plus five
  fixed ESM/network/fs/package-manager snapshots.
- Existing `NodeModernCoreDifferentialInstrumentationTest` keeps modern-core
  partial evidence for AsyncLocalStorage, `perf_hooks`, stream promises,
  WebCrypto, diagnostics, timers promises, and `node:test`.
- `NODE_BUILTIN_PARITY_MATRIX.md` now records the P13-43 representative
  stdlib modules as partial rather than suggesting promotion to full desktop
  behavior.

## Closure

The v1.1 gate closes because `stdlibProfile`, representative module device
coverage, Android difference notes, stable denials, builtin matrix rows, real
npm corpus deltas, modern-core differential evidence, TypeScript declarations,
docs, surface manifest, and Gradle gate are synchronized without enabling raw
desktop parity.

## Future Work

- Broader module-by-module conformance needs hostile inputs, more edge cases,
  Android terminal/OS edge cases, and packaged lifecycle evidence.
- Additional stdlib-dependent packages and broader connected differential
  dashboards remain future work beyond the current stdlib +5 and
  ESM/network/fs/package-manager +5 fixed-fixture deltas.
- Full desktop parity still needs future policy for raw native handles, broad
  terminal behavior, unrestricted network/OS surfaces, raw
  `process.getBuiltinModule` bypass behavior, and `zlib/promises`.

The docs, release matrix, surface manifest, and Gradle gate are evidence checks
only. They do not add a second blocker for the same broader stdlib conformance
and packaged lifecycle work.

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
