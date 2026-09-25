# AI Provider Protocol V2

This document defines the provider-neutral text/tool-generation Binder contract used
by AutoJs6 and independently released AI provider plugins. The checked-in API,
codecs, lifecycle policies, and conformance tests are normative. This document
explains the invariants those implementations enforce.

The host supports protocol 2.0 through 2.1. Version 2.1 adds explicitly negotiated
image inputs; a 2.0 peer still negotiates 2.0 and retains its text wire encoding.
Superseded AI wire interfaces, schema
names, codecs, and service methods are not accepted and have no compatibility
aliases.

## Modules And Responsibility

| Module | Responsibility |
| --- | --- |
| `plugin-api:protocol-wire-api` | Versioned binary envelopes and strict TaggedWire decoding |
| `plugin-api:ai-common-api` | Protocol versions, provider identity, locality, credential mode, errors, usage, payload references, and shared limits |
| `plugin-api:ai-provider-api` | Text-generation AIDL, target catalog, requests, callbacks, codecs, quotas, and lifecycle policy |
| `test-apps:ai-provider-conformance` | Deterministic provider plus malformed, hostile, timeout, Binder, descriptor, catalog, and terminal-state cases |
| `app/.../core/plugin/ai` | Exact-component discovery, trust pinning, catalog/session transports, target planning, descriptor ownership, and safe diagnostics |

V2.0 accepts text inputs only. V2.1 can accept images as input to text/tool
generation when the host, provider and exact target support `vision`. Image
generation, audio and video remain outside this contract. The maintainer approved
this change to the earlier text-only boundary on 2026-09-25 for Agent P9.2.

## Discovery And Binder Surface

Providers publish an exported service for the exact action
`org.autojs.plugin.AI_PROVIDER` and require
`org.autojs.permission.PLUGIN`. The AIDL package is
`org.autojs.plugin.ai.provider.api`.

`IAiProvider` exposes four operations:

```text
getProviderInfo() -> byte[]
getCapabilities() -> byte[]
listTargets(request, callback)
openSession(request, descriptors, callback) -> IAiSession
```

`IAiTargetListCallback` emits exactly one `onPage` or `onFailed` result for one
request. `IAiCallback` emits session events. `IAiSession` accepts a persistent
session's next request, chunk credits, tool results, cancellation, and close.

All structured values are strict versioned `byte[]` documents. Larger request,
tool, or result bodies use explicitly indexed `ParcelFileDescriptor[]` values.
Generated AIDL stubs in the current toolchain do not prove rejection of trailing
Parcel data, so `GENERATED_AIDL_ENFORCES_NO_TRAILING_DATA` remains `false`.
Envelope, descriptor, caller, lifecycle, and quota validation still fail closed.

## Two Independent Provider Identities

V2 deliberately separates two identity domains:

1. `AiProviderInfo.providerId` identifies the Binder plugin implementation that
   the host discovered, authenticated, pinned, and opened.
2. `AiTargetInfo.providerId` identifies the logical AI service behind one
   target, such as an on-device engine or an OpenAI-compatible service.

They are not required to match. A hybrid plugin can therefore expose one local
target and several remote service targets while retaining one stable Binder
plugin identity.

`AiProviderRequest.providerId` always names the pinned Binder provider.
`AiProviderRequest.targetId` selects the exact catalog target. The provider must
reject a request whose pinned provider ID or target ID does not match the
opened plan. Display names never participate in identity.

## Provider Declaration

`AiProviderInfo` declares:

- stable Binder provider ID and display name;
- implementation version name and code;
- protocol range;
- aggregate locality: `ON_DEVICE`, `REMOTE`, or `HYBRID`;
- credential mode: `NONE` or `PLUGIN_MANAGED`;
- bounded HTTPS origin allowlist;
- supported ABIs and host-version bounds.

An `ON_DEVICE` provider must use credential mode `NONE` and declare no remote
origin. A `REMOTE` or `HYBRID` provider must use `PLUGIN_MANAGED` and declare at
least one canonical HTTPS origin. Every remote target origin must be contained
in this pinned provider-level allowlist.

The provider capability record is an upper bound. A target may publish only a
subset of those capabilities and limits. Target metadata can narrow a provider
declaration but cannot widen it.

## Unified Target Catalog

`listTargets` replaces provider-local model enumeration with one paged catalog
covering every callable destination. The official plugin uses these stable ID
conventions:

- `local:*` for an imported on-device model;
- `profile:*` for an explicitly configured online profile.

The protocol validates the general `namespace:id` shape and does not infer
security properties from the prefix. Locality, credential mode, origins, and
configuration state remain authoritative fields.

`AiTargetListRequest` contains the selected protocol version, a page size from
1 through 100, and an optional continuation token. `AiTargetPage` contains a
stable `catalogGeneration`, at most 100 targets, and an optional next token.

Continuation tokens are opaque, bounded, single-use values. The host scopes
them to the complete pinned provider identity and catalog generation. It allows
at most 100 pages and uses non-evicting per-provider and process-wide ledgers;
replay, cross-provider reuse, generation drift, duplicate target IDs, excessive
pages, callback flooding, and late callbacks fail closed.

Each `AiTargetInfo` contains:

| Field | Meaning |
| --- | --- |
| `targetId` | Stable callable target identity |
| `providerId` | Logical service/engine identity for this target |
| `profileId` | Plugin-private remote-profile identity, absent on on-device targets |
| `modelId` | Exact model selector understood by the target |
| `displayName` | Bounded user-facing label; never an identity key |
| `capabilityIds` | Target-specific subset of provider capabilities |
| `locality` | `ON_DEVICE`, `REMOTE`, or `HYBRID` |
| `credentialMode` | `NONE` or `PLUGIN_MANAGED` |
| `availability` | `available` or `unavailable` |
| `configured` | Whether all plugin-private configuration required by the target exists |
| `maximumContextBytes` | Optional target-specific input ceiling |
| `maximumOutputBytes` | Optional target-specific output ceiling |
| `supportedControls` | Generation controls this target accepts |
| `declaredHttpsOrigins` | Exact remote origins this target may contact |
| `backendProfiles` | Optional CPU/GPU/NPU availability records for local execution |

An unconfigured target cannot be available. An on-device target has no remote
profile, uses `NONE`, and declares no origin. A remote or hybrid target has a
profile ID, uses `PLUGIN_MANAGED`, and declares at least one canonical HTTPS
origin. Backend profiles are present exactly when the target advertises the
`backend-profile` control. JSON Schema control requires the
`structured-json` capability.

Supported target controls are `maximum-output-tokens`, `temperature`, `top-k`,
`top-p`, `response-json-schema`, and `backend-profile`.

## Host Target Planning

The host authenticates and pins the exact component, package UID, complete
signer set, package version/update identity, Binder descriptor, provider
metadata, protocol, and negotiated capabilities before accepting a catalog.

For a selected target it then verifies:

- provider locality covers target locality;
- every target origin belongs to the pinned provider allowlist;
- target capabilities and byte limits do not exceed provider capabilities;
- requested features and controls are supported by the selected target;
- the target is configured and available;
- remote credentials remain plugin-managed;
- the catalog generation and target identity are included in the immutable
  session plan and binding cache key.

The plan may contain the profile ID, model ID, locality, capability IDs, byte
limits, and HTTPS origins. It cannot contain credential bytes, authorization
headers, arbitrary provider configuration, request bodies, filesystem paths,
page tokens, tool arguments, or provider-native objects.

## Credentials And Network Boundary

The host never reads, stores, logs, transports, or caches a plugin credential.
`PLUGIN_MANAGED` means the authenticated plugin process resolves its own
encrypted credential only after the host selects a declared remote target.
Credential bytes and authorization headers never cross Binder.

Remote execution is constrained twice: the provider-level declaration is the
pinned allowlist, and the selected target declares its subset. A target cannot
change origin, provider, profile, model, locality, or credential mode during a
session. There is no automatic local-to-remote or remote-to-local fallback.

## Text Requests

`AiProviderRequest` carries a stable request ID, exact protocol version, pinned
Binder provider ID, exact target ID, ordered messages, generation options, and
optional tool definitions.

The text surface supports:

- `system`, `user`, `assistant`, and `tool` roles;
- inline or descriptor-backed UTF-8 text and JSON payloads;
- one-shot and streaming generation;
- optional temperature, top-K, top-P, and maximum output tokens;
- optional structured JSON response schemas;
- persistent sessions;
- provider-neutral reasoning, tool calls/results, usage, and finish reasons.

Required capability IDs are explicit. The provider cannot emit an event or use
a control outside the intersection of host, provider, target, and request.

## Negotiated 2.1 Image Input

Version and capability negotiation are both required. `getCapabilities` may
append optional tag 18 containing `AiVisionCapabilities`; old decoders ignore
this field. A 2.0 selection removes `vision` from the effective capabilities.
Providers must apply `AiTargetCatalogPolicy.forProtocol` to each target before
replying to `listTargets`, so 2.0 hosts never encounter an unknown capability ID.
Declaring protocol 2.1 alone does not declare vision. Only actually supported
model/profile targets may advertise it; LiteRT is not implicitly visual.

An `AiContentPart` remains a text payload when image metadata is absent. An image
part adds required tag 2 containing `AiImageMetadata(width, height)`. It accepts
only `image/jpeg` or `image/png`, uses a descriptor (no inline/base64 alternative),
has no charset, and requires its encoded byte length and SHA-256. Initial images
may occur only in user messages. `AiToolResult` may append repeated required tag
4 image parts beside its existing text output. Old readers reject these required
fields, rather than silently dropping image content. Existing text codecs and
AIDL transaction order remain unchanged.

Request `vision` in `requiredCapabilityIds` before a native conversation can
accept image tool results, including a conversation with no initial image. Tool
results share one descriptor-index space across their text and image payloads.
The same request/session ID, pending call IDs, round budget, deadline and callback
remain in force. This host does not combine vision with persistent `ai.session`.

| Image limit | Protocol ceiling |
| --- | --- |
| Images per request / tool-result batch | 4 |
| Encoded bytes per image | 4 MiB |
| Encoded bytes per request / batch | 8 MiB |
| Width and height | 1-4096 pixels each |
| Decoded pixel count | 16,777,216 |
| Images across initial request and tool continuations | 16 |
| Encoded bytes across the session | 32 MiB |

The capability intersection can lower per-event limits or accepted MIME types.
Text/context, schemas, image bytes/pixels and descriptor counts have independent
limits; image bytes do not turn into UTF-8 text/context bytes. Every descriptor
must be referenced exactly once. Validate metadata, capability and quotas before
reading; then verify actual length, digest, decoded MIME/dimensions and image
decodability before model use. Bounded sampling avoids allocating a full-size
bitmap merely to validate it. Each receiver owns and closes its transferred FDs
on every exit path. The host uses private temporary files unlinked after opening,
and opens continuation descriptors only when their serialized dispatch starts.
No image bytes, base64, hashes or paths are included in ordinary diagnostics.

## Session And Streaming Lifecycle

`openSession` admits the initial request and callback. A persistent session may
later receive `generateNext`; non-persistent sessions reject it. One session
has at most one active generation at a time.

The callback sequence is governed by one terminal owner:

1. `onStarted` reports the exact request, session, provider, target, first chunk
   sequence, and effective limits.
2. `onChunk` carries a monotonically sequenced text delta and/or reasoning
   delta. Empty chunks are invalid.
3. `onToolCalls` carries a verified tool-call batch and descriptors when tools
   are negotiated.
4. `onUsage` carries a monotonic cumulative usage snapshot.
5. Exactly one of `onCompleted`, `onFailed`, or `onCancelled` terminates the
   generation.

Streaming uses explicit credits. The provider cannot emit more chunks than the
host has granted. The initial allowance is 8 and outstanding credits are capped
at 16. Credits, sequence numbers, callback caller UID, callback envelope size,
descriptor references, aggregate byte count, and aggregate SHA-256 are checked
before data becomes visible to a script.

`onCompleted` always carries an `AiCompletionResult` with the verified complete
output and a valid finish reason. Usage and reasoning metadata are optional,
but when present they must agree with previously accepted events. There is no
payload-free completion path.

Cancellation, failure, timeout, Binder death, and close race through the same
terminal gate. Late events are ignored or treated as protocol violations as
defined by the policy; they never create a second terminal result. Close is
idempotent and waits for already-running callbacks on other threads without
waiting on the callback that initiated close.

## Payload And JSON Rules

Every payload uses exactly one inline byte array or one descriptor index.
Declared length, optional SHA-256, MIME type, and UTF-8 charset are verified.
Descriptor indices must be unique, in range, and consume every supplied
descriptor exactly once.

Every JSON payload is limited to 32 nested containers, 10,000 nodes, and 64 KiB
of decoded UTF-8 per string or object key. Object keys must be unique after
escape decoding. Inline and descriptor-backed JSON use the same materialize,
digest, strict UTF-8, parse, depth, node, string, and duplicate-key path.

Reliable-pipe error state is preserved across asynchronous ownership transfer.
Structural descriptor checks do not substitute for payload materialization.

## Principal Ceilings

- 256 messages and 512 content parts;
- 128 tool definitions and 16 tool rounds;
- 2 MiB total input text;
- 8 MiB non-streaming output and 32 MiB streaming output;
- 48 KiB chunk text and 64 KiB chunk envelope;
- 16 descriptors on request opening and 32 per later session event;
- 8 MiB cumulative tool-call/tool-result data;
- 10 minute maximum request deadline;
- 100 targets per page and 100 target pages.

Negotiated provider and target limits may only reduce these ceilings.

## Errors

Stable protocol errors include invalid request, unsupported protocol or
capability, provider unavailable, target unavailable, permission denied, quota
exceeded, backpressure, timeout, cancellation, protocol violation, provider
failure, and descriptor failure.

Public messages and provider codes are bounded and cannot contain control
characters. Retry disposition is either never or an explicit new request only.
The protocol does not authorize automatic retries or target fallback.

## Trust And Fail-Closed Behavior

The host uses exact-component package discovery and reinspection, verifies UID
ownership and complete signing identity, checks the Binder descriptor before
creating a proxy, and revalidates identity before catalog dispatch, callback
admission, and session opening. Ambiguous providers are rejected.

Metadata, catalog, and session transports use bounded executors, absolute
deadlines, process-wide single-flight gates, death recipients, and idempotent
cleanup. A timed-out operation that has not unwound can process-fuse that exact
component until the late operation exits. A fuse does not pretend to interrupt
Binder; it prevents further trusted work from racing the stuck operation.

Safe diagnostics accept only stable pinned identity and lifecycle enums. They
cannot receive prompts, output, page tokens, provider error text, request
bodies, filesystem paths, headers, or credentials.

## Conformance Requirements

The in-repository fake provider and host tests cover local, remote, hybrid, and
unconfigured targets; logical target-provider identity distinct from Binder
provider identity; origin, locality, capability, and limit escalation;
continuation-token replay and generation drift; malformed envelopes; wrong UID
or descriptor; callback flooding; PFD ownership; usage monotonicity; aggregate
digest validation; cancellation and terminal races; and hostile Binder timing.

An implementation is not conformant merely because its happy path generates
text. It must pass the lifecycle, trust, quota, catalog, credential-isolation,
descriptor, and hostile-callback rules as one contract.
