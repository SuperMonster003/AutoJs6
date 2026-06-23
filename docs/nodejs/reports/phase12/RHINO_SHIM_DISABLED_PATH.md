# Rhino Shim Disabled Path

Status: P12-24 disabled/default denial path implemented.

P12-24 reserves `require("rhino")` as a controlled AutoJs6 module name in the
default Safe Node Profile. The module is intentionally a disabled facade: it
does not execute Rhino without an explicit P12-25 `experimental: true` request,
does not dispatch default calls to Android or Rhino bridge providers, and does
not install any globals.

## Runtime Contract

Default behavior:

- `require("rhino")` returns a frozen facade with schema
  `autojs6-rhino-json-only-shim-disabled-v1`;
- `require.resolve("rhino")` returns `rhino`;
- a local `node_modules/rhino` package cannot shadow the controlled module;
- `require("node:rhino")` remains unavailable because there is no Node builtin
  alias;
- `rhino.isEnabled()` returns `false`;
- `rhino.status()` returns a frozen object with `enabled: false`,
  `mode: "disabled"`, `run: "disabled"`, `install: "unsupported"`,
  `globalsInstalled: false`, `nodeAlias: false`, and
  `shadowing: "reserved"`;
- `rhino.run()` without `experimental: true` returns a rejected Promise with
  `ERR_AUTOJS6_RHINO_SHIM_DISABLED`;
- `rhino.run({ experimental: true, source|path, timeoutMs, args })` is the
  separate P12-25 JSON-only execution adapter POC;
- `rhino.install()` throws `ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED`.

The disabled facade is not `autojs6:compat`. It does not add `compat.rhino`,
does not add a global `rhino`, and does not install `importClass`,
`importPackage`, `Packages`, `java`, `android`, `JavaAdapter`, `toast`,
`files`, `images`, selector globals, `threads`, or any other Rhino-era global.

## Stable Error Shape

`rhino.run()` default denial:

```text
code: ERR_AUTOJS6_RHINO_SHIM_DISABLED
autojs6Code: ERR_AUTOJS6_RHINO_SHIM_DISABLED
module: rhino
api: run
category: disabled
```

`rhino.install()` denial:

```text
code: ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED
autojs6Code: ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED
module: rhino
api: install
category: unsupported
```

The Safe Node Profile also exposes these codes through
`require("autojs6:profile").errorCodes.rhinoShimDisabled` and
`require("autojs6:profile").errorCodes.rhinoInstallUnsupported`.

## Type Contract

`docs/nodejs/types/autojs6-node/rhino.d.ts` declares the disabled facade and
the explicit P12-25 POC request/result types:

- `mode: "disabled"`;
- `enabled: false`;
- `isEnabled(): false`;
- `status(): Status`;
- `run<T>(request: RunRequest): Promise<RunResult<T>>` for requests that carry
  `experimental: true`;
- `run(request?: unknown): Promise<never>` for default-denied requests;
- `install(options?: unknown): never`;
- `errorCodes.shimDisabled`;
- `errorCodes.installUnsupported`.

The type package still declares no `compat.rhino` surface and no Rhino globals.

## P12-25 Boundary

P12-24 did not add the execution adapter. P12-25 adds the experimental
JSON-only `rhino.run({ experimental: true, source|path, timeoutMs, args })`
adapter while preserving this default-denial path. `rhino.install()` remains
unsupported and must not become available because of the experimental flag.

P12-26 records packaged metadata behavior as fail-closed. Packaged APKs without explicit shim metadata cannot execute Rhino through Node, and Phase 12 accepts no packaged metadata shape that would enable the P12-25 POC in an APK.

## Evidence

- Runtime facade: `app/src/main/cpp/node_bridge_sources.cpp`
- P12-25 execution adapter:
  `app/src/main/java/org/autojs/autojs/engine/NodeBridgeRhinoExecutionService.kt`
- Types: `docs/nodejs/types/autojs6-node/rhino.d.ts`
- Type smoke: `sample/nodejs/typescript-smoke/src/main.ts`
- Type declaration gate: `app/node-examples.gradle.kts`
- Instrumentation gate:
  `app/src/androidTest/java/org/autojs/autojs/engine/NodeRhinoShimDesignGateInstrumentationTest.kt`
- Host verifier:
  `tools/nodejs/phase12/verify-rhino-compat-rhino-shim-disabled-path.js`
- P12-25 host verifier:
  `tools/nodejs/phase12/verify-rhino-compat-rhino-json-only-execution-adapter-poc.js`
- P12-26 packaged metadata gate:
  `docs/nodejs/reports/phase12/RHINO_SHIM_PACKAGED_METADATA_GATE.md`
- Gradle gate: `:app:verifyNodeRhinoCompatRhinoShimDisabledPath`
