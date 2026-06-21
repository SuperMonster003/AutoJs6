# Embedded Node Process Pool Policy

Status: S8-43 policy gate implemented; Phase 11 adds internal explicit
`primary` / `overflow_1` slot selection while default runtime promotion remains
disabled.

## Goal

S8-43 prepares a finite Android service-process pool for future concurrent
Embedded Node execution without changing the current Safe Node default:

```text
one active Node/V8/libuv environment per Android service process
```

The current product runtime still launches normal scripts through the primary
`NativeNodeEmbeddedScriptService` component in `:nodejs_embedded`.

## Declared Slots

The app manifest now declares three non-exported embedded script service
components:

| Slot | Component | Process | Default launch |
| --- | --- | --- | --- |
| `primary` | `NativeNodeEmbeddedScriptService` | `:nodejs_embedded` | enabled |
| `long_running` | `NativeNodeEmbeddedScriptServiceSlot1` | `:nodejs_embedded_1` | disabled |
| `overflow_1` | `NativeNodeEmbeddedScriptServiceSlot2` | `:nodejs_embedded_2` | disabled |

The default v1.1 profile still starts normal scripts on `primary`. Phase 11
adds an internal request field that can explicitly route one-shot diagnostics to
`primary` or `overflow_1`, allowing focused tests to prove two independent
Android service processes. Project metadata and package metadata still cannot
select additional slots, and `long_running` remains reserved.

## Policy Gate

`NodeEmbeddedProcessPoolPolicy` records the process-pool contract:

- schema: `autojs6-node-embedded-process-pool-v1`;
- status: `design_gated`;
- mode: `primary_only`;
- effective active slots: `1`;
- device-memory-aware recommended slot count: `1..3`;
- long-running dedicated slot: reserved but disabled;
- queue-when-full: disabled until the pool is promoted;
- shared V8 environment across slots: forbidden.

When a request explicitly selects `overflow_1`, diagnostics switch to:

- status: `explicit_slot_enabled`;
- mode: `explicit_two_slot`;
- selected slot: `overflow_1`;
- effective active slots: `2`;
- shared V8 environment across slots: forbidden.

Script result native values expose `embedded_script.process_pool.*` diagnostics
for the selected slot, declared slot count, effective limit, recommended limit,
memory profile, and reason.

## Security Boundary

S8-43 does not enable reusable Node environments or same-process concurrency.
It preserves the existing invariants:

- no overlapping `v8::Isolate`, `uv_loop_t`, or `node::Environment` inside one
  Android process;
- no shared stdout/stderr capture, bridge transport directory, capability
  provider, cwd, env, module cache, timer, watcher, fd, image handle, or
  execution id across scripts;
- non-primary slots are non-exported foreground services and remain unreachable
  from project/package metadata;
- explicit caller-side queueing remains separate from the process-pool policy.

Future promotion must add a real pool coordinator, queue-when-full behavior,
crash removal/replacement, foreground notification policy per execution,
packaged manifest synthesis, and long-running routing before any non-primary
slot becomes part of default project/package behavior.

## Tests

Focused S8-43 coverage:

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeEmbeddedProcessPoolPolicyInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
```

The gate verifies policy defaults, device-memory-aware recommendations,
manifest declarations for all three slots, default-disabled reserved slots,
primary-only script result diagnostics, explicit `overflow_1` diagnostics, and
focused primary/overflow concurrent execution with different service-process
pids.
