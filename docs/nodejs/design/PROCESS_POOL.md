# Embedded Node Process Pool Policy

Status: S8-43 policy gate implemented; Phase 11 adds internal explicit
`primary` / `overflow_1` slot selection. Phase 13 adds a default one-shot
scheduler that can select `overflow_1` when the device profile recommends at
least two active slots and `primary` is already occupied.

## Goal

S8-43 prepares a finite Android service-process pool for future concurrent
Embedded Node execution without changing the current Safe Node default:

```text
one active Node/V8/libuv environment per Android service process
```

The current product runtime still starts the first ordinary script through the
primary `NativeNodeEmbeddedScriptService` component in `:nodejs_embedded`.
Eligible concurrent one-shot scripts may be routed to `overflow_1` in
`:nodejs_embedded_2`; long-running and packaged promotion remain separate gates.

## Declared Slots

The app manifest now declares three non-exported embedded script service
components:

| Slot | Component | Process | Default launch |
| --- | --- | --- | --- |
| `primary` | `NativeNodeEmbeddedScriptService` | `:nodejs_embedded` | enabled |
| `long_running` | `NativeNodeEmbeddedScriptServiceSlot1` | `:nodejs_embedded_1` | disabled |
| `overflow_1` | `NativeNodeEmbeddedScriptServiceSlot2` | `:nodejs_embedded_2` | disabled |

The default v1.1 profile starts normal scripts on `primary`. Phase 11 adds an
internal request field that can explicitly route one-shot diagnostics to
`primary` or `overflow_1`, allowing focused tests to prove two independent
Android service processes. Phase 13 adds client-side default scheduling for
eligible ordinary one-shot scripts: when `primary` is leased, the scheduler may
lease `overflow_1`. Project metadata and package metadata still cannot select
additional slots, and `long_running` remains reserved.

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

When the default scheduler selects a slot, diagnostics switch to:

- status: `default_scheduler_enabled`;
- mode: `default_two_slot`;
- slot selection source: `default_scheduler`;
- selected slot: `primary` or `overflow_1`;
- effective active slots: `2`;
- default scheduler fallback: `false`.

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
slot becomes part of packaged or long-running behavior. Phase 13's default
one-shot scheduler is intentionally narrower than that future full coordinator.

## Tests

Focused S8-43 coverage:

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeEmbeddedProcessPoolPolicyInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
```

The gate verifies policy defaults, device-memory-aware recommendations,
manifest declarations for all three slots, default-disabled reserved slots,
primary/default-scheduler script result diagnostics, explicit `overflow_1`
diagnostics, focused primary/overflow concurrent execution with different
service-process pids, and Phase 13 default-scheduler concurrent execution on
eligible devices.
