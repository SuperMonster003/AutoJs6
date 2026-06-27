# Phase 8 Inspector Design Gate

Status: disabled design gate complete

S8-37 keeps the Node/V8 inspector disabled while adding focused evidence for
the current policy boundary. This is a negative gate, not a runtime inspector
implementation.

## Scope

The current v1.1 behavior is:

- no script-visible `inspector` or `node:inspector` builtin;
- no debugger TCP or WebSocket endpoint;
- no binding to `0.0.0.0`, Wi-Fi, cellular, LAN, or loopback;
- no ADB forwarding target because no endpoint exists;
- no active-session notification because no inspector session can start;
- no build-time, `project.json`, or packaged APK metadata override;
- no packaged release inspector support.

Any future positive inspector implementation must be a separate debug-only
task that proves loopback-only binding, random ephemeral ports, explicit user
action, visible and revocable UI/notification state, deterministic lifecycle
shutdown, packaged release denial, and rollback to this disabled behavior.

## Focused Gate

Run the focused gate on the Sony device:

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeInspectorDesignGateInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
```

`NodeInspectorDesignGateInstrumentationTest` verifies:

- `NodeRuntimeCapabilities.featureFlagsById["inspector"]` is disabled,
  high-risk, release-forbidden, and has no Gradle, project JSON, or packaged
  APK override path;
- debug-build override attempts fail with `build_time_override_denied`;
- release-build override attempts fail with `release_forbidden`;
- `project.json` metadata attempts fail with `project_json_override_denied`;
- `package.json autojs6.node.featureFlags.inspector` attempts fail with
  `packaged_apk_override_denied`;
- `require("inspector")`, `require("node:inspector")`,
  `require.resolve("inspector")`, and `require.resolve("node:inspector")`
  fail before local `node_modules/inspector` shadowing;
- `process.getBuiltinModule("inspector")` cannot bypass the disabled builtin
  policy;
- `process._debugProcess`, `process.execArgv`,
  `process.allowedNodeEnvironmentFlags`, and `process.features` remain inert;
- script-visible `process.env` filters `NODE_OPTIONS` and
  `NODE_INSPECT_RESUME_ON_START` so user-provided startup/debugger env values
  cannot be observed as an implied inspector contract, and S8-40 extends this
  to the embedded request/native startup boundary;
- disabled runs do not emit inspector native diagnostics.

## Runtime Change

S8-37 adds filtering for Node startup/debugger env keys in the generated
embedded bootstrap:

```text
NODE_OPTIONS
NODE_INSPECT_RESUME_ON_START
```

The original S8-37 filter applies to the script-visible `process.env`
snapshot. S8-40 additionally strips Node startup/debugger/cache keys before
the request reaches native startup and initializes embedded Node with
`NODE_OPTIONS` parsing disabled. This does not add a Node startup env control
surface, and it does not enable `--inspect`, `--inspect-brk`, or inspector
resume behavior.

## Safety Boundary

The current result intentionally satisfies the Safe Node Profile by absence:
there is no listener to forward, no random or fixed port to report, no
debugger-side `Runtime.evaluate`, no heap or CPU profile channel, and no
packaged APK metadata that can silently open a debugger. S8-38 and later
debugging/profiling tasks must either keep this disabled boundary or land a
full debug-only implementation with the acceptance gates listed in
`docs/nodejs/design/INSPECTOR.md`.
