# P13-24 Shell/Root/Shizuku Capability

Status: partial

Date: 2026-06-24

## Landed Scope

P13-24 records the current controlled shell provider and splits the authority
vocabulary for ordinary shell, root shell, and Shizuku.

- `shell` remains the ordinary controlled shell capability.
- `shell.root` remains the explicit root capability. It is required when
  `shell.exec(..., { root: true })`, `shell.execRoot(...)`, or
  `compat.shell(command, true)` is used under an enforced manifest.
- `shell.shizuku` is now a defined capability token, but it is vocabulary-only
  in this partial landing.
- `shell.execRoot(command, options)` is a JavaScript facade over the existing
  `shell.exec()` bridge path with `root: true`.
- `shell.execShizuku(command, options)` is a stable rejection stub returning
  `ERR_AUTOJS6_SHELL_SHIZUKU_UNSUPPORTED`.
- `shell.exec(..., { shizuku: true })` also rejects before bridge dispatch.
- `shell.checkAccess({ shizuku: true })` uses the explicit `shell.shizuku`
  capability token while still reporting `shizuku: false` because no provider
  is promoted.
- Manifest enforcement diagnostics now distinguish root and Shizuku authority:
  undeclared root requests report missing `shell.root`, combined
  `checkAccess({ root: true, shizuku: true })` reports
  `shell.root|shell.shizuku`, and Shizuku-only access reports
  `shell|shell.shizuku` while execution remains unsupported.
- `autojs6:compat.shell` exposes the same `execRoot`, `execShizuku`,
  `execFile`, `checkAccess`, and timeout helpers.

## Metadata Examples

`project.json shell.root metadata example`:

```json
{"node":{"permissions":["shell","shell.root"]}}
```

`package.json shell.shizuku metadata example`:

```json
{"autojs6":{"node":{"permissions":["shell","shell.shizuku"]}}}
```

These examples document separate capability declarations only. Declaring
`shell.shizuku` does not promote Shizuku execution; `shell.execShizuku()` and
`shell.exec(..., { shizuku: true })` still reject before bridge dispatch.
For P13-24 v1.1, Shizuku execution stays stable-denied. A future promotion
must be owned by a dedicated provider with service binding, permission
request/denial, lifecycle, cleanup, packaged disclosure, and rollback evidence;
it must not turn the vocabulary-only `shell.shizuku` token into executable
authority by declaration alone.

## Evidence

- Capability split:
  `app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt`
- Runtime facade: `app/src/main/cpp/node_bridge_sources.cpp`
- Android controlled shell provider:
  `app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt`
- Provider registry details:
  `app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt`
- Type declarations:
  `docs/nodejs/types/autojs6-node/shell.d.ts` and
  `docs/nodejs/types/autojs6-node/rhino_compat.d.ts`
- Type smoke: `sample/nodejs/typescript-smoke/src/main.ts`
- Conformance:
  `app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgeConformanceInstrumentationTest.kt`
- Split capability diagnostics:
  `app/src/androidTest/java/org/autojs/autojs/engine/NodeShellBridgeInstrumentationTest.kt`
  and
  `app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgePermissionManifestInstrumentationTest.kt`
- Host verifier:
  `tools/nodejs/phase13/verify-shell-root-shizuku-capability.js`
- Gradle gate: `:app:verifyNodePhase13ShellRootShizukuCapability`
- Generated reports:
  `build/reports/nodejs/phase13-shell-root-shizuku-capability.json` and
  `build/reports/nodejs/phase13-shell-root-shizuku-capability.md`

## Boundary

The Android bridge still exposes only `shell.exec` as its native dispatch
method. `execFile`, `execRoot`, and `execShizuku` are JavaScript facades:
`execFile` validates and quotes scoped relative paths, `execRoot` requests
`shell.root`, and `execShizuku` rejects without dispatch.

Shizuku is not executed by Node in P13-24 v1.1. No Shizuku service, Binder,
user service, shell process, file descriptor, stream, signal handle, `Process`,
root shell handle, or Android shell object crosses the bridge. Declaring
`shell.shizuku` is only auditable vocabulary until a dedicated Shizuku provider
is promoted.

Packaged queued fallback must not execute shell commands. Packaged shell use
requires live bridge metadata plus explicit project capabilities, and remains
partial until user-facing disclosure and failure diagnostics are complete.

## Remaining Blockers

- Root device matrix for devices with and without `su`, including stable denial
  and command-result evidence.
- AbortSignal cancellation instrumentation for in-flight shell commands.
- Packaged shell/root capability disclosure and rollback policy.
- Expanded audit report taxonomy for denied authorization, timeout, and
  resource-limit failures.
- Migration examples from Rhino `shell` and root shorthand.

## Verification

Expected commands:

```powershell
node tools\nodejs\phase13\verify-shell-root-shizuku-capability.js --repo-root D:\idea-projects\AutoJs6 --report-dir D:\idea-projects\AutoJs6\build\reports\nodejs
.\gradlew.bat --console=plain :app:verifyNodePhase13ShellRootShizukuCapability
.\gradlew.bat --console=plain :app:verifyAutoJs6NodeTypeDeclarations :app:typeCheckNodeTypescriptSmoke
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeBridgeConformanceInstrumentationTest"
```
