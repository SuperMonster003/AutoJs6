# Host capability broker contract V1

Status: host implementation for 3-Stove Agent roadmap P1.1/P1.3. The public module is
`plugin-api/host-capability-api`, package `org.autojs.plugin.host.capability.api`.
MCP contract V2 and 3-Stove Agent contract V2 consume the same data plane. A plugin does
not need an MCP server, an HTTP connection or a second accessibility service.

The complete P1 3-Stove Agent host integration requires AutoJs6 6.8.0 / build 5285.
This is the Agent family's minimum, not a new minimum for existing MCP V1 APKs.
The two shared-interface consumers are the Agent attachment's
`HostCapabilityBrokerStub(ThreeStoveAgentGrant)` and MCP V2's `openServerV2` session.
Legacy MCP V1 uses its existing descriptor through an adapter to the same core.
The production Agent plugin stages the shared AAR in its original P2.5 step;
the separate MCP repository controls its own V2 migration.

## Binder surface

`IHostCapabilityBroker` freezes these transactions in order:

1. `Bundle getBrokerInfo()`
2. `void dispatch(in Bundle request, IHostCapabilityCallback callback)`
3. `void destroy(in Bundle reason)`

`IHostCapabilityCallback` is oneway and has one transaction, `onResponse(Bundle)`.
Methods may be called concurrently. Dispatch only admits work on the Binder thread;
accepted work has a deadline and exactly one terminal response. Pending work is
released on broker destruction or its request deadline; callback death alone does
not immediately cancel device operations. A new link gets a new broker.

## Envelopes and ownership

`HostCapabilityContract` is the source of truth for all literal keys and byte caps.
The `getBrokerInfo()` Bundle includes `contractVersion = 1`, independently of the
consuming plugin's control-plane version. Legacy-compatible dispatch and response
Bundles do not require or return this version key. `hostCapabilityBrokerId`,
`hostCapabilityBrokerVersion`, `hostCapabilityModules` and `bridgeEngineInfo`
identify the broker. `hostCapabilityModules` contains the modules in this broker's
grant, rather than every module implemented by the host. All grant fields preserve
the released MCP names.

Dispatch supplies `bridgeRequestJson`, a Node Bridge request with `id`, `module`,
`method`, an `args` array, optional `timeoutMs`, and a `permissions` array. Omitted
`args` and `permissions` default to empty arrays. Capability requests are inline
only: incoming descriptors are closed and rejected. Requests exceeding the grant
are denied before dispatch. The response supplies `bridgeResponseJson` and
`bridgeResponseOk`; the JSON is `{id, ok: true, result}` or
`{id, ok: false, error}`. An error contains `name`, `message`, `code`, `category`
and optional method/capability details. `bridgeErrorMessage` mirrors its message.
The ten stable error categories in `HostCapabilityContract.ERROR_CATEGORIES` are
`unavailable`, `permission-denied`, `capability-denied`, `provider-failed`,
`invalid-request`, `resource-limit`, `rate-limited`, `timeout`, `process-dead`
and `runtime-error`. Agent control-plane error names are a separate vocabulary.

Descriptor responses retain the small success envelope in `bridgeResponseJson`
and add `bridgePayloadFd`, `bridgePayloadBytes` and `bridgePayloadMime`. A one-shot
host-cache file is delivered as its raw bytes, with `result.payload.kind` changed
to `descriptor` and its host path removed. A JSON result larger than 64 KiB is
also moved to a descriptor: its MIME type is `application/json`, and the envelope's
`result` becomes `{payload: {kind: "descriptor", mime: "application/json", bytes,
oneShot: true}}`. The descriptor contains the original result JSON, not another
`{id, ok, result}` envelope. Clients must decode that JSON to restore the result.

Inline JSON has a hard ceiling of 512 KiB, further narrowed by the grant (the
current default request grant is 96 KiB); descriptor payloads are capped at 8 MiB.
The sender closes its local descriptor after Binder transfers it, and the
receiver closes the transferred descriptor after reading or rejecting it. Size
checks apply independently on both sides. An FD is not an unlimited payload path.

`grantMethods`, `grantPermissions`, `grantMaxRequestBytes`,
`grantMaxConcurrentCalls`, `grantAccessibilityQueriesPerSecond`,
`grantDefaultTimeoutMs`, and `grantMaxTimeoutMs` describe enforced limits.
`grantJson` contains `methods`, `permissions`, `maxRequestBytes`,
`maxConcurrentCalls`, `modelCallsPerMinute`, `maxTotalTokens` and
`maxInputBytesPerRequest`. It does not duplicate the plugin's tool-group catalog;
the plugin maps granted methods to its own tools. Model quotas are enforced by the
Agent model broker, not by capability dispatch. Senders may include `reasonJson`
on destruction; current shared teardown uses legacy `errorMessage`/`errorCode`
when present for its pending-call failure message, otherwise a generic message.
Private arguments,
observations, output, paths and error bodies must not enter ordinary logs.
In particular, a plugin request for `toast.showToast` with `log: true` records
only the UTF-8 byte count and duration. The displayed toast is unchanged.
Direct Node.js script calls retain their explicit text-logging behavior; the
restriction applies to the host-owned `backend=plugin` bridge context.

## Plugin tool module

Since AutoJs6 6.8.0 the data plane also carries `plugin_tools` (`list`, `invoke`, `cancel`), which forwards
calls to the tools that plugins describe themselves; see [Plugin tool protocol v1](plugin-tool-protocol-v1.md)
for the provider contract, the argument rules and the result schemas. Its tokens are `plugin_tools` and
`plugin_tools.user_granted`; the second admits the tools of USER_GRANTED providers and is never implied by
the first. A call also needs `screen_capture` for a `"screen"` image argument, `files` for path arguments
and `files.write` for declared outputs. The MCP default grant includes the three methods and both tokens;
`ThreeStoveAgentGrant` includes the three methods and `plugin_tools` only. `NodeCapabilityBrokerPolicy` allows 72 KiB of arguments for
`plugin_tools.invoke` (the contract's 64 KiB of tool arguments plus the name and options) within the
96 KiB request grant. Binary outputs do not return through broker descriptors: the host writes them to the
working-directory paths the request names, and the response lists their paths and sizes. The bridge
answers 750 ms before the request deadline, so it can still tell the provider to cancel.

## Host implementation and compatibility

`HostCapabilityBrokerCore` owns admission, the Node bridge adapter, deadlines,
response encoding, descriptor delivery and teardown. `HostCapabilityBrokerStub`
is the shared AIDL adapter. Agent instances pin the attached plugin UID.
`HostCapabilityGrant` is independent of Android and limits methods, permission
tokens, bytes, concurrency, accessibility request rate and timeout. Agent model
limits share this grant: 30 calls/minute, 1,000,000 tokens/link and 128 KiB/request
by default. Model quota accounting is performed by the Agent model broker.

MCP V1 retains its released `IMcpHostCapabilityBroker` descriptor and transaction
order. `McpHostCapabilityBroker` is a thin adapter to the same core;
`McpCapabilityGrant` remains a source alias. The MCP V1 wire names and limits are
preserved. MCP V2 uses the shared descriptor through `openServerV2`.

## Verification

Contract module tests freeze AIDL order/oneway flags, the unique literal keys,
limit relationships and MCP aliases. Host tests cover grants, forbidden methods,
admission, timeout, destruction, UID rejection and response ownership. MCP V1
and V2 round trips are tested independently; a V2 module build does not imply
that the separately installed MCP plugin has migrated to V2.

See [3-Stove Agent protocol V2](three-stove-agent-protocol-v2.md) for attachment admission and
user entry points, and [Agent script manifest V1](agent-script-manifest-v1.md)
for the registered-script methods. Host entry evidence and remaining independent
conformance-APK checks are recorded in
[the P1.5 report](evidence/ai-agent-p15-20260923.md).
The P7 grant matrix has a dedicated foreign-UID driver in
[`test-apps:three-stove-agent-conformance`](../../test-apps/three-stove-agent-conformance/README.md).
It uses the real host Binder stubs and Android query limiter, with deterministic
provider runners for model quota tests. This suite is separate from the remaining
full Agent attach/run/death scenarios and the plugin-side fake host APK.
