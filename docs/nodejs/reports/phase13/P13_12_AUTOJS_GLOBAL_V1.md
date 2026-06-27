# P13-12 $autojs Global V1

- Status: ready
- Gate: `:app:verifyNodePhase13AutoJsGlobalV1`
- Runtime surface:
  - `globalThis.$autojs`
  - `$autojs.version`
  - `$autojs.profile`
  - `$autojs.androidContext`
  - `$autojs.java`

## Implemented

P13-12 adds a Pro-style `$autojs` global before Node user code runs. The global
is non-enumerable, non-writable, non-configurable, and frozen. Its profile
descriptor is the same object returned by `require("autojs6:profile")`.

The Safe Node Profile exposes `$autojs.androidContext` only as a frozen
diagnostic descriptor. It contains package metadata and a stable reason string,
but it does not expose a raw Android `Context`, `Activity`, `Service`, View, or
Binder object.

`$autojs.java` is a gated facade over the existing Java interop experiment. In
normal builds it reports `enabled=false` and calls reject with
`ERR_AUTOJS6_JAVA_INTEROP_DISABLED`. When the build-time Java interop experiment
is enabled, `findClass`/`type`, `callStatic`, `new`, and default-thread
`create` route through the existing allowlist-only `java` bridge; dynamic dex,
jar loading, callback wrapping, and thread-mode mutation remain stable
rejections.

## Evidence

- Runtime: `app/src/main/cpp/node_bridge_sources.cpp`
- Focused test:
  `EmbeddedScriptMvpInstrumentationTest#autojsGlobalV1ExposesFrozenDiagnosticsAndGatedJavaFacade`
- Type declarations: `docs/nodejs/types/autojs6-node/autojs_global.d.ts`
- Type smoke: `sample/nodejs/typescript-smoke/src/main.ts`
- Host verifier: `tools/nodejs/phase13/verify-autojs-global-v1.js`
- Gradle gate: `:app:verifyNodePhase13AutoJsGlobalV1`
- Report artifacts:
  - `build/reports/nodejs/phase13-autojs-global-v1.json`
  - `build/reports/nodejs/phase13-autojs-global-v1.md`

## Boundaries

This task does not promote full Auto.js Pro Java parity. `findClass`,
constructors, static calls, instance calls, fields, object lifetime, thread
dispatch, reflection denial corpus, and Java callback wrapping continue under
P13-13. `require("rhino").install()`, `importClass`, `importPackage`, and
`JavaAdapter` now has the P13-16 stable denial / `defineClass` reservation, but
the actual proxy-interface provider remains later work.

The `pro.entry_java_rhino` surface therefore remains `debug_poc`: `$autojs`
exists as a safe diagnostic and gated entry point, while raw Java/Android and
Rhino compatibility remain fail-closed unless a later gate promotes them.

## Validation

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase13AutoJsGlobalV1
```
