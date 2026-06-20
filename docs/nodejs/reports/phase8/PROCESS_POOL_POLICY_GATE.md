# S8-43 Process Pool Policy Gate

Status: complete as a default-disabled process-pool policy gate.

## Scope

S8-43 prepares the Android side of a future multi-runtime process pool while
keeping the v1.1 runtime primary-only:

- `NativeNodeEmbeddedScriptService` remains the default script service.
- `NativeNodeEmbeddedScriptServiceSlot1` and `NativeNodeEmbeddedScriptServiceSlot2`
  are statically declared as non-exported reserved components.
- `NodeEmbeddedProcessPoolPolicy` reports declared slots, device-memory-aware
  recommended slot count, effective active-slot limit, long-running slot
  reservation, queue policy, and crash-isolation policy.
- Script results include `embedded_script.process_pool.*` native diagnostics.

## Current Decision

The process pool is not enabled by default:

- effective active slots: `1`;
- selected slot: `primary`;
- mode: `primary_only`;
- long-running dedicated slot: reserved but disabled;
- overflow slot: reserved but disabled;
- queue-when-full: disabled until a real pool coordinator exists.

This keeps the existing one-active-execution-per-process policy intact. S8-43
does not permit overlapping Node/V8/libuv lifecycles in one process and does not
start multiple embedded service processes for normal scripts.

## Validation

Focused Sony gate:

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeEmbeddedProcessPoolPolicyInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
```

Result:

```text
XQ-AT72 - 12: 4 tests passed, 0 failed
```

Compile gate:

```powershell
.\gradlew.bat --console=plain :app:compileAppDebugKotlin :app:compileAppDebugAndroidTestKotlin
```

Result: passed.

## Remaining Work

Future promotion still needs:

- a pool coordinator that owns slot acquisition and release across processes;
- queue-when-full behavior tied to the pool, not just the caller-side queue;
- long-running slot routing and foreground notification policy;
- crash detection that removes only the failed slot;
- packaged manifest synthesis for each slot;
- stress gates proving no cwd, env, module cache, provider, bridge transport,
  fd, watcher, timer, or handle crosses slot or execution boundaries.
