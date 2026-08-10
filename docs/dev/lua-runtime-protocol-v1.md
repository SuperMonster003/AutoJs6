# Lua Runtime Plugin Protocol V1

This document freezes the R0 contract for executing a single Lua source in an
independent Android plugin process. The API module is buildable contract
infrastructure. It does not make `.lua` runnable in the host and does not claim
that a production provider or Android Binder adapter exists.

The checked implementation roadmap is
[Lua Runtime Integration Roadmap](../lua/ROADMAP.md). Structured messages use
[Plugin Protocol Wire V1](plugin-protocol-wire-v1.md).

## V1.0 Scope And Non-Goals

V1.0 supports one UTF-8 Lua source, bounded arguments, a bounded return value,
stdout/stderr, errors, cancellation, deadlines, and request-bound host
capability calls. Source content is transported through a read-only
`ParcelFileDescriptor`.

V1.0 does not provide projects, arbitrary module graphs, UI, accessibility,
network, shell, APK packaging, debugger, Java reflection, Java objects, a
general Parcelable/Serializable channel, or Rhino `ScriptRuntime` access.
There is no compatibility promise for precompiled Lua chunks across runtime
slots or language versions.

## Stable Identities And Discovery

| Identity | V1 value | Meaning |
| --- | --- | --- |
| Plugin ID | `lua-runtime` | Plugin family identity |
| Plugin engine | `lua` | Plugin metadata/discovery category |
| Initial plugin variant | `puc-lua54` | Independently released PUC Lua 5.4 provider variant |
| Runtime provider ID | Provider-defined stable ID | Pinned implementation identity, never an Android trust authority |
| Planned host engine | `lua` | R1 `LuaScriptSource` engine key |
| INFO action | `org.autojs.plugin.INFO` | Common plugin metadata service |
| Runtime action | `org.autojs.plugin.lua.RUNTIME` | Runtime service discovery action |
| Runtime slot | `lua54` | PUC Lua 5.4 semantic/bytecode slot |
| API package | `org.autojs.plugin.lua.runtime.api` | Public AIDL and wire API |

Discovery reads the common INFO service and the Lua runtime service. After a
candidate is selected, binding uses an explicit component. A service must
return its binder for an explicit component-only bind whose Intent action is
null; the discovery action is not a binder handshake requirement.

The INFO `id`, `engine`, and `variant` must match the table, and INFO/runtime
services must resolve to the same package. INFO `versionCode` must equal both
the installed package version code and RuntimeInfo `providerVersionCode`.
INFO `supportedAbis` must be a non-empty, unique, valid, complete inventory of
the packaged native ABIs, and it must equal RuntimeInfo `supportedAbis` as a
set. Runtime `providerId` distinguishes stable implementations inside that
trusted package; it never substitutes for component, UID, signer, or package
verification.

The R1 host adapter must reject disabled/unexported services, wrong binding
permissions, unsupported host versions, unsupported ABI, protocol mismatch,
untrusted signer sets, and metadata/action inconsistencies. It pins the exact
component, package UID, complete normalized signer set, package version and
last-update identity, runtime info, negotiated version, runtime slot, ABI, and
capabilities, then revalidates them immediately before dispatch. Multiple
eligible providers are ambiguous unless the user selected one exact component.

## Binder Surface

```text
ILuaRuntimeProvider
  getRuntimeInfo()
  createExecution(request, sourcePfd, callback, hostBroker) -> session

ILuaExecutionSession (oneway)
  start()
  grantOutputCredits(count)
  cancel()
  close()

ILuaExecutionCallback (oneway)
  onStarted(metadata)
  onOutput(output)
  onCompleted(result, descriptors)
  onFailed(error)
  onCancelled(cancellation)

ILuaHostCapabilityBroker (oneway)
  invoke(request, descriptors, callback)
```

`createExecution` creates a session but does not run source. The host may grant
initial output credits, then calls `start`. Provider implementations make
`start`, `cancel`, and `close` idempotent even though duplicate callback events
remain protocol violations.

All structured arguments are bounded TaggedWire `byte[]`. The source PFD is
the only active data descriptor in V1.0. The completion and host-call descriptor
arrays are reserved so a compatible minor protocol can add descriptor-backed
payload records without changing AIDL. V1.0 requires those arrays to be empty;
the lifecycle/validation policy rejects a nonzero descriptor count.

Generated AIDL stubs in the current build do not prove strict trailing Binder
data rejection, so `GENERATED_AIDL_ENFORCES_NO_TRAILING_DATA` is false. Envelope,
UID, signature, identity, PFD, lifecycle, and quota validation remain mandatory.

## Source PFD Rules

The execution request carries a logical source label, exact byte length, and
SHA-256. It never carries a host path, raw URI, `Context`, or storage authority.
The label rejects absolute, parent-traversing, Windows-path, and URI forms and
must never be interpreted as ambient filesystem authority.

The provider duplicates the read-only descriptor into provider-owned lifetime
before `createExecution` returns. Its worker then:

1. Reads exactly the declared length within the negotiated source limit.
2. Rejects early EOF, additional trailing bytes, and read errors.
3. Verifies SHA-256 before parsing or executing source.
4. Closes the owned descriptor on every success, failure, cancellation,
   deadline, Binder death, and session-close path.

The host closes its copy after the synchronous creation call returns. Source
dispatch happens once. A lost response after dispatch never causes automatic
redispatch because the script may already have produced side effects.

The V1 synchronous Binder ABI for INFO lookup, `getRuntimeInfo()`, and
`createExecution()` does not provide a transaction deadline. Interrupting the
calling thread or reaching a host deadline cannot terminate a wedged remote
Binder transact.

The R1 host contains one process-wide daemon lane for these plugin calls. The
lane has capacity one and no backlog or replacement worker. Each complete
preparation or acquisition phase propagates one monotonic budget instead of
resetting a timeout per provider, and the host rejects more than 16 discovered
candidates. Timeout or caller interruption abandons the local wait and makes
later calls fail closed while the original call remains in flight; this bounds
the host caller, not the remote transaction.

The lane covers untrusted plugin Binder calls. PackageManager/system-server
queries are checked against the remaining phase budget before and after the
call, but an individual platform Binder transaction is not made cancellable.

Read-only INFO/runtime-info calls may drain late without making their data
current. An abandoned `createExecution()` has an uncertain dispatch outcome:
the host revokes its callback and broker, permanently poisons the process-wide
Lua lane until the host process restarts, never retries the request, and never
starts a late session. The single worker owns the source PFD during the call and
cancels then closes any late session before releasing the lane. This policy is
implemented in R1 source, but Android Binder/PFD and hostile-provider evidence
is still required before bounded stop or production readiness is claimed.

## Runtime Information And Negotiation

`LuaRuntimeInfo` declares a protocol range, provider identity/version, runtime
family, runtime slot, language version, active process ABI, packaged ABIs,
capability names, host version bounds, and source/memory/output/time/concurrency
limits. V1 accepts the `PUC_LUA` family and at most one concurrent execution per
provider process. The RuntimeInfo packaged ABI set must exactly match the INFO
inventory. The process ABI must appear in that inventory and also match a
device-supported process ABI before dispatch.

The host selects the highest common protocol version and intersects every
request with host policy and provider limits. Required capabilities must be a
subset of the pinned provider/host intersection. A mismatch fails before
source dispatch.

## Lua Value Model

The complete cross-process semantic set is:

```text
nil / boolean / int64 / finite float64
UTF-8 string / bytes
dense array / string-key map
```

The default ceilings are depth 32, 4,096 value nodes, 1,023 entries per
container, 64 KiB per string or bytes value, 1 KiB per map key, and 256 KiB
aggregate string/key/bytes data. Encoded documents are additionally bounded by
the shared 256 KiB TaggedWire limit.

Map encoding sorts keys by unsigned UTF-8 bytes and rejects duplicate decoded
keys. Empty string keys are valid. Arrays preserve order and represent dense
Lua indices `1..n`. `nil` is valid as a top-level or standalone value but is
forbidden inside arrays and maps: array nil would create a hole with unstable
length semantics, and assigning map nil removes the key.

Functions, closures, coroutines/threads, userdata, metatables, cyclic tables,
mixed array/map tables, arbitrary numeric map keys, Java/Android objects, paths,
URIs, and Serializable/Parcelable values cannot cross this protocol. Future
Android resources use host-owned, session-bound opaque handles rather than raw
objects.

## Execution Lifecycle And Output Backpressure

The legal callback sequence is:

```text
created -> started -> zero or more ordered output events -> one terminal
        \-----------------------------------------------> failed/cancelled
```

Failure is allowed after `start()` but before `onStarted()` for negotiation,
queue, source, or load failures. Output is legal only after `onStarted()`. The
started event fixes the first sequence number; every output consumes one credit
and advances that sequence exactly once. Empty chunks, gaps, replay, output
without credit, credit overflow, and aggregate output beyond the request limit
are protocol violations. Validation failures do not consume credit.

Exactly one of completed, failed, or cancelled is accepted. Terminal and closed
sessions reject later data. `cancel()` and `close()` are idempotent. Once the
host records a cancellation request, cancellation owns the local race: further
output/completion/failure data is rejected and the provider must converge on a
cancelled terminal. A close releases resources and need not manufacture a
callback for an observer that has closed the session.

`LuaRetryDisposition` is advisory for a newly created execution only. The host
never automatically retries or reuses an execution ID after dispatch, timeout,
Binder death, or an ambiguous terminal outcome.

## Host Capability Broker

Each host request contains the execution ID, a session-unique call ID, a
declared capability name, and bounded `LuaValue` arguments. The host accepts
only capabilities in the pinned grant, limits outstanding calls, rejects a
replayed call ID for the entire session, limits lifetime call IDs, and returns
one matching result or error. A result/error for the wrong execution or a
non-outstanding call is a protocol violation. Closing the call policy clears
its bounded replay and outstanding-call state.

R0 defines the broker transport and policies but does not expose production
capabilities. Arguments, result, and console output are protocol-native
execution features; JSON can remain provider-local. The first broker calls are
timer, read-only device information, controlled module loading, and restricted
file access. File write, network, shell, UI, and accessibility are separate,
default-denied capability families.

## Limits And Error Semantics

Protocol ceilings include 16 MiB source, 256 MiB requested Lua memory, 8 MiB
output, 32 KiB output chunks, 128 outstanding output credits, a ten-minute
deadline, 32 outstanding host calls, 1,024 total host calls per execution, and
one concurrent execution. Runtime info declares at most 16 packaged ABIs and
64 capabilities. Provider, host policy, and request limits can only reduce
these ceilings.

Errors identify a bounded phase and code. Messages are diagnostic text and
must not be used for control flow. `TIMEOUT`, `MEMORY_LIMIT`, `OUTPUT_LIMIT`,
`RESULT_LIMIT`, `SOURCE_DIGEST_MISMATCH`, `PROTOCOL_VIOLATION`, and
`DESCRIPTOR_FAILED` remain distinct. Sensitive models summarize sizes and
identities in `toString()` instead of printing arguments, results, call IDs, or
raw error text.

If no eligible provider exists, the R1 host returns
`LUA_RUNTIME_UNAVAILABLE`. It never sends Lua source to Rhino, Node.js, or
RootAutomator. A busy provider returns `BUSY`; V1 has no hidden execution queue.

## Native Provider Requirements

The production provider belongs in the independent sibling repository
`AutoJs6-Plugin-Lua-Runtime`. Its initial `lua54` provider uses a bounded custom
allocator, periodic cancellation/deadline hooks, protected execution, a single
serialized worker per `lua_State`, and an explicitly selected standard-library
set. Unrestricted `io`, `os.execute`, `debug`, dynamic C modules, `loadlib`, and
Java reflection remain closed.

A C call that cannot honor hooks is isolated by the dedicated runtime process;
timeout recovery terminates and rebuilds that process rather than reusing a
possibly wedged state.

## Compatibility Rules

A schema minor may add optional fields or capability-gated enum values. Unknown
optional fields are skipped; unknown required fields and incompatible schema
majors fail closed. Changing terminal ownership, value semantics, descriptor
accounting, identity/trust boundaries, or required fields requires a protocol
major revision.

Canonical golden bytes for a cancellation and nested Lua value are frozen in
`LuaRuntimeGoldenWireTest`. Future readers must continue decoding the V1.0
corpus before a compatibility claim is made.
