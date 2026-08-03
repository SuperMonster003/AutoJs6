# AI Text Plugin Protocol V1

This document records the frozen provider-neutral text-generation protocol.
The two API AARs and the fake provider are conformance infrastructure. They do
not replace or modify the existing host `ai.ask`, `ai.chat`, or `ai.stream`
implementation in this phase.

The final checked-in API and conformance tests are normative if wording in this
document temporarily lags the current hardening pass.

## Modules And Modality Boundary

| Module | Responsibility |
| --- | --- |
| `plugin-api:protocol-wire-api` | Shared versioned binary envelope |
| `plugin-api:ai-common-api` | Provider identity, errors, usage, payload and FD references |
| `plugin-api:ai-text-generation-api` | Text AIDL, models, codecs, limits, and lifecycle policies |
| `plugins:ai-text-provider-fake` | Deterministic provider and hostile conformance scenarios |
| `app/.../core/plugin/ai` | Default-off, transport-independent host selection and session policies |

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
not replace this materialization step. A future Android adapter must use the
request-aware verified tool-call path and must materialize every request and
tool-result descriptor before exposing its content to an engine or script.

Usage callbacks are cumulative snapshots. Omitted counters retain their last
reported values, and reported counters never decrease. When total, input, and
output counters are all known, total must be at least input plus output.
Reasoning and cached-input counters are details already included in output or
input and are not added again. The host never silently derives or raises a
provider-reported total to repair an inconsistent snapshot.

`supportedAbis` is empty for a pure Java or Kotlin provider. A future local
model provider with native libraries reports only its actual packaged ABIs.

## Current Host Policy Layer

The repository now contains a pure Kotlin host policy layer, but its entry
option defaults to `enabled = false`. It consumes discovery candidates supplied
by a future Android adapter and performs no `PackageManager` query or Binder
operation itself.

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
  negotiated protocol, and negotiated capabilities. The same values must pass
  revalidation immediately before dispatch, and callbacks must come from the
  pinned package UID.
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

This policy layer is not an active selector. There is still no Android service
discovery adapter, Binder binding, FD transport adapter, UI, script-runtime
entry point, network request, or built-in-provider migration.

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

The fake provider exposes deterministic public models for echo, streaming,
reasoning, and tools. Reserved hidden model IDs select hostile conformance
behavior without adding a public `testMode` field to the protocol.

## Credentials, Privacy, And Retry

V1 allows `NONE` and `PLUGIN_MANAGED` credential modes. Credentials remain in
the owning component and never cross the protocol. The fake provider is local,
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

This phase has a default-off, pure host selection policy but no Android
discovery, Binder binding, UI, or runtime integration. Installing or removing
the fake provider therefore adds no user-facing AI capability and cannot alter
the existing built-in providers. The fake APK is test-only, has no launcher or
network permission, and is intended for explicit conformance environments.

A future Android integration built on the policy must preserve the built-in
implementation as the baseline, independently obtain and revalidate every
package fact, own every FD transaction, enforce the lifecycle policies in the
host, and expose selection explicitly. No current result compatibility claim
is made for proprietary `message` or `raw` fields.
