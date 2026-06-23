# Rhino Shim Disabled Path

Status: P12-24 disabled/default denial path implemented.

P12-24 reserves `require("rhino")` as a controlled AutoJs6 module name in the
default Safe Node Profile. The module is intentionally a disabled facade: it
does not execute Rhino, does not dispatch to Android or Rhino bridge providers,
and does not install any globals.

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
- `rhino.run()` returns a rejected Promise with
  `ERR_AUTOJS6_RHINO_SHIM_DISABLED`;
- `rhino.install()` throws `ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED`.

The disabled facade is not `autojs6:compat`. It does not add `compat.rhino`,
does not add a global `rhino`, and does not install `importClass`,
`importPackage`, `Packages`, `java`, `android`, `JavaAdapter`, `toast`,
`files`, `images`, selector globals, `threads`, or any other Rhino-era global.

## Stable Error Shape

`rhino.run()` denial:

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

`docs/nodejs/types/autojs6-node/rhino.d.ts` declares only the disabled facade:

- `mode: "disabled"`;
- `enabled: false`;
- `isEnabled(): false`;
- `status(): Status`;
- `run(request?: unknown): Promise<never>`;
- `install(options?: unknown): never`;
- `errorCodes.shimDisabled`;
- `errorCodes.installUnsupported`.

The type package still declares no `compat.rhino` surface and no Rhino globals.

## P12-25 Boundary

P12-24 does not add the execution adapter. P12-25 remains responsible for any
experimental JSON-only `rhino.run({ source, timeoutMs, args })` or scoped
`path` adapter. If that adapter is enabled later, `rhino.install()` must remain
unsupported and must not become available because of the experimental flag.

P12-26 remains responsible for packaged metadata behavior. Packaged APKs without
explicit shim metadata still cannot execute Rhino through Node.

## Evidence

- Runtime facade: `app/src/main/cpp/node_bridge_sources.cpp`
- Types: `docs/nodejs/types/autojs6-node/rhino.d.ts`
- Type smoke: `sample/nodejs/typescript-smoke/src/main.ts`
- Type declaration gate: `app/node-examples.gradle.kts`
- Instrumentation gate:
  `app/src/androidTest/java/org/autojs/autojs/engine/NodeRhinoShimDesignGateInstrumentationTest.kt`
- Host verifier:
  `tools/nodejs/phase12/verify-rhino-compat-rhino-shim-disabled-path.js`
- Gradle gate: `:app:verifyNodeRhinoCompatRhinoShimDisabledPath`
