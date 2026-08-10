# AI Text Plugin Protocol V1

This document records the frozen provider-neutral text-generation protocol.
The two API AARs and the in-repository conformance test app are contract and
hostile-test infrastructure. The first production on-device provider lives in
the independent sibling project `AutoJs6-Plugin-AI-Text-Generation`. Neither
the SDK nor the test app replaces or modifies the existing host `ai.ask`,
`ai.chat`, or `ai.stream` implementation in this phase.

The final checked-in API and conformance tests are normative if wording in this
document temporarily lags the current hardening pass.

## Modules And Modality Boundary

| Module | Responsibility |
| --- | --- |
| `plugin-api:protocol-wire-api` | Shared versioned binary envelope |
| `plugin-api:ai-common-api` | Provider identity, errors, usage, payload and FD references |
| `plugin-api:ai-text-generation-api` | Text AIDL, models, codecs, limits, and lifecycle policies |
| `test-apps:ai-text-provider-conformance` | Deterministic provider and hostile conformance scenarios |
| `app/.../core/plugin/ai` | Default-off Android package discovery, metadata-only Binder handshake, exact-component model-list coordination/transport, transcript validation, and host policies |

V1 is text generation only. Image, audio, and video remain separate future
protocols and separate plugins. The protocol does not create a mixed-media or
general AI transport.

## Scope

The protocol supports:

- Provider information and version-range negotiation.
- Paged model discovery with provider-scoped, single-use page tokens.
- One-shot and streaming text generation.
- Plain UTF-8 text and structured JSON output.
- Bounded provider-neutral reasoning summaries and token counts.
- Provider-neutral usage accounting.
- Tool definitions, tool calls, and exactly-once tool-result submission.
- Inline bounded content or declared descriptor references for larger data.

The public result intentionally excludes provider-native `raw` responses,
opaque reasoning signatures, proprietary message objects, credential values,
HTTP configuration, arbitrary headers, filesystem paths, raw URI strings, and
host runtime objects.

## Binder Surface

`IAiTextProvider` exposes provider info, capabilities, paged model listing, and
session opening. An `IAiTextSession` accepts bounded chunk credits, tool
results, cancellation, and close. `IAiTextCallback` reports start, chunks, tool
calls, usage, and exactly one terminal event.

All structured values are `byte[]` documents encoded with
[Plugin Protocol Wire V1](plugin-protocol-wire-v1.md). Large request, tool, or
result data uses explicitly declared `ParcelFileDescriptor[]` arguments.

Current generated AIDL stubs do not prove rejection of trailing Parcel data.
`GENERATED_AIDL_ENFORCES_NO_TRAILING_DATA` is therefore fixed to `false`. This
does not relax envelope, descriptor, UID, signature, lifecycle, or quota
validation.

## Capabilities And Limits

The host and provider intersect capabilities before a request is dispatched.
Unsupported features are disabled and their dependent numeric limits become
zero. A request must explicitly name every required capability and must fit the
resulting intersection.

Request options are also authoritative callback boundaries. A provider cannot
emit chunks for a non-streaming request, emit reasoning when reasoning was not
requested, or use a capability that was removed by the host/provider
intersection. Such an event is a protocol violation rather than an implicit
capability upgrade.

The protocol ceilings include 256 messages, 512 content parts, 128 tools, 16
tool rounds, 2 MiB input text, 32 MiB streaming output, 16 request descriptors,
32 session descriptors, and a ten minute deadline. Smaller message-specific
and provider limits apply. Descriptor references must be unique, in range, and
collectively consume every supplied descriptor exactly once.

The request descriptor limit applies to the request-opening Binder event. The
session descriptor limit applies independently to each later session method or
callback event and bounds the descriptors concurrently open for that event. It
is not a cumulative lifetime counter: after an event's descriptors close, later
tool rounds may reuse the capacity. The separate 8 MiB tool-data byte quota is
cumulative across all tool calls and results in the session.

Every JSON payload is limited to 32 nested containers, 10,000 nodes, and 64
KiB of decoded UTF-8 per string or object key. Each JSON value, including a
scalar or container, is one node; each object key is one additional node.
JSON numbers and escapes use only the ASCII characters admitted by the JSON
grammar. Object keys must be unique after JSON escape decoding, so spellings
such as `"name"` and `"\u006eame"` cannot alias the same key.

Inline and descriptor-backed JSON follow the same validation path. The receiver
materializes exactly the declared bytes within the negotiated bound, verifies
length and optional SHA-256, decodes strict UTF-8, parses one complete JSON
value, and applies the depth, node, string, and duplicate-key rules. MIME type,
descriptor index, and declared length alone do not establish valid JSON.
Quota and descriptor-reference helpers that are documented as structural do
not replace this materialization step. A future Android session/FD adapter must
use the request-aware verified tool-call path and must materialize every request
and tool-result descriptor before exposing its content to an engine or script.

Usage callbacks are cumulative snapshots. Omitted counters retain their last
reported values, and reported counters never decrease. When total, input, and
output counters are all known, total must be at least input plus output.
Reasoning and cached-input counters are details already included in output or
input and are not added again. The host never silently derives or raises a
provider-reported total to repair an inconsistent snapshot.

`supportedAbis` is empty for a pure Java or Kotlin provider. A future local
model provider with native libraries reports only its actual packaged ABIs.

## Current Host Discovery, Handshake, Model-List, And Policy Baseline

The repository now contains a default-off host baseline with five deliberately
separate layers. The first, `AndroidAiTextProviderDiscovery`, is a read-only,
`PackageManager`-only Android adapter with no production call site. When called
directly, it can discover services for the exact protocol action and reinspect
one exact component. Query and package-identity failures are captured as
discovery outcomes rather than causing a bind. Its local snapshots preserve the
raw application and service enabled flags, export status, binding permission,
UID, version code, last-update time, complete current signer set, and
same-signer, official-signer, and debuggable evidence. Debuggable status is
evidence only and never grants trust by itself. The added package-visibility
intent query is scoped to services declaring that action; it neither registers
a host component nor activates discovery.

The discovery adapter itself does not read `PluginEnableStore`, bind a service,
inspect remote provider metadata, construct an `AiTextProviderCandidate`,
invoke provider selection, or dispatch a request. The second layer combines
`AiTextProviderHandshakeCoordinator` with
`AndroidAiTextProviderHandshake`. Its entry option defaults to
`enabled = false`, and it also has no production call site. An explicitly
enabled attempt reinspects every exact package-identity boundary it reaches.
The successful path reaches at most three such boundaries: before binding,
after the exact component connects, and after both metadata reads. The Android
transport binds with an explicit component-only intent and verifies the
`IAiTextProvider` interface descriptor before exposing a metadata-only proxy.

That proxy exposes only `getProviderInfo()` and `getCapabilities()`. Each value
must be present, fit the Binder-envelope limit, and pass strict wire decoding
before the third reinspection and provider selection. The handshake does not
list models, open a session, transfer a `ParcelFileDescriptor`, register an
`IAiTextCallback`, grant credits, or carry prompt, tool, or generated content.

One absolute deadline covers the complete attempt. When it expires, the
attempt fails, its binding closes, and late results are ignored. The exact
component is fused for the rest of the process only when the deadline expires
while a synchronous Binder call for the interface descriptor,
`getProviderInfo()`, or `getCapabilities()` is still executing. Strict
synchronous decoding after either getter is outside this RPC-in-flight region
and does not fuse the component. A timeout in the worker queue, during binding,
or during the final identity reinspection likewise fails the attempt and
ignores late results without fusing the component. Even when applied, the fuse
is not a hard interruption of a synchronous Binder transaction that is already
blocked: the process-wide handshake lease remains held until that blocking job
actually returns.

The third layer is the pure Kotlin host policy. Its standalone entry option
continues to default to `enabled = false`; an explicitly enabled handshake
invokes it only after the final identity reinspection. The policy continues to
own all trust, protocol, capability, and ambiguity decisions.

When explicitly exercised by tests, the policy:

- Checks the service action, export and enable state, binding permission,
  package UID, package version, last-update time, and the complete normalized
  signer set.
- Applies official, same-signer, explicitly user-approved, or debug-only trust
  rules as appropriate to the build and configuration.
- Intersects protocol and text capabilities, checks host version and ABI, and
  rejects multiple eligible providers as ambiguous unless one exact component
  was selected. Duplicate eligible rows for that same component remain
  ambiguous and are rejected.
- Pins the exact component and package identity together with provider metadata,
  negotiated protocol, and negotiated capabilities. The model-list coordinator
  uses this revalidation decision before it lists models; a future session path
  must repeat the same boundary immediately before session dispatch. Callbacks
  must come from the pinned package UID.
- Builds a credential-free session plan. Its binding-decision key includes the
  pinned identity, `listingGeneration`, model ID, model capability snapshot,
  effective context/output limits, and negotiated capabilities. Collection
  domains and counts are encoded explicitly so two different capability
  partitions cannot share a key. This key is not a generated-response cache key
  and accepts no prompt or credential input.
- Formats diagnostics only from stable IDs and bounded event enums. Request
  bodies, prompt text, tool data, page tokens, provider error text, exception
  messages, and credential values are not accepted by the formatter.
- Gives payload-bearing public models summary-only `toString` output. Generated
  text, reasoning, tool payloads and descriptions, opaque page tokens, provider
  error text, payload digests, and inline bytes are omitted.
- Serializes dispatch, remote attachment, cancellation, close, data admission,
  and terminal ownership so cancellation/completion races have one winner and
  deferred remote controls are claimed at most once.

The fourth layer, `AiTextProviderModelListingPolicy`, is default-off,
transport-independent, and has no production call site. It accepts an already
pinned provider identity and returns defensively copied encoded model-list
request envelopes for a caller-owned transport; it neither binds nor registers
`IAiModelListCallback`. A future transport must supply the calling UID captured
synchronously at callback entry. The policy checks that UID against the pinned
provider before decoding, strictly bounds and decodes model pages and provider
errors, applies `AiModelPaginationPolicy` across one transcript, rejects stale
or duplicate callbacks, listing-generation drift, token replay or cycles,
duplicate model IDs, and model capabilities outside the pinned negotiation,
and gives callback, cancellation, timeout, and binder-death terminal races
exactly one winner. A completed transcript publishes an immutable
provider-pinned catalog. `planExact()` selects one exact model ID and returns an
immutable `AiTextHostSessionPlan` only when that model satisfies the requested
capability requirements.

The listing policy's internal `Dispatch` decision identifies only the next
encoded model-list request for a caller-owned transport; it is not session
dispatch.

The fifth layer combines `AiTextProviderModelListingCoordinator` with
`AndroidAiTextProviderModelListing`. Its entry option defaults to
`enabled = false`, and it has no production call site. An explicitly invoked
run accepts an already pinned provider; it does not select or enable one. The
Android transport requires a background thread, binds with an actionless intent
to the exact component, verifies the `IAiTextProvider` descriptor before
constructing the proxy, links Binder death, and keeps that one binding alive for
the complete transcript. The coordinator uses the same descriptor-verified
Binder for bounded provider-info and capability revalidation and for every
`listModels()` call. It performs exact package-identity reinspection before the
bind, immediately before each initial or continuation dispatch, and again
before admitting each callback to the transcript policy.

The `IAiModelListCallback` stub captures `Binder.getCallingUid()` and forwards
the original envelope synchronously on that Binder callback stack. Before the
callback returns, the coordinator checks the UID and message-specific envelope
ceiling, reserves one slot in its bounded callback queue, and only then makes
the defensive copy retained for later admission. A callback delivered
synchronously by `listModels()` is not admitted unless that dispatch itself
returns successfully. Callback registrations detach on close even if the
provider retains their Binder objects.

One process-owned coordinator gate permits a single active transcript. Its
continuation-token replay ledgers are isolated by the full pinned transport
identity and bounded without eviction; exhausting the provider-identity or
per-provider token budget, or the aggregate process token-entry budget fails
closed. One absolute deadline covers binding,
metadata, dispatch, reinspection, and callback admission. Cancellation,
timeout, Binder death, callback, and provider-error races publish one terminal
outcome. If a terminal path wins while an operation remains blocked, the gate
and deadline watchdog stay owned until that operation unwinds; deadline expiry
then fuses the exact component for the rest of the process. Binding, death
recipient, prepared calls, callback sinks, and the process gate close at most
once.

These five layers are not an active host route. The discovery, metadata
handshake, model-list coordinator, Android model-list transport, and transcript
policy all remain default-off or otherwise unreachable from production. There
is still no `openSession` or session dispatch, request/result FD transport,
`IAiTextCallback`, credit flow, UI, script-runtime entry point, network request,
or built-in-provider migration. In particular, there is no route from
`ai.ask`, `ai.chat`, `ai.stream`, or any other `ai.*` API to this baseline.

## Streaming And Backpressure

The host grants an initial bounded credit window. Each chunk consumes one
credit. The provider must stop emitting chunks at zero credits and may resume
only after `grantCredits`. Credits cannot be negative, zero, or accumulated
beyond the negotiated maximum.

The first sequence is declared by `onStarted`; each following chunk advances
by exactly one. A gap, replay, chunk before start, chunk without credit, or
event after a terminal result is a protocol violation. Terminal callbacks are
not blocked by chunk credits.

The terminal output is the complete aggregate visible text. When chunks were
emitted, its declared length and SHA-256 must exactly match the concatenated
text deltas. Aggregate text is charged once rather than once as chunks and
again as the terminal payload. Streamed reasoning deltas and a terminal
reasoning summary are separate generated data and are each charged to the same
negotiated output budget. Completion validation is attempted at most once and
runs descriptor reads outside the lifecycle lock; cancellation, failure, or
close may win while a reader is active, but a failed validation cannot reopen
data admission.

## Tool Turns

Tool calls contain only stable call IDs, names, and bounded JSON arguments.
They authorize no host action. The host decides whether and how to execute a
tool, then submits a result for each outstanding call exactly once.

The session rejects unknown, duplicate, missing, or replayed call IDs,
descriptor aliasing, excessive tool rounds, and cumulative tool data beyond
the negotiated limit. Provider-native continuation state never crosses this
public boundary.

Every provider-emitted tool name must appear in the tool definitions of the
original request. A syntactically valid stable name is not sufficient. Tool
arguments, including descriptor-backed arguments, must be fully materialized
and pass the bounded JSON entity validation before any script tool can be
considered for execution.

The request-aware tool-call validator returns an immutable verified snapshot.
The older three-argument overload performs structural descriptor accounting
only and is deprecated. Likewise, the no-result lifecycle completion method is
a deprecated structural compatibility path and rejects every session that was
constructed with an expected request.

## Model Pagination

Page tokens are opaque to the host and scoped to package, signer, provider,
protocol, listing generation, and the requested page size. Each page must not
exceed that scoped size. A token is single use. The host
rejects replay, cycles, identity drift, generation changes, duplicate model
IDs within a page, and model IDs repeated across pages.

The current default-off transcript policy enforces the page-size, token,
generation, duplicate-model, pinned-UID, envelope, capability, and
single-terminal portions of this boundary. When the explicitly invoked
coordinator is enabled, the Android transport now binds the exact component,
registers one callback per dispatch, invokes `listModels()`, and brackets each
dispatch and callback admission with exact package reinspection. No production
call site enables that path.

The conformance provider exposes deterministic public models for echo, streaming,
reasoning, and tools. Reserved hidden model IDs select hostile conformance
behavior without adding a public `testMode` field to the protocol.

## Credentials, Privacy, And Retry

V1 allows `NONE` and `PLUGIN_MANAGED` credential modes. Credentials remain in
the owning component and never cross the protocol. The conformance provider is local,
uses `NONE`, has no network permission, and never logs prompts, tool arguments,
tool results, keys, or provider-native payloads.

The current host session plan represents this boundary only as `NONE` or
`PROVIDER_PROCESS_ONLY`. It has no field for a key, token, cookie, authorization
header, endpoint credential, or arbitrary provider configuration.

After session dispatch, the host does not retry automatically. A provider may
already have billed a request or advanced opaque state. A retry is a new,
explicit request with a new identity. A local provider never silently falls
back to a cloud provider, changes models, or changes credential mode.

## Failure And Degradation

This phase has default-off, read-only Android package discovery and exact
component reinspection, an explicit-component metadata-only Binder handshake,
pure host selection policies, and an exact-component Android model-list
coordinator/transport. It still has no production discovery, handshake, or
model-list call site, session or FD transport, `IAiTextCallback`, UI, or runtime
integration. Installing either the independent production provider or the
in-repository conformance provider therefore does not switch the public AI
module or alter its built-in providers. The conformance APK is test-only, has
no launcher or network permission, and is intended only for explicit
conformance environments.

A future production integration built on these default-off layers must preserve
the built-in implementation as the baseline, independently obtain and
revalidate every package fact, own every FD transaction, enforce the lifecycle
policies in the host, and expose selection explicitly. No current result
compatibility claim is made for proprietary
`message` or `raw` fields.
