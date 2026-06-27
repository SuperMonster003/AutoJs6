# Phase 8 Inspector Security Gate

Status: disabled-state security gate complete

S8-40 hardens the existing disabled inspector policy. It does not expose a
debugger endpoint, port, DevTools URL, profiler channel, or script-visible
`inspector` API.

## Focused Gate

Run the focused gate on the Sony device:

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeInspectorSecurityGateInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
```

`NodeInspectorSecurityGateInstrumentationTest` verifies:

- release build metadata cannot enable the high-risk `inspector` flag;
- `project.json` and `package.json autojs6.node` metadata cannot enable
  inspector for packaged release behavior;
- hostile `NODE_OPTIONS=--inspect=<host>:<port>` values for non-loopback hosts
  remain inert and leave the checked loopback port bindable;
- hostile loopback `NODE_OPTIONS=--inspect=127.0.0.1:<port>` remains inert
  after normal script stop and timeout cleanup;
- script-visible `process.env.NODE_OPTIONS` and
  `process.env.NODE_INSPECT_RESUME_ON_START` stay absent, `process.execArgv`
  stays empty, and `process.features.inspector` / `process.features.debug`
  stay false;
- disabled runs do not emit inspector native diagnostics;
- profiler artifact extensions remain absent under the execution work
  directory, and project-sensitive marker data does not leak through captured
  stdout, stderr, error fields, stack fields, or native diagnostics.

## Runtime Boundary

S8-40 adds a startup boundary before native Node initialization:

```text
NODE_OPTIONS
NODE_INSPECT_RESUME_ON_START
NODE_COMPILE_CACHE
NODE_DISABLE_COMPILE_CACHE
```

The Kotlin request sanitizer removes these keys before the embedded request is
handed to native startup. The generated script environment still filters the
script-visible snapshot, so the same keys are also absent from `process.env`.

Native Node initialization now uses `kDisableNodeOptionsEnv` and
`kNoParseGlobalDebugVariables` in addition to the legacy embedded
initialization behavior. The stdout-capture path still preserves the existing
stdio initialization behavior needed by the bridge, but it also inherits the
disabled startup-env and global-debug-variable policy.

## Lifecycle Evidence

The gate treats inspector startup as hostile input instead of a supported
debugging mode. Because the current embedded service is a one-shot Node
process and does not guarantee a second libnode/V8/libuv startup in the same
`:nodejs_embedded` process, the test settles the embedded service process
between host attempts and between the normal-stop and timeout probes.

The accepted result is absence:

- no listener survives normal script completion;
- no listener survives script timeout;
- no non-loopback host is accepted through `NODE_OPTIONS`;
- no profiler artifact is generated;
- no metadata override creates a packaged or release inspector path.

Future inspector promotion must remain a separate debug-build-only feature
with explicit user action, loopback-only random ports, visible revocation,
deterministic shutdown, packaged release denial, and rollback to this disabled
gate.
