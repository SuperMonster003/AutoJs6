# External Node Runtime Plugin Backend

R5 makes an external Android service the default Node.js runtime delivery for
the main AutoJs6 app. The host discovers an authorized runtime service, binds
through the Node runtime AIDL contract, and executes user code in the plugin's
dedicated process. The JavaScript compatibility surface remains the AutoJs6
Safe Node Profile regardless of where the native runtime is delivered.

## Scope And Non-Goals

Three mechanisms use the word "plugin" in this repository and must not be
treated as interchangeable:

- The R5 `plugin` backend is the external Binder runtime described here. Node,
  V8, libuv, and `libautojs6-node.so` execute inside the runtime plugin process;
  the host APK does not load those libraries for this backend.
- `NodeJsPluginRuntimeLoader` is the legacy compatibility path that resolves
  `libnode.so` and `libautojs6-node.so` from an installed plugin APK and loads
  them into an AutoJs6-owned process. It remains available for embedded/native
  diagnostics and compatibility, but it is not the `nodePlugin` distribution
  contract and is not the default release architecture.
- `require("plugins")` is a project-local JS-only module manager. It does not
  discover Android services, carry native libraries, or participate in runtime
  backend selection.

Packaged Node APKs are also separate from the R5 main-app distribution. The
APK builder injects the selected Embedded Node libraries and service manifest
entries so the exported APK is self-contained; an exported app must not depend
on an external runtime plugin being installed on the device.

## Release And Backend Policy

`autojs.nodejs.releaseFlavor` determines the default backend and the native
payload policy:

| Release flavor | Release/gate backend | Host Embedded Node payload | Policy |
| --- | --- | --- | --- |
| `nodePlugin` | `plugin` | none | External plugin is required. |
| `nodeFull` | `embedded` or `pluginOptional` | all supported ABIs | Embedded is the default; optional plugin-first execution is allowed. |
| `nodeMini` | `embedded` or `pluginOptional` | configured ABI set | Embedded is the default; optional plugin-first execution is allowed. |
| `rhinoOnly` | `rhino` | none | Node-marked entries fail clearly when Node is unavailable. |

`nodePlugin` is the default main-app release flavor. Release and focused gate
tasks enforce the table and fail during configuration for an incompatible
backend. Non-release debug builds may still select `rhino`, `native`,
`embedded`, `plugin`, `pluginOptional`, or `auto` as explicit diagnostic
overrides.
`pluginOptional` is accepted only with `nodeFull` or `nodeMini`, because those
flavors package the Embedded Node fallback.

The two plugin modes differ at the failure boundary:

- `plugin` is required. Discovery, trust, bind, or contract failure is returned
  to the caller and never silently changes the runtime.
- `pluginOptional` tries the external service first. It may run the embedded
  fallback exactly once only when diagnostics prove that user code was not
  dispatched to the plugin. Once dispatch may have begun, there is no fallback
  and no automatic replay.

## Discovery, Trust, And Candidate Identity

The host queries the Node runtime service action and engine category. A
candidate must be exported and enabled, use the exact signature permission
`org.autojs.permission.PLUGIN`, be enabled in host plugin state, be compatible
with the current host version, and resolve to an `official`, `trusted`, or
explicitly `user_granted` authorization state. The host must also hold the
service permission.

Eligible candidates are ordered by descending package version and update time,
then by package and service name. Cached candidate identity includes the
component, version code, last update time, UID, and signer SHA-256. A changed
identity retires the old Binder session rather than reusing it for a replaced
package.

The application-scoped candidate cache is invalidated for package add, remove,
replace, change, and data-clear broadcasts. Host-side enable, authorization,
and trust changes notify the same invalidation path. Invalidation clears the
candidate snapshot, retires affected pooled connections, and unbinds them;
the next operation discovers and negotiates again.

## Binder Session And Process Lifetime

The host keeps one pooled Binder connection per selected component. Concurrent
bind waiters share a single connection attempt, and a successful connection
caches the negotiated runtime info. `linkToDeath` invalidates a dead session;
the next acquisition performs one atomic reconnect and advances the session
generation. The host never treats reconnection as proof that an in-flight
execution did not run.

Application startup schedules a best-effort plugin prewarm on a background
thread for both `plugin` and `pluginOptional`. Script RPC calls run on the
dedicated plugin RPC executor rather than the Android main thread or a
`ServiceConnection` callback.

The plugin process and process runtime persist across successful calls, while
each execution receives fresh JavaScript globals, environment state, timers,
and execution-owned resources. A Binder session or runtime process may still
be retired after package/trust invalidation, Binder death, cancellation, or
explicit host cleanup.

## Contract And ABI Negotiation

Before user-code dispatch, the host requires the exact current runtime contract
version, non-empty `runtimeSlot` and `nodeVersion`, and the mandatory transport,
native runtime, host capability broker, live bridge, on-demand module source
provider, and `scopedWorkspaceArchiveTransport` capabilities.

R5 additionally requires a dedicated persistent runtime process with:

- `persistentProcessRuntime=true`;
- `dedicatedRuntimeProcess=true`;
- `maxConcurrentExecutions=1`;
- `queueCapacity=0`;
- `cancellationMode=process_restart`;
- advertised `persistentProcessRuntime`, `singleActiveBackpressure`, and
  `processRestartCancellation` lifecycle capabilities for the live R5 gate.

The reported `processAbi` must be non-empty, appear in the plugin's
`supportedAbis`, and be supported by the current device. Any contract,
mandatory-capability, lifecycle, or ABI mismatch fails as
`ERR_AUTOJS6_NODE_PLUGIN_CONTRACT_MISMATCH` before dispatch. In
`pluginOptional` mode this pre-dispatch failure is fallback-safe; required
`plugin` mode still fails closed.

## Scoped Workspace Archive Transport V2

The host and runtime plugin run under different Android UIDs, so the plugin
cannot safely execute against a raw host-private working-directory path. For
each request, workspace archive contract v2 maps only input paths already named
by that request. The host does not recursively enumerate or copy the sandbox.
It sends the bounded input archive, output archive target, and relative working
directory through owned `ParcelFileDescriptor` values.

The plugin validates the contract and archive limits, materializes the explicit
inputs beneath a request-specific plugin-private mirror, and executes there.
After a stable execution it walks only that private mirror to produce the
output archive. The archive returns output files plus a deletion
tombstone manifest as its final entry. Only files declared as explicit inputs
may appear in that deletion set.

Before changing host files, the host validates the complete output archive and
its final tombstone, rechecks sandbox identity, path containment, symlink state,
and regular-file identity, then writes outputs back and applies authorized
deletions. A missing final tombstone is an incomplete commit, so cancellation
or process death cannot turn a partial archive into host-side deletions. The
accepted R5 diagnostic invariant is
`legacy_directory_scan_ran=false` and `input_directory_scan_count=0`; the only
directory scan belongs to the plugin-private output mirror.

## Execution, Backpressure, And Cancellation

The external runtime admits one execution and exposes no waiting queue. A
concurrent request is rejected immediately with
`ERR_AUTOJS6_NODE_PLUGIN_BUSY`, `admission.status=busy`, queue capacity `0`,
and active-execution attribution. It is not routed through the embedded queue
and is not fallback-safe.

Cancellation before backend dispatch is handled by the host request lifecycle.
After dispatch, the host calls the plugin's `cancelScript(executionId)` method.
The R5 service contract implements forceful cancellation by restarting the
plugin runtime process, so the cancelled process/session is not reusable and a
later operation must reconnect with a new PID. The live R5 gate cancels an
active execution and verifies this replacement rather than accepting capability
negotiation alone. Callers must not rely on plugin PID, process runtime
generation, or in-memory process state surviving cancellation.

## Failure And Replay Boundary

Fallback safety is explicit in
`embedded_script.runtime_plugin.fallback_safe`:

| Failure point | Stable result | Optional embedded fallback |
| --- | --- | --- |
| No eligible service, permission/bind failure, or session preparation before dispatch | `ERR_AUTOJS6_NODE_PLUGIN_UNAVAILABLE` | Allowed once. |
| Contract, capability, lifecycle, or ABI negotiation mismatch | `ERR_AUTOJS6_NODE_PLUGIN_CONTRACT_MISMATCH` | Allowed once. |
| Single-active admission rejection | `ERR_AUTOJS6_NODE_PLUGIN_BUSY` | Forbidden. |
| Binder/transaction response loss after dispatch may have started | `ERR_AUTOJS6_NODE_PLUGIN_EXECUTION_LOST` | Forbidden. |
| Script timeout or cancellation after dispatch | Script/cancellation error | Forbidden. |

The dispatch boundary is deliberately conservative. Once `runScript` begins,
a failed Binder transaction can mean that the plugin already executed user
code but the host lost the response. AutoJs6 never replays that request and
never falls back automatically, preventing duplicate filesystem, UI, network,
or other side effects. A later retry must be an explicit user decision with a
new execution identity.

## Diagnostics

Successful and failed plugin results preserve package, service, version, update
time, UID, signer SHA-256, authorization state, runtime slot, Node version,
capability, host broker, fallback, and error attribution under
`embedded_script.runtime_plugin.*`.

Host lifecycle and performance diagnostics include:

- `discovery_elapsed_ms`, `candidate_cache_hit`, and
  `candidate_cache_generation`;
- `bind_elapsed_ms`, `session_reused`, and `session_generation`;
- `binder_death_count` and `reconnect_count`;
- `session_package_name` and `session_service_name`;
- `backend_dispatched`, `fallback_safe`, `optional_fallback_used`, and
  `optional_fallback_reason`;
- `admission.status`, `admission.queue_capacity`, and
  `admission.active_execution_id`;
- `workspace.contract_version`, `workspace.mapped`, `workspace.status`,
  `workspace.legacy_directory_scan_ran`,
  `workspace.input_directory_scan_count`,
  `workspace.output_private_workspace_scan_count`, and
  `workspace.deleted_input_files`.

Contract and ABI failures keep their actionable message in the stable error
envelope. Runtime plugin errors are cataloged in
[ERROR_CATALOG.md](../ERROR_CATALOG.md).

## R5 Verification Gate

The focused live-device gate is `verifyNodeR5RuntimePluginGate`, selecting
`org.autojs.autojs.engine.NodeRuntimePluginR5InstrumentationTest`. It requires
an installed, enabled, authorized, ABI-compatible runtime plugin and contains no
`Assume`-based skip for a missing or incompatible service.

The gate uses `nodeMini` plus `pluginOptional` so the same run can prove both
the external backend and the pre-dispatch embedded fallback boundary:

```powershell
$nodeRuntimeProject = (Resolve-Path ..\AutoJs6-Plugin-NodeJs-Runtime).Path
$gradleArgs = @(
    ':app:verifyNodeR5RuntimePluginGate'
    '-Pautojs.nodejs.test.deviceProfile=sony'
    '-Pautojs.nodejs.releaseFlavor=nodeMini'
    '-Pautojs.nodejs.backend=pluginOptional'
    '-Pautojs.nodejs.embedded.script.enabled=true'
    '-Pautojs.nodejs.embedded.persistentRuntime=true'
    '-Pautojs.nodejs.releaseAbis=arm64-v8a'
    "-Pautojs.nodejs.pluginProjectDir=$nodeRuntimeProject"
    "-Pautojs.nodejs.runtimeJniLibs=$nodeRuntimeProject\app\src\main\jniLibs"
    '-Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.r5.pluginWarmSamples=30'
    '-Pksp.incremental=false'
    '--console=plain'
)

& .\gradlew.bat @gradleArgs
exit $LASTEXITCODE
```

This command defines the gate topology; it is not acceptance evidence by
itself. The final R5 run passed all eight ordered tests with zero skipped tests
for `arm64-v8a` and `armeabi-v7a` on the Sony XQ-AT72 and `x86_64` on an
Android AVD. Each ABI used 30 runtime-warm samples:

| Plugin ABI | Warm wall p50 | Warm wall p95 |
| --- | ---: | ---: |
| `arm64-v8a` | 231 ms | 254 ms |
| `armeabi-v7a` | 240 ms | 265 ms |
| `x86_64` | 182 ms | 212 ms |

The immutable commands, identities, hashes, and semantic results are archived
in [R5_ACCEPTANCE_SAMPLING.md](../R5_ACCEPTANCE_SAMPLING.md). See also
[TESTING.md](../TESTING.md), [PERFORMANCE.md](../PERFORMANCE.md), and
[RELEASE_FLAVORS.md](RELEASE_FLAVORS.md).
