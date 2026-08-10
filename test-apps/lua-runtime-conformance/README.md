# Lua runtime conformance test app

This Android application is a test-only Binder fixture for the Lua Runtime
protocol. It is not a Lua implementation, a host fallback, or source shared
with the future independent production runtime plugin.

Only the debug variant exists. The manifest sets `android:testOnly="true"`,
and the application uses the normal Android debug/test signing key. Never copy
or reference a production signing key for this fixture.

The normal INFO service and `NormalLuaRuntimeService` are enabled by default.
Every hostile INFO/runtime component is disabled by default so installing the
fixture does not accidentally create an ambiguous provider set. An Android
test harness must select modes through `FakeLuaConformanceControlService`; it
does not need shell access or permission to mutate another package's
components.

| Scenario | Component | Intended behavior |
| --- | --- | --- |
| `stall-plugin-info` | `StallLuaPluginInfoService` | Blocks synchronous `IPluginInfoProvider.getInfo()` until explicit control release, then returns valid metadata late. |
| `normal` | `NormalLuaRuntimeService` | Returns valid metadata and a deterministic callback-only session. |
| `malformed-runtime-info` | `MalformedRuntimeInfoLuaRuntimeService` | Returns bytes that are not a runtime-info document. |
| `null-binding` | `NullBindingLuaRuntimeService` | Records an actionless exact-component bind and deliberately returns a null Binder without killing the shared fixture process. |
| `stall-runtime-info` | `StallRuntimeInfoLuaRuntimeService` | Blocks `getRuntimeInfo()` until explicit control release, then returns valid metadata late. |
| `stall-create` | `StallCreateLuaRuntimeService` | Retains the incoming source descriptor until explicit release, closes it, then exits without a session. |
| `late-create-return-session` | `LateCreateReturnSessionLuaRuntimeService` | Retains the incoming source descriptor and returns a live session only after explicit release. |
| `callback-before-start` | `CallbackBeforeStartLuaRuntimeService` | Emits `onStarted` before `createExecution()` returns and before host `start()`. |
| `duplicate-terminal` | `DuplicateTerminalLuaRuntimeService` | Emits two completion terminals for one request. |
| `ignore-cancel` | `IgnoreCancelLuaRuntimeService` | Starts a session, then ignores cancellation and remains non-terminal until close or process teardown. |
| `zero-credit-output` | `ZeroCreditOutputLuaRuntimeService` | Sends output while synchronous creation is retained and before the provider can receive credits, then waits for the create-stall release. Host receipt order remains diagnostic because the callback is oneway. |
| `sequence-gap` | `SequenceGapLuaRuntimeService` | Starts at sequence zero, then emits sequence one as the first output. |
| `sequence-replay` | `SequenceReplayLuaRuntimeService` | Emits sequence zero twice after a valid start. |
| `wrong-request-id` | `WrongRequestIdLuaRuntimeService` | Emits an output event carrying a request ID different from the active execution. |
| `busy-second-create` | `BusySecondCreateLuaRuntimeService` | Keeps the first started session active and returns a `BUSY` queue-phase terminal for the second session. |

Three additional host-harness modes reuse `NormalLuaRuntimeService` but bypass
the engine snapshot builder and submit a raw read-only PFD by exact component:

| Harness mode | Actual PFD bytes versus request metadata |
| --- | --- |
| `raw-source-truncated` | One byte shorter than the declared length and digest input. |
| `raw-source-trailing` | Contains the declared bytes followed by one extra byte. |
| `raw-source-digest-mismatch` | Has the declared length but one changed byte and therefore a different SHA-256. |

These are not additional discoverable runtime components. They are authored to
exercise the real cross-package Binder/PFD transfer while keeping the component
allowlist at fourteen runtime scenarios; that transfer remains `NOT RUN`.

`ILuaExecutionCallback` is oneway. For `callback-before-start` and
`zero-credit-output`, provider send order before the synchronous create reply
does not prove the host received the callback before dispatching `start` or
credits. Those two modes are ordering diagnostics; their Android tests contain
both observable receipt branches and apply the same cleanup requirements.

## Deterministic control plane

`FakeLuaConformanceControlService` is an exported, test-only Binder service in
the same `:lua_runtime` process as INFO and runtime services. It is protected by
`org.autojs.permission.PLUGIN`, and every method additionally verifies the
host UID, package ownership, and matching signature. Its action is
`org.autojs.plugin.lua.runtime.fake.CONTROL`; tests should bind it by explicit
component and retain that binding for the whole scenario.

Control operations are serialized. `selectScenario(metadataValue)` accepts only
the mode strings in the table above. It uses an internal component allowlist to
enable exactly one INFO and one runtime service in its own package:
`stall-plugin-info` selects the stalling INFO plus the normal runtime; every
runtime scenario selects normal INFO plus that runtime. `reset()` restores the
normal INFO/runtime topology. Both operations install a new counter/gate
generation, close tracked source descriptors from the old generation, and
release old waiters without exposing their late increments in the new snapshot.

`selectRuntimeAmbiguity(first, second)` is the only operation that enables two
runtime services. It requires two distinct modes whose runtime-info replies are
valid and non-stalling, while retaining exactly one normal INFO service. This
keeps accidental ambiguity out of ordinary tests.

The three targeted release methods unblock plugin INFO, runtime info, or
execution creation. The creation release covers `stall-create`,
`late-create-return-session`, and `zero-credit-output`;
`releaseAllStalls()` is the teardown escape hatch. Host unbinding from
INFO/runtime does not release these process-owned gates.

`getCounters()` returns this stable `long[24]` snapshot:

```text
0 pluginInfoCalls       1 pluginInfoExits
2 runtimeInfoCalls      3 runtimeInfoExits
4 createCalls           5 createExits
6 startCalls            7 cancelCalls          8 closeCalls
9 sourceFdsOpened      10 sourceFdsClosed
11 sessionsCreated     12 sessionsFinished
13 sourceValidations   14 startedCallbacksSent
15 outputCallbacksSent 16 terminalCallbacksSent  17 busyRejections
18 sourceReadOnlyChecks 19 sourceReadOnlyConfirmed
20 sourceEarlyEofFailures 21 sourceTrailingDataFailures
22 sourceDigestMismatchFailures
23 nullBindingCalls
```

Tests can wait for a call counter before abandoning the host operation, verify
that the matching exit counter remains unchanged after host unbind, explicitly
release the gate, and then assert late-session cancel/close plus balanced FD and
session counters.

`NullBindingLuaRuntimeService` increments `nullBindingCalls` only when its bind
Intent has no action and explicitly names that component, then returns `null`.
The fresh-process host test requires a `BIND_FAILED` provider evaluation plus
zero runtime-info/create/start/session/FD activity. It does not kill the shared
fixture process or treat authored source as executed Binder evidence.
On API 28+ the framework can deliver `ServiceConnection.onNullBinding`; on API
24-27 the same fail-closed evaluation may arrive only after the bounded exact-bind
timeout because that callback is absent. The future matrix must record both paths.

Before creating any non-stall session, the fixture duplicates its
provider-owned descriptor and performs a zero-length `pwrite` access-mode probe.
Only `EBADF` is accepted as read-only confirmation; a writable descriptor
returns zero without changing the source. The probe duplicate is tracked and
closed. The fixture then reads exactly the declared source length, rejects
early EOF and a trailing byte, compares SHA-256, and closes the tracked
descriptor. Dedicated counters distinguish all three content failures. The
raw-source Android tests additionally retain and then close the caller-owned
descriptor, require zero callbacks and zero session starts, and require the
provider-owned FD counters to balance. The ordinary non-stall path tracks the
Binder-received descriptor, its validation duplicate, and the read-only probe
rather than leaving receiver-side copies outside accounting. This proves the
fake actually consumed cross-process bytes rather than validating only request
metadata. It still does not parse or execute Lua.

The normal mode deliberately does not parse or execute Lua and is not evidence
for Lua syntax, native cancellation, ABI support, or runtime correctness.
`Build.SUPPORTED_ABIS` is advertised only as synthetic fixture metadata so INFO
and runtime inventories match; it is not evidence that this APK packages a
native library for any ABI. The stalling modes intentionally occupy a provider
Binder thread and therefore belong only in isolated hostile-provider tests.

This initial skeleton has not been assembled, installed, bound, or executed.
Android Binder ordering, caller UID, service death, `ParcelFileDescriptor`
ownership, component switching, and cleanup remain `NOT RUN` until the R2
instrumented matrix exercises them.
