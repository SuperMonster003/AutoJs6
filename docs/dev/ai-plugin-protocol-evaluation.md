# AI Plugin Protocol Pack Evaluation

This note evaluates a provider-neutral AI plugin protocol for AutoJs6. It
defines a deliberately narrow build-time protocol pack and its runtime Binder
boundary. It does not extract the current host AI implementation and does not
authorize a mixed-modality AI plugin.

## Decision

Proceed with a limited GO for a build-time AI protocol pack and a dedicated
conformance test app.

Implementation status on 2026-08-09: Phase 1 is implemented as a
provider-neutral text protocol pack and an isolated conformance test app under
`test-apps`. Phase 3 now has a real CPU-only on-device provider in the
independent sibling project `AutoJs6-Plugin-AI-Text-Generation`. A non-invasive
slice of Phase 2 exists as a default-off, pure Kotlin host policy for selection,
trust pinning, capability matching, safe session planning, and race ownership.
The protocol layer also binds tool calls to the original request, verifies
descriptor-backed tool JSON, enforces aggregate streaming digests and quotas,
rejects decoded duplicate JSON keys, and redacts payload-bearing model
summaries. Android discovery, Binder binding, UI, and script-runtime selection
remain open, so installing either app alone has no effect on the public AI
module. Repository and Android runtime gates are tracked in
[the conformance matrix](compiler-ai-protocol-conformance.md), and the frozen
contract is documented in
[AI Text Plugin Protocol V1](ai-text-plugin-protocol-v1.md).

V1 supports provider-neutral text generation only: one-shot chat, chat over a
supplied message history, streaming text, reasoning metadata, tool-call
descriptions, tool results, usage, cancellation, and capability discovery. One
session represents one generation request plus bounded tool rounds; v1 does not
publish a reusable conversation handle across separate script calls. The
current host implementation remains available and continues to back the
existing `ai.ask`, `ai.chat`, and `ai.stream` surfaces.

This decision is a NO-GO for immediate extraction of the current host HTTP AI
stack and a NO-GO for one monolithic AI plugin. Image, audio, and video are
separate future protocol families and must be delivered as three independent
plugins. A provider supporting several modalities does not relax that boundary.

## Current Repository Evidence

The host AI implementation already contains security-sensitive behavior that
would not become simpler merely by moving it across Binder:

- `app/src/main/java/org/autojs/autojs/core/ai/AiProviderDefinition.kt` defines
  six built-in providers covering OpenAI, Anthropic, Gemini, DeepSeek,
  OpenRouter, and generic OpenAI-compatible endpoints.
- `app/src/main/java/org/autojs/autojs/core/ai/AiProviderVault.kt` stores each
  provider profile and API key as one authenticated encrypted entry through
  the Android Keystore backed AES-GCM implementation in
  `app/src/main/java/org/autojs/autojs/core/security/AndroidKeyStoreSecretCipher.kt`.
- Credential-bearing HTTP clients disable redirects and automatic retries and
  constrain proxy behavior in
  `app/src/main/java/org/autojs/autojs/runtime/api/ai/AiHttpClientFactory.kt`.
- The adapters under `app/src/main/java/org/autojs/autojs/core/ai/transport/`
  normalize OpenAI-compatible, Anthropic Messages, and Gemini Generate Content
  request and response shapes.
- `app/src/main/java/org/autojs/autojs/runtime/api/ai/AiService.kt` and the
  transport package handle SSE quotas, streaming accumulation, cancellation,
  provider-neutral usage, reasoning fields, and tool-call round trips.
- `app/src/main/java/org/autojs/autojs/runtime/api/augment/ai/Ai.kt` exposes the
  existing `ai.ask`, `ai.chat`, and `ai.stream` script facade.
- `app/src/main/java/org/autojs/autojs/core/plugin/ai/` contains the default-off
  transport-independent selection and session policies. It does not query
  `PackageManager`, bind a service, read a credential, send a request, or
  connect to the script facade.
- Current `ai.chat` results include provider-native `message` and `raw` fields;
  transport tests prove that opaque reasoning/thinking signatures survive a
  later same-provider tool round trip. A provider-neutral protocol cannot claim
  full script compatibility until this behavior has an explicit mapping.
- OkHttp 4.12.0 and Gson 2.13.2 are shared host dependencies rather than
  AI-only payload.

Extracting that code now offers little dependable host-size benefit and would
add credential, endpoint, retry, and compatibility risk. The stronger near-term
benefit is extensibility: third parties can add a provider or a local text model
without changing the stable script API.

## Meaning Of Protocol Pack

The protocol pack is a set of small Android Library artifacts used at build
time by the host and provider plugins. It is not a runtime plugin APK and does
not contain an AI engine.

The initial artifact split is:

```text
ai-common-api
  protocol versions, stable IDs, capabilities, errors, usage, finish reasons,
  locality, credential mode, modality, bounded metadata, and PFD references

ai-text-generation-api
  messages, chat requests, stream chunks, reasoning metadata, tools, sessions,
  callbacks, cancellation, and flow control

ai-text-generation-conformance
  conformance provider, hostile fixtures, host harness, and compatibility corpus
```

The API artifacts must not depend on Gson, OkHttp, Retrofit, Bitmap, OpenCV,
Media3, a provider SDK, a model runtime, or native libraries. Parcelable and
AIDL types are the wire contract; provider JSON never crosses the public
boundary. Binary compatibility checks and a generated public API signature are
release gates for every protocol artifact.

Every wire Parcelable is size-prefixed and field-tagged, with an explicit
schema version and only primitives, strings, protocol parcelables, and declared
PFDs. Ordinary positional `Parcelable`, `Bundle`, Java serialization, arbitrary
class names, and undeclared Binder objects are forbidden. This encoding is what
makes an unknown optional field skippable; a Kotlin `@Parcelize` class alone
does not establish that compatibility property.

Future packs remain separate:

```text
ai-embedding-api
ai-image-api
ai-audio-api
ai-video-api
```

`ai-image-api`, `ai-audio-api`, and `ai-video-api` are independent artifacts and
must not be merged into a multimedia or multimodal catch-all API. Their plugin
APKs are also independent. OCR, barcode recognition, OpenCV processing, and
image quantization remain their own domains rather than generic AI operations.

## V1 Scope

V1 includes:

- Text input and text output only.
- One-shot generation over a single message or a supplied multi-turn history.
- Incremental streaming with explicit backpressure.
- Provider-neutral roles, content parts, finish reasons, usage, and bounded
  reasoning metadata.
- Tool definitions, plugin-produced tool-call descriptions, host-produced tool
  results, and bounded multi-round continuation.
- Provider and model capability discovery with paged model listing.
- Cancellation, timeout, Binder-death cleanup, and exactly one terminal event.
- Local and remote provider declarations with explicit credential modes.
- PFD references for oversized text, schemas, and tool data.

V1 explicitly excludes:

- Image, audio, or video input or output.
- Embeddings, reranking, OCR, barcode recognition, speech recognition, speech
  synthesis, image editing, and media generation.
- One plugin that proxies OpenAI, Anthropic, Gemini, DeepSeek, OpenRouter, local
  LLMs, OCR, vision, and media models together.
- Direct access to `AiProviderVault` or any host-managed credential.
- Direct execution of AutoJs6 tools, scripts, intents, shell commands, files, or
  accessibility actions by the plugin.
- Automatic cloud fallback, arbitrary HTTP proxying, prompt replay, or deferred
  offline requests.
- A v1 model downloader, model marketplace, or shared model-storage manager.
- Immediate migration or removal of built-in host providers.

## Proposed Text Protocol

A conceptual v1 Binder surface is:

```text
IAiTextProvider
  getInfo()
  getCapabilities()
  listModels(pageToken, callback)
  openSession(request, callback) -> IAiTextSession

IAiTextSession
  grantCredits(count)
  submitToolResults(results)
  cancel()
  close()

IAiTextCallback
  onStarted(metadata)
  onChunk(sequence, chunk)
  onToolCalls(calls)
  onUsage(usage)
  onCompleted(result)
  onFailed(error)
  onCancelled()
```

The interface names describe responsibilities rather than freezing exact AIDL
syntax. The conformance fixture must validate these semantics before the AAR is
treated as stable:

- Exactly one terminal callback is allowed.
- `cancel()` and `close()` are idempotent.
- Binder death, script-runtime close, host timeout, plugin disable, update, and
  uninstall close the session and every owned descriptor.
- `onStarted` occurs at most once and before any data event. The provider
  serializes callbacks on one logical event lane, and no event is accepted
  after a terminal callback.
- Chunk sequence numbers are strictly increasing and unique.
- At most one tool turn is outstanding. Tool calls pause ordinary completion
  until every accepted call receives one result or the session is cancelled.
- Usage counters are non-negative and monotonic when reported incrementally.
- A provider tool name must occur in the original request tool whitelist.
  Stable-name syntax alone does not authorize a tool call.
- Descriptor-backed JSON is materialized within its declared bound and checked
  for exact length, optional digest, strict UTF-8, one complete JSON entity,
  complexity quotas, and duplicate decoded object keys.
- A non-streaming request rejects chunks, and a request that did not ask for
  reasoning rejects reasoning deltas or terminal reasoning metadata.
- Streamed visible/reasoning bytes and terminal visible/reasoning payloads use
  one cumulative negotiated output budget rather than independent allowances.
- A terminal callback and a bounded error are never blocked by stream credits.
- Provider-specific deltas are merged inside the plugin. Provider-native
  continuation state remains plugin-private for the live session and is erased
  on every terminal path.
- `listModels()` emits exactly one bounded page or one bounded error. Page
  tokens are scoped to package, signer, provider, protocol, and listing
  generation; repeated, expired, or cyclic tokens are rejected.

## Capability And Version Negotiation

Provider capabilities include:

- Protocol minimum and maximum major/minor versions.
- Stable provider ID, display metadata, implementation version, and signer
  identity observed by the host.
- Supported operations, with `TEXT_GENERATION` as the only v1 operation.
- Input and output modalities, both restricted to `TEXT` in v1.
- Data locality: `ON_DEVICE`, `REMOTE`, or `HYBRID`.
- Credential mode: `NONE` or `PLUGIN_MANAGED` in v1.
- Declared remote HTTPS origins for remote and hybrid providers.
- Support flags for streaming, reasoning metadata, tools, structured JSON, and
  usage reporting.
- Maximum messages, parts, tools, context bytes, output bytes, and tool rounds.
- Accepted text and schema MIME types.
- ABI requirements and minimum/maximum supported host versions.

The host intersects requested behavior with declared capabilities before
opening a session. An unknown required field or capability rejects the plugin.
An unknown optional field is ignored. Breaking parcel, lifecycle, flow-control,
credential, or privacy semantics require a new protocol major version.

The current pure host policy accepts candidates from a future discovery
adapter. With its entry option explicitly enabled, it checks the action,
export/enable state, signature-level `org.autojs.permission.PLUGIN` binding
permission, trust authorization, host version, ABI, and protocol/capability
intersection. It rejects multiple eligible providers unless one exact
component was selected; duplicate eligible rows for that same component remain
ambiguous and are rejected. It pins the exact component, package UID, complete
normalized signer set, package version, last-update time, provider metadata,
negotiated protocol, and capabilities, then requires an exact revalidation
immediately before dispatch. A callback UID must equal the pinned package UID.

No Android adapter currently supplies those candidates or performs the bind.
That adapter must independently obtain every package fact from
`PackageManager`; an action string or package name supplied inside a Parcel is
not caller proof. The provider must separately check `Binder.getCallingUid()`
and the packages/signers associated with that UID against its expected
authorized host identity.

A separate pure session gate serializes dispatch start, remote-handle
attachment, cancellation, close, data admission, and terminal ownership.
Cancellation or close requested before attachment is claimed at most once when
the handle arrives, and cancellation/completion races cannot both win. This is
policy coverage only; it is not currently connected to an AIDL session.

A pure Java or Kotlin provider reports `supportedAbis = emptyArray()`. A local
native model plugin declares its actual ABIs and should ship ABI splits plus a
Universal APK when practical.

## Credential And Privacy Boundary

V1 has two credential modes:

### `NONE`

This mode is intended for on-device providers that require no host or remote
credential. The provider declares `ON_DEVICE`. Absence of the Android
`INTERNET` permission is a useful release check for a local plugin, but it is
not treated as proof of all data-flow behavior.

### `PLUGIN_MANAGED`

The plugin owns its credential UI, encrypted storage, endpoint selection, HTTP
stack, request construction, and billing relationship. The host never reads or
writes the plugin credential. The plugin never receives an `AiProviderVault`
entry, host API key, host cookie, host proxy credential, or credential-bearing
request object.

Before first selection of a remote or hybrid plugin, the host presents the
plugin package, signer, provider name, declared endpoint origins, locality, and
an explicit statement that prompt data leaves the device. A signer, origin, or
locality change invalidates prior authorization and requires confirmation.

Endpoint and locality declarations are signed-package claims, not an Android
network sandbox. The host cannot prove or enforce which origins an independent
plugin contacts, and a malicious or compromised plugin can violate its
declaration using its own permissions. Authorization UI and documentation must
state this limitation. Host-side origin enforcement is possible only for a
future host-owned transport with a fixed request policy.

`HOST_TRANSPORT` is deferred. If introduced later, it is limited to an official
or allowlisted protocol in which a plugin returns a bounded provider-neutral
request plan and never receives the key. The host would validate a fixed HTTPS
origin, authentication placement, redirects, cookies, caching, replay, and
response limits. It must never become an arbitrary credentialed HTTP proxy.

Prompts, tool schemas, tool results, reasoning content, provider-native state,
and attached text are sensitive. They are excluded from normal logs, crash
messages, analytics, model-list cache keys, and plugin-discovery diagnostics.

The implemented host session plan is credential-free by construction and can
represent only `NONE` or `PROVIDER_PROCESS_ONLY`. Its SHA-256 binding-decision
key covers the pinned identity, negotiated protocol/capabilities,
`listingGeneration`, model ID, model capability snapshot, and effective
context/output limits. It is not a response cache and has no prompt, tool data,
page token, header, or credential input. The diagnostic formatter accepts only
stable IDs and a bounded event enum; it cannot accept request content, provider
error text, exception messages, or credentials.

## Tool Boundary

The plugin may describe a requested tool call with a stable call ID, tool name,
and bounded provider-neutral arguments. It cannot execute the tool.

The host and script runtime own:

- Mapping a declared tool name to a script-provided implementation.
- Showing or enforcing any user authorization required by the operation.
- Validating tool arguments and result size.
- Executing the tool in the normal script security context.
- Returning a bounded result to the same live session.
- Rejecting duplicate, unknown, expired, or replayed call IDs.

The plugin receives only tool results explicitly submitted for its own active
session. It receives no runtime object, Android `Context`, Binder capability
broker, filesystem path, intent sender, accessibility service, or shell handle.
Provider-native continuation data stays inside the plugin process, is bound to
the package, signer, provider, model, session, and protocol version, and is
erased on every terminal path. It is not transferred to the host, persisted, or
replayed after either process dies.

Host-executed tools are never inferred from a schema. They require an explicit
script implementation and an explicit request option. Without both, tool calls
are returned as descriptions, matching the current host surface, and no
operation is executed. The current JSON-only `ai.chat` input does not yet carry
callable implementations, so automatic tool execution is a Phase 2 API and
compatibility decision rather than an assumed v1 host behavior.

The protocol additionally binds every provider-emitted tool name to the tool
definitions in the original request. Descriptor-backed arguments are read and
validated as bounded JSON entities before a script tool can be considered.
Unknown names and malformed, trailing, over-quota, or duplicate-key JSON are
rejected before execution.

Tool names, call IDs, JSON depth, object members, array elements, string bytes,
and argument/result bytes have independent limits. The host rejects duplicate
names, duplicate or unknown call IDs, non-finite numbers, malformed UTF-8,
excessive nesting, and results submitted after a tool turn or session expires.
These checks happen before a script tool is invoked.

## Streaming And Flow Control

Streaming uses a credit window so a fast local model cannot overflow Binder or
an inactive script callback:

- The host grants 8 initial chunk credits.
- Outstanding credits may not exceed 16.
- One `onChunk` consumes one credit regardless of the text length.
- One chunk is at most 64 KiB including Parcelable overhead.
- The plugin pauses chunk emission at zero credits without blocking
  cancellation, usage, tool calls, or a terminal callback.
- Sequence values begin at a negotiated origin, increase by one, and never
  repeat.
- The host rejects out-of-order chunks, duplicate terminal events, chunks for
  non-streaming requests, or unrequested reasoning. Terminal aggregate text
  must match the streamed text length and SHA-256 and is charged once; streamed
  reasoning and the terminal reasoning summary are each charged to the same
  session quota.

Callback methods are one-way or otherwise return immediately, and the provider
uses one serialized callback lane. Credits bound ordinary data callbacks; they
do not permit concurrent Binder threads to reorder events or bypass totals.

Credits apply to callback delivery, not model token budgeting. Token and byte
limits remain explicit request fields and capability intersections. A plugin
must not retain unbounded generated text while waiting for credits; it pauses
generation where supported or fails with a bounded backpressure error.

Automatic retry is disabled after request dispatch. A provider may have billed
the request, generated a tool call, or advanced opaque state even when the host
did not receive the callback.

## PFD And Data Transport Boundary

Public AIDL never carries a filesystem path, file URI, raw content URI, or
unbounded byte array. The host resolves every URI and transfers an exact,
already-open descriptor.

V1 applies these rules:

- Small text and metadata are inline within the Binder envelope.
- Large message text, tool schemas, tool arguments, and tool results use a
  read-only `ParcelFileDescriptor` reference with MIME type, declared length,
  and optional SHA-256.
- Non-seekable content is copied to a bounded host-private snapshot before
  dispatch when replay or length verification is required.
- A plugin duplicates a descriptor before asynchronous use and closes every
  owned descriptor on all terminal paths.
- Descriptor duplication is for lifetime ownership, not an independent cursor:
  duplicated descriptors share the same open-file position. Once dispatched,
  the host does not concurrently read the handed-off text/schema descriptor,
  and a seekable reader establishes its expected starting offset. A pipe has
  exactly one consumer.
- Only `text/plain` with an explicit charset and `application/json` are
  accepted by the text protocol unless a later compatible extension adds
  another text MIME type.
- Descriptor metadata is not accepted as proof of JSON validity. The receiver
  reads exactly the bounded declared entity, verifies length and optional
  SHA-256, decodes strict UTF-8, rejects trailing JSON and duplicate decoded
  object keys, and applies the same depth/node/string quotas as inline JSON.
- Large plugin-produced text uses a bounded reliable pipe or host-created
  write-only descriptor. The host verifies the byte count and encoding before
  exposing the result to the script and checks reliable-pipe errors after EOF.
- A future persistent artifact uses a host output transaction. The plugin never
  chooses the destination or publishes a partial file.

API 24 through 26 use pipes and PFD snapshots. V1 text generation does not need
`SharedMemory`. A future media protocol may negotiate `SharedMemory` on API 27
and later, but it must retain encoded PFD fallback and remain in the independent
image, audio, or video protocol.

## Proposed V1 Quotas

| Resource | Proposed limit |
| --- | ---: |
| Messages per request | 256 |
| Total content parts | 512 |
| Tool definitions | 128 |
| Tool rounds per session | 16 |
| Binder envelope | 256 KiB |
| Inline text field | 64 KiB |
| Total input text | 2 MiB |
| One inline schema | 64 KiB |
| All tool schemas | 1 MiB total, using PFD above the inline limit |
| One callback chunk | 64 KiB |
| Chunk text payload | 48 KiB of valid UTF-8 |
| Initial/max outstanding chunk credits | 8/16 |
| Tool name/call ID | 128/256 UTF-8 bytes |
| Tool JSON depth/nodes | 32/10,000 |
| One tool JSON string | 64 KiB of valid UTF-8 |
| One tool-call argument or result | 256 KiB, using PFD above the inline limit |
| Total tool arguments and results per session | 8 MiB |
| Outstanding tool calls per turn | 32 |
| Models per page | 100 |
| Model pages per listing | 100 |
| Model page token | 4 KiB |
| Marshaled model page | 128 KiB |
| Open PFDs per request/session | 16/32 |
| Non-streaming output | 8 MiB |
| Total streaming output | 32 MiB |
| Provider error detail | 64 KiB internal, 512 characters visible |
| Session timeout | 1 second minimum, 10 minutes maximum |

The request may choose smaller limits. The effective limit is the smallest of
the host ceiling, provider capability, user request, and script-runtime policy.
Every count and byte total is checked independently by the host and plugin.
String and document ceilings are UTF-8 byte limits, while Binder ceilings are
measured from the marshaled Parcel including field and Parcelable overhead.
Reasoning text and tool traffic count toward their session totals rather than
forming unmetered side channels.

Android's Binder transaction buffer is currently 1 MiB shared by all in-flight
transactions in a process, not a per-call allowance. The smaller protocol
ceilings above preserve headroom but do not eliminate
`TransactionTooLargeException`; either side treats it as an ambiguous partial
failure and terminates the session without retry.

## Fallback And Degradation

The existing built-in providers remain the compatibility baseline. Plugin
selection is designed to be explicit and does not silently replace a built-in
profile. The current policy entry is disabled by default and has no Android or
runtime adapter. Installing, removing, disabling, or omitting the conformance provider
therefore creates no user-visible feature or degradation today.

- Missing, disabled, incompatible, updating, or crashed selected plugins fail
  with `PROVIDER_UNAVAILABLE`.
- A local provider never falls back to a cloud provider automatically.
- A remote provider never changes model, origin, provider, or credential mode
  automatically.
- Offline requests fail immediately. Prompts are not queued for later replay.
- Binder death produces one terminal failure and no credential-bearing or
  billable retry.
- A plugin tool-call failure does not cause the host to execute a different
  tool or switch providers.
- OCR never uploads an image as a fallback, and media plugins never become an
  implicit fallback for text generation.

A user may configure an explicit ordered fallback chain in a later host UI only
when every entry has been selected and preauthorized for its signer, endpoint,
credential mode, and locality. The chain is evaluated before dispatch. Once a
request has been dispatched, automatic fallback remains off unless the protocol
can prove that no provider work began.

## Conformance Matrix

The limited GO advances only after a conformance provider and hostile fixture pass:

- Android API 24, 26, 27, and the current highest supported API.
- Current host/current plugin, current host/previous minor plugin, previous host/
  current minor plugin, incompatible major, and unknown required capability.
- One-shot chat, multi-turn chat, streaming, reasoning metadata, tools, usage,
  cancellation, and model pagination.
- Normal completion, cancel before dispatch, cancel during stream, timeout,
  Binder death, plugin disable, update, uninstall, and host process death.
- Slow callback, zero credits, credit overflow, oversized chunks, reordered
  chunks, duplicate sequence, duplicate terminal, terminal without start, and
  unbounded plugin buffering.
- Bad Parcelable/class-loader data, malformed JSON schema, incorrect MIME,
  length mismatch, truncated pipes, descriptor leaks, and oversized tool data.
- Bundles, Java serialization, arbitrary Parcelable class names, undeclared
  Binder objects, unexpected descriptors, and descriptor-count overflow.
- Unknown tool calls, duplicate call IDs, expired results, excessive rounds,
  call-ID replay across package, signer, provider, model, or session, repeated
  model page tokens, and cyclic model pagination.
- Proof that plugin code cannot enumerate or receive host vault entries and
  that prompts, keys, raw payloads, and provider-native state do not enter host
  logs or protocol objects.
- Current provider-native `message`/`raw` and manual tool round-trip behavior is
  compared with the provider-neutral plugin result. Any incompatibility remains
  an explicit selector blocker rather than being hidden by the unchanged
  `ai.chat` function name.
- Existing built-in AI unit tests continue to pass with the plugin protocol
  present and no plugin installed.

The first real provider should be an on-device text model with credential mode
`NONE`. It exercises process, ABI, cancellation, memory, and streaming
boundaries without introducing remote credential transport at the same time.

## Estimated Benefit And Cost

| Area | Estimate | Basis |
| --- | --- | --- |
| New provider extensibility | High benefit | Provider and local-runtime releases no longer require a host release. |
| On-device text models | High benefit | Native model payloads and ABI splits remain outside the host APK. |
| Host APK size from extracting current HTTP AI | Unquantified, likely low benefit | OkHttp and Gson are shared, and no identical release-build package decomposition has measured an AI-only delta. |
| Credential isolation | Mixed | `PLUGIN_MANAGED` keeps plugin keys outside the host, but each remote plugin becomes its own credential and network trust boundary. |
| Crash and memory isolation | Potential medium benefit | A local model moves work out of the host address space. Android may reclaim its process independently after unbinding, but immediate reclamation is not guaranteed. |
| Protocol maintenance | Medium to high cost | Streaming, tools, version skew, privacy, PFD transport, and provider quirks need a permanent conformance suite. |
| User comprehension | Medium cost | Provider, signer, locality, endpoint, and credential ownership must remain visible and understandable. |

The protocol pack is justified by extensibility, not by an immediate promise of
host size reduction. A migration proposal for an existing built-in provider
requires separate evidence for APK delta, settings migration, vault handling,
fallback behavior, and script compatibility. The shared dependency observation
is directional evidence only, not an APK-size or performance measurement.

## Phased Rollout

### Phase 0: Protocol Documents

- Freeze terminology, ownership, credential modes, flow control, quotas, and
  fallback semantics.
- Keep all existing host behavior unchanged.

### Phase 1: Build-Time Pack And Conformance Test App

- Publish `ai-common-api` and `ai-text-generation-api` as lightweight AARs.
- Add binary API checks, hostile Parcelable fixtures, and a conformance APK.
- Run the full version, lifecycle, flow-control, PFD, and vault-secrecy matrix.

### Phase 2: Optional Host Selector

- Keep the completed pure policy core default-off while Android discovery,
  Binder transport, UI, and runtime adapters are absent.
- Add an experimental provider selector without changing `ai.ask`, `ai.chat`,
  or `ai.stream` script signatures.
- Keep built-in providers as the default and explicit fallback baseline.
- Surface signer, locality, endpoint, credential mode, and degradation reason.
- Do not claim response compatibility until provider-native `message`/`raw`
  round trips and explicit tool execution have a documented mapping. If that
  mapping needs a persisted opaque envelope or callable script objects, it is a
  separate versioned API decision.

### Phase 3: First Real Text Provider

- Validate one on-device text-generation plugin first.
- Record ABI, Universal APK, model storage, peak RSS, cancellation latency,
  throughput, descriptor, and process-death results.
- Revisit remote `PLUGIN_MANAGED` providers only after the local lifecycle is
  stable.

### Later Independent Packs

- Evaluate embeddings as a separate operation and API artifact.
- Evaluate image, audio, and video in three separate stages.
- Publish separate API artifacts and separate plugin APKs for image, audio, and
  video, even when one vendor or model family implements all three.
- Keep OCR, barcode, OpenCV, image quantization, and media playback outside the
  generic text protocol.

No stage authorizes a combined multimedia AI plugin. Any future modality must
define its own content model, PFD and output-transaction policy, quotas,
permissions, privacy notice, fallback behavior, and conformance matrix before
implementation.

## Android Platform References

- [TransactionTooLargeException](https://developer.android.com/reference/android/os/TransactionTooLargeException)
  documents the process-wide shared Binder buffer and ambiguous partial
  failure behavior.
- [ParcelFileDescriptor](https://developer.android.com/reference/android/os/ParcelFileDescriptor)
  documents descriptor ownership, shared-offset `dup()` semantics, and reliable
  pipe error reporting.
- [Binder](https://developer.android.com/reference/android/os/Binder#getCallingUid())
  documents caller UID as the Binder security identity.
- [SharedMemory](https://developer.android.com/reference/android/os/SharedMemory)
  is available from API 27.
