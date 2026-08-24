# AI Provider Protocol V1

This document records the frozen provider-neutral text-generation protocol.
The current unpublished contract accepts protocol 1.2 through 1.3 and
negotiates the highest common minor version. Protocol 1.2 is the minimum
because it adds the persistent conversation lifecycle; protocol 1.3 adds
explicit inference backend profiles and device availability reporting.
The two API AARs and the in-repository conformance test app are contract and
hostile-test infrastructure. The official production local provider lives in
the independent sibling project `AutoJs6-Plugin-Three-Stone-AI`. The SDK
and test app do not replace a built-in provider. A later narrow integration now
adds explicit local-plugin branches to `ai.ask`, `ai.chat`, and `ai.stream`;
when the `plugin` selector is absent, the existing cloud-backed behavior of all
three calls is unchanged.

The final checked-in API and conformance tests are normative if wording in this
document temporarily lags the current hardening pass.

## Modules And Modality Boundary

| Module | Responsibility |
| --- | --- |
| `plugin-api:protocol-wire-api` | Shared versioned binary envelope |
| `plugin-api:ai-common-api` | Provider identity, errors, usage, payload and FD references |
| `plugin-api:ai-provider-api` | Text AIDL, models, codecs, limits, and lifecycle policies |
| `test-apps:ai-provider-conformance` | Deterministic provider and hostile conformance scenarios |
| `app/.../core/plugin/ai` | Default-off Android package discovery, metadata-only Binder handshake, exact-component model-list and session coordination/transports, transcript validation, PFD ownership, and host policies |
| `app/.../runtime/api/ai` | Strict explicit-selector admission and per-script ownership for the narrow production `ai.ask`, `ai.chat`, and `ai.stream` plugin routes |

V1 is text generation only. Image, audio, and video remain separate future
protocols and separate plugins. The protocol does not create a mixed-media or
general AI transport.

## Scope

The protocol supports:

- Provider information and version-range negotiation.
- Paged model discovery with provider-scoped, single-use page tokens.
- One-shot and streaming text generation.
- Optional temperature, top-K, top-P, and output-token generation controls.
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

`IAiProvider` exposes provider info, capabilities, paged model listing, and
session opening. An `IAiSession` accepts bounded chunk credits, tool
results, cancellation, and close. `IAiCallback` reports start, chunks, tool
calls, usage, and exactly one terminal event.

All structured values are `byte[]` documents encoded with
[Plugin Protocol Wire V1](plugin-protocol-wire-v1.md). Large request, tool, or
result data uses explicitly declared `ParcelFileDescriptor[]` arguments.
The protocol owns the `AP` TaggedWire schema domain (`0x4150`) and the Binder
discovery action `org.autojs.plugin.AI_PROVIDER`; its Kotlin and AIDL package is
`org.autojs.plugin.ai.provider.api`. No alternate package, action, descriptor,
or schema-domain alias is part of this unpublished contract.

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

Protocol 1.1 adds nullable `temperature`, `topK`, and `topP` fields to
`AiGenerationOptions`; `maximumOutputTokens` remains the nullable output-token
ceiling. Temperature must be finite and non-negative, top-K must be positive,
top-P must be finite in the inclusive range 0 through 1, and the token ceiling
must be an integer from 1 through 2,147,483,647. A token ceiling is enforced by
the provider and does not require the `usage` capability or a usage callback.

Protocol 1.2 adds explicitly negotiated persistent conversations. Protocol
1.3 adds provider-declared CPU/GPU/NPU backend profiles and their current
availability so the host can pin the selected execution plan before opening a
session.

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
not replace this materialization step. The current default-off Android session
adapter preserves the full callback `ParcelFileDescriptor` object across its
asynchronous ownership-transfer boundary so reliable-pipe error state is not
lost through a data-FD-only `dup()`. Tool-bearing requests still fail closed in
this slice; a future tool-enabled route must use the request-aware verified
tool-call path and materialize every tool-result descriptor before exposing its
content to an engine or script.

Usage callbacks are cumulative snapshots. Omitted counters retain their last
reported values, and reported counters never decrease. When total, input, and
output counters are all known, total must be at least input plus output.
Reasoning and cached-input counters are details already included in output or
input and are not added again. The host never silently derives or raises a
provider-reported total to repair an inconsistent snapshot.

`supportedAbis` is empty for a pure Java or Kotlin provider. A future local
model provider with native libraries reports only its actual packaged ABIs.

## Current Host Discovery, Handshake, Model-List, Android Session, And Policy Baseline

The repository now contains a default-off host baseline with seven deliberately
separate layers. The first, `AndroidAiProviderDiscovery`, is a read-only,
`PackageManager`-only Android adapter. It is not an automatic discovery entry;
the explicit production route runner invokes only exact-component inspection.
When called directly, it can discover services for the exact protocol action
and reinspect one exact component. Query and package-identity failures are
captured as discovery outcomes rather than causing a bind. Its local snapshots preserve the
raw application and service enabled flags, export status, binding permission,
UID, version code, last-update time, complete current signer set, and
same-signer, official-signer, and debuggable evidence. Debuggable status is
evidence only and never grants trust by itself. The added package-visibility
intent query is scoped to services declaring that action; it neither registers
a host component nor activates discovery.

The discovery adapter itself does not read `PluginEnableStore`, bind a service,
inspect remote provider metadata, construct an `AiProviderCandidate`,
invoke provider selection, or dispatch a request. The second layer combines
`AiProviderHandshakeCoordinator` with
`AndroidAiProviderHandshake`. Its entry option defaults to
`enabled = false`; the explicit production ask/chat/stream runner is its only
production owner and enables one exact attempt. Such an attempt reinspects every
exact package-identity boundary it reaches.
The successful path reaches at most three such boundaries: before binding,
after the exact component connects, and after both metadata reads. The Android
transport binds with an explicit component-only intent and verifies the
`IAiProvider` interface descriptor before exposing a metadata-only proxy.

That proxy exposes only `getProviderInfo()` and `getCapabilities()`. Each value
must be present, fit the Binder-envelope limit, and pass strict wire decoding
before the third reinspection and provider selection. The handshake does not
list models, open a session, transfer a `ParcelFileDescriptor`, register an
`IAiCallback`, grant credits, or carry prompt, tool, or generated content.

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
  uses this revalidation decision before it lists models; the session
  coordinator repeats the package and metadata boundary and performs a final
  exact package inspection immediately before `openSession`. Callbacks must come
  from the pinned package UID.
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

The fourth layer, `AiProviderModelListingPolicy`, is default-off and
transport-independent. It has no independent public entry; the explicit
ask/chat/stream runner reaches it only through the model-list coordinator. It
accepts an already pinned provider identity and returns defensively copied
encoded model-list request envelopes for a caller-owned transport; it neither
binds nor registers `IAiModelListCallback`. A future transport must supply the
calling UID captured synchronously at callback entry. The policy checks that
UID against the pinned provider before decoding, strictly bounds and decodes model pages and provider
errors, applies `AiModelPaginationPolicy` across one transcript, rejects stale
or duplicate callbacks, listing-generation drift, token replay or cycles,
duplicate model IDs, and model capabilities outside the pinned negotiation,
and gives callback, cancellation, timeout, and binder-death terminal races
exactly one winner. A completed transcript publishes an immutable
provider-pinned catalog. `planExact()` selects one exact model ID and returns an
immutable `AiProviderHostSessionPlan` only when that model satisfies the requested
capability requirements.

The listing policy's internal `Dispatch` decision identifies only the next
encoded model-list request for a caller-owned transport; it is not session
dispatch.

The fifth layer combines `AiProviderModelListingCoordinator` with
`AndroidAiProviderModelListing`. Its entry option defaults to
`enabled = false`; the explicit ask/chat/stream runner is its only production
owner and enables one exact run. That run accepts an already pinned provider;
it does not select or enable one. The Android transport requires a background
thread, binds with an actionless intent
to the exact component, verifies the `IAiProvider` descriptor before
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

The sixth layer, `AiProviderSessionPolicy`, is a transport-independent pure
Kotlin owner for one session transcript. Its entry option defaults to
`enabled = false` and it exposes no independent public entry. The explicit
ask/chat/stream runner reaches it only through the session coordinator. It
accepts an already pinned `AiProviderHostSessionPlan`, one request, and abstract
descriptor, remote, callback, and executor interfaces. Before producing a
dispatch decision, it checks that the request still matches the pinned plan. At callback ingress, it
validates the exact pinned UID and bounds callback envelopes before any
asynchronous retention or decode. Requests containing tools fail closed in this slice;
tool-call callbacks are rejected without reading their descriptors.

`beginOpen()`, `commitOpenSucceeded()`, and `commitOpenFailed()` explicitly
model the synchronous open transaction. Provider callbacks that arrive before
the transaction returns are provisional: they are bounded and buffered but do
not become listener-visible until a successful commit. An external cancel,
timeout, or death may supersede provisional callback failure before the open is
resolved; absent such an external terminal, a failed open reports
`OPEN_FAILED`. Request descriptors remain policy-owned until either open commit
settles.

The callback queue is bounded. Its minimum capacity is ten entries: one
`Started`, the fixed eight-credit initial chunk window, and one terminal event.
Credits are reserved before chunk ingress, replenished only after successful
delivery, and rolled back fail-closed when a remote grant fails. Once terminal
ingress is claimed, no further credit is replenished. Callback descriptors are
closed exactly once after their bounded ownership-transfer/read reservation
settles, including late and rejected callbacks.

Terminal processing has two stages. Remote cancel/close and late-remote cleanup
run promptly and idempotently on a serialized non-inline terminal lane. Listener
publication waits until the open transaction, request-descriptor close,
callback ownership-transfer/read/cleanup reservations, data delivery, credit
grants, and late
remote closures have all settled. If the primary executor rejects, an
independent fallback lane owns terminal cleanup. If both lanes reject, the
fatal owner-contract path exclusively drains claimable local and remote
resources, does not invoke the listener on the rejecting caller thread, and
throws. Session events and dispatch decisions expose summary-only `toString()`
output rather than prompt, generated text, reasoning, provider error text, or
descriptor content.

The seventh layer combines `AiProviderSessionCoordinator`,
`AndroidAiProviderSession`, the generated `IAiCallback` stub, and the
Android request/callback descriptor owners. It is default-off
(`enabled = false`); the explicit ask/chat/stream runner is its only production
owner and supplies an already pinned exact component, plan, and request. One
process owner permits one active run. One absolute deadline covers the four
exact package inspections, exact actionless bind, provider descriptor check, bounded metadata reads, synchronous
open, callback admission, PFD reads, credit controls, and terminal cleanup.

After connection, provider info and capabilities are read and revalidated from
the same descriptor-verified `IAiProvider` Binder later used to prepare and
invoke `openSession`. The fourth inspection occurs immediately before that one
synchronous open. The returned `IAiSession` descriptor is verified and its
death recipient is linked before the handle is accepted. Callbacks that occur
synchronously before `openSession` returns remain provisional under the session
policy and are published only after the open commits successfully.

At callback entry the Android stub captures `Binder.getCallingUid()`. The policy
checks that UID and the event-specific envelope and descriptor-count bounds
before strict typed decoding. Request PFDs are borrowed only for the synchronous
open and remain host-owned until its success/failure commit. Callback PFDs are
not reduced to data-FD-only duplicates: the receiver transfers each complete
`ParcelFileDescriptor` object exactly once to the asynchronous lease, preserving
the reliable-pipe communication FD used by `closeWithError()`/`checkError()`.
Null array elements close every valid peer and become a no-FD invalid-count
sentinel, so policy rejects them before transfer or decode. Reads require the
exact declared byte count, EOF, and reliable producer-error check; partial ownership
or constructor failure rolls back every transferred PFD.

Eight initial chunk credits are granted only after open commit. One credit is
replenished only after one accepted chunk is delivered. Cancel, timeout, Binder
death, control failure, callback failure, and completion have one terminal
owner; no operation is retried after dispatch. Binding, prepared callback,
request descriptors, callback leases, remote controls, death recipients, and
the process gate settle before the terminal event is published. A deadline
crossed by a blocking Binder/PFD/control operation fuses the exact component and
keeps the process gate busy until that operation unwinds.

The session coordinator can locally pin the `listingGeneration` carried by the
prior `AiProviderHostSessionPlan`, but V1 provider metadata and `openSession` expose
no remote model-catalog generation proof. Re-reading provider info and
capabilities from the same Binder therefore cannot prove that the listed model
or `listingGeneration` is still current. This remains an explicit promotion
gap, not an identity guarantee inferred from package or metadata stability.

The pure session-policy slice in commit `325577c82` retains its standalone
Kotlin K2/JVM 40/40 and focused Gradle 40/0 host-unit evidence. The coordinator,
Android transport, and instrumentation landed in `4f22dc854`, `038728f03`, and
`a22ed8b75`. A focused Gradle run recorded all 18
`AiProviderSessionCoordinatorTest` methods with 0 skipped, 0 failures, and
0 errors and assembled the host, instrumentation, and fake-provider APKs.

The earlier API 31 baseline ran two exact methods: a cross-process
`fake.stream` request-PFD session delivered approximately 18 chunks beyond the
initial credit window of 8, then usage and one off-main-thread completion; the
process-local descriptor-owner method accepted exact input and rejected short,
trailing, and reliable-producer-error inputs while checking idempotent close.

The H1 hostile-provider and instrumentation additions landed in `edd10008f` and
`06ebc788c`. Their focused Gradle evidence recorded coordinator 18/0 and fake
provider unit 31/0, then assembled the host, instrumentation, and fake-provider
APKs. All three APKs verified with Signature Scheme v2 and the same certificate
SHA-256
`2e64822e13a6c80c12e1c4b47e8fb32d1e9334526289da75777b7a79145de4b8`.
Their artifact SHA-256 values were host
`0ec03495cdd096fa812dd64641c07a484bdd2231df777427f55e8dd9a96e2634`,
instrumentation
`68e9a08522c03e2daa2575a0a087403372adf0e6eefee452fcce66263ecdaa59`,
and final fake provider
`8c9c3b3d0eca3e67c41a2c563e975d927e5422018c5a790330c10b164303b521`.

On `QV710AF65F` (Android 12/API 31, arm64-v8a), each of these seven exact
instrumentation methods returned `OK (1 test)`:

| Method | Narrow evidence |
| --- | --- |
| `completionDescriptorIsMaterializedAcrossProcess` | An ordinary-pipe callback PFD was owned and materialized across the process boundary with exact EOF, declared length, SHA-256, UTF-8 output, one descriptor, and terminal cleanup. |
| `toolDescriptorIsOwnedThenRejectedWithoutExecution` | A descriptor-backed tool callback crossed Binder, was owned, rejected as `TOOLS_UNSUPPORTED` without tool execution, and cleaned up. |
| `chunkBeforeStartFailsClosedAfterInitialCreditCommit` | A chunk before `Started`, emitted only after initial host credit, failed closed as `CALLBACK_MALFORMED`. |
| `sequenceGapFailsClosedOnce` | A non-contiguous chunk sequence failed closed as `CALLBACK_MALFORMED`. |
| `invalidCompletionDescriptorReferenceFailsClosedOnce` | A completion referring to a missing descriptor failed closed as `CALLBACK_MALFORMED`. |
| `duplicateTerminalPublishesOnlyOneSettledOutcome` | The observed duplicate terminal produced one settled host outcome within a bounded 250 ms late-callback smoke window. |
| `stalledSessionCanBeCancelledAfterStartAndReleasesOwner` | Host cancel after `Started` was accepted, published `Cancelled`, and released the process owner. |

Every exercised H1 mode waited for initial host credit before acting, so the
callbacks landed after synchronous open commit rather than in the provisional
callback-before-return window. Each method also asserted one published
terminal, off-main-thread delivery, stable package identity, and reusable
process ownership. The host, instrumentation, and fake-provider packages were
absent before installation and absent again after cleanup.

The H2 lifecycle slice landed in production transport commit `e5bd92b16`, fake
fixture commit `10dad3e39`, and host-test commit `0b9a94742`. Once Android has
delivered the exact service connection, `onServiceDisconnected` now follows
the same binder-death path as the death recipient and `onBindingDied`; only a
disconnect before that connection callback retains the typed `DISCONNECTED`
binding failure. The fake provider adds an `isolatedProcess` exact-bound relay
for a real mismatched callback UID and a provider-process-death mode that waits
for initial host credit, emits `Started` and one sequence-0 `hostile` chunk,
waits for the host grant to restore the captured credit window, and only then
kills the provider process.

The focused H2 Gradle XML recorded all 18 coordinator tests and all 31
fake-provider unit tests with 0 skipped, 0 failures, and 0 errors, and the same
run assembled the host, instrumentation, and fake-provider APKs. All three
verified with Signature Scheme v2 and certificate SHA-256
`2e64822e13a6c80c12e1c4b47e8fb32d1e9334526289da75777b7a79145de4b8`.
Their H2 artifact SHA-256 values were host
`0685ce99c0f9e8f9056be5f3a8eebc2c7ea5ffca2d422e21eac69d0cb3364629`,
instrumentation
`7c91a0af651e2098ad124ff8a89ae3ac3018e0f1d0dec068367595e964739178`,
and fake provider
`6c7319a6682b08cab2e3c610d6db0919c7c71bc034c2cec8b46eb23623d55a18`.
Each local hash matched the installed device `base.apk` hash.

On `QV710AF65F` (Android 12/API 31, arm64-v8a), these two separate exact H2
method invocations each returned `OK (1 test)`:

| Method | Time | Narrow evidence |
| --- | ---: | --- |
| `callbackFromIsolatedProviderUidIsRejectedAndReleasesOwner` | 0.852 s | A callback issued through the same conformance APK's exact-bound isolated-process relay arrived under a distinct Android UID, failed closed as `CALLBACK_UID_MISMATCH` before publishing transcript data, produced one terminal, and released the process owner. |
| `providerProcessDeathAfterStartedPublishesOneBinderDiedAndReleasesOwner` | 0.544 s | After `Started`, one sequence-0 `hostile` chunk, and causal host credit replenishment, killing the provider process published one `BinderDied` terminal and released the process owner. |

The host, instrumentation, and fake-provider packages were absent before this
H2 install and absent again after cleanup. This is cleanup evidence; it is not
an update/uninstall-during-session test.

H1 and H2 remain **PARTIAL** Android evidence. The H1 completion case used an
ordinary pipe: it proves cross-process callback-PFD ownership, exact
EOF/length/SHA-256/UTF-8 materialization, and cleanup, but not cross-process
reliable-pipe producer status. `closeWithError()`/`checkError()` remains covered
only by the process-local descriptor test. The tool case proves ownership and
fail-closed cleanup, not tool payload execution or reliable-status propagation.
The duplicate-terminal result is only a 250 ms bounded smoke observation, not
an exhaustive late-callback proof. H2 proves one isolated-callback-UID rejection
and one fake-provider process death on one API 31 device; it is not a broader
identity or lifecycle matrix. Deterministic no-credit behavior remains only in
the existing JVM policy test
`ninthChunkBeforeAnyReplenishmentFailsClosedForNoCredit`. Package last-update
drift and a missing final exact inspection are covered only by coordinator JVM
final-reinspection cases before `openSession`; they do not prove an Android
package update or uninstall during an active session. Provider disable, host
death, other API/ABI, remote `listingGeneration`, and the broader `ai.*` matrix
remain open. Separate real-provider public `ai.ask` and fake-provider public
`ai.stream` and public `ai.chat` smokes with the fake provider are recorded
below; none broadens these hostile-provider cells or closes a broad R1 or
protocol exit gate.

### Explicit Production `ai.ask`, `ai.chat`, And `ai.stream` Routes

The seven layers above now have three deliberately narrow production call sites.
Only an `ai.ask`, `ai.chat`, or `ai.stream` whose options object contains the
`plugin` key enters them. The stable selector shape is:

```javascript
ai.ask(prompt, {
    plugin: {
        component: {
            packageName: "org.example.provider",
            className: "org.example.provider.AiProviderService",
        },
        providerId: "example.provider",
        modelId: "example-model",
    },
    timeout: 120000,
    temperature: 0.7,
    topK: 40,
    topP: 0.9,
    maxTokens: 256,
});

ai.chat(prompt, {
    plugin: {
        component: {
            packageName: "org.example.provider",
            className: "org.example.provider.AiProviderService",
        },
        providerId: "example.provider",
        modelId: "example-model",
    },
    timeout: 120000,
});

const stream = ai.stream(prompt, {
    plugin: {
        component: {
            packageName: "org.example.provider",
            className: "org.example.provider.AiProviderService",
        },
        providerId: "example.provider",
        modelId: "example-model",
    },
    timeout: 120000,
});
```

The exact component package and class, provider ID, and model ID are all
required. Provider and model IDs use the protocol stable-ID grammar; component
strings must be nonblank and contain no ISO control characters. The timeout is
an integer from 1,000 through 600,000 milliseconds, inclusive, and defaults to
120,000 milliseconds. One outer absolute deadline covers discovery through the
active session terminal. Lower coordinators and handles retain their own
settled resource-cleanup ownership after that terminal. The session is not
opened if fewer than 1,000 milliseconds remain.

Presence of the `plugin` key is the fail-closed routing decision, including
when its value is null or malformed. The selector rejects unknown nested or
outer fields other than `timeout`, `temperature`, `topK`, `topP`, and
`maxTokens`; it also rejects any explicit `stream` option and every cloud
control alias for profile, provider, base URL, model, or API key. A null
generation control means unspecified. Numeric controls are parsed without
rounding or string coercion and must satisfy the protocol ranges above. It does
not read the provider vault, construct a cloud request, issue HTTP, retry, or
fall back to a built-in provider. Absence of `plugin` preserves the legacy
cloud path unchanged.

The production 3-Stone AI provider leaves LiteRT-LM's model/engine sampler
defaults untouched when `temperature`, `topK`, and `topP` are all unspecified.
LiteRT-LM requires a complete sampler configuration when any one is overridden,
so a partial override fills omitted controls with the stable baseline
`temperature: 1`, `topK: 1`, and `topP: 0.95`. The provider passes the resolved
triple to `SamplerConfig` and the optional token ceiling to both conversation
configuration and asynchronous message generation.

All three routes accept exactly one message object with only `role: "user"` and
a nonempty string `content`; a prompt string is normalized to that shape by the
existing facade. They send one inline UTF-8 `text/plain` part and zero request
descriptors, with no reasoning, structured JSON, usage, tools, or tool rounds.
`ai.ask` and `ai.chat` are non-streaming and reject chunk or usage callbacks.
Chat completion uses the stable 12-key public result: text, an empty reasoning
string, an empty toolCalls array, null usage/finishReason/message/error/raw/
profile, route `plugin`, and the exact provider/model pins. `ai.stream` requires
the planned model's `STREAMING` capability; it maps `Started` to `open`, each
nonempty text chunk to `chunk` followed by `delta` in one bounded dispatcher
action, and the aggregate completion to `done`.

`AndroidAiPluginAskRunner` lazily creates one process-shared backend. A
process admission gate permits one routed ask, chat, or stream at a time.
Process-lifetime single-thread orchestration, coordinator, terminal-fallback, and deadline
owners share a bounded blocking pool of at most three Binder/package-manager
operations with the existing model-list and session process owners. Every stage
uses an explicitly enabled exact-component attempt, but selection still
requires the pinned provider to be `ON_DEVICE` with credential mode `NONE`.
There is no retry after dispatch. Each script runtime owns its active attempts;
a runtime close requests silent cancellation, closes the handle once,
suppresses late terminals, and leaves process-owned executors and component
fuses alive for the host process lifetime. Public `AiStream.cancel()` propagates
to the same attempt; natural completion closes without cancelling it.

The routes retain their source/JVM gate. The earlier standalone K2/JUnit ask
slice passed 15/15 tests (8 option-parser, 3 input-mapper, and 4 per-script
runtime lifecycle cases). After production commit `2ea3360a2` and test commit
`50b43d00c`, the focused Gradle XML recorded 25/25 tests across
`AiPluginAskInputMapperTest` (3), `AiPluginAskRuntimeTest` (4),
`AiPluginStreamRuntimeTest` (4), `AiServicePluginRouteTest` (5), and
`AiStreamTest` (9), with 0 skipped, 0 failures, and 0 errors. Separately, the
production plugin retained its completed `testDebugUnitTest`, `lintDebug`,
`assembleDebug`, and `assembleRelease` evidence. The chat addition was tested at
source `b0aa6768484d6046551264f69ecc84b527bbb442`, after exact feature/test
commits `d480b6918` / `b0aa67684`. Its focused Gradle XML recorded 14/14 tests:
`AiPluginAskInputMapperTest` (3), `AiPluginAskRuntimeTest` (4), and
`AiServicePluginRouteTest` (7), with 0 skipped, 0 failures, and 0 errors. Main
integrates the production and test patches as commits `2d256b99f` and
`c35f199b8`; those later identifiers provide integration traceability and are
not the tested APK source.

On 2026-08-11, the exact opt-in method
`AiPluginPublicAskSmokeTest#publicRhinoAiAskCompletesThroughExactRealPlugin`
ran on `QV710AF65F` (Android 12/API 31, arm64-v8a). It exercised the real
`ScriptEngineService` and Rhino global `ai.ask`, an explicit selector for
`io.github.supermonster003.autojs6.plugin.threestoneai.provider.AiProviderService`
and provider `autojs6.three-stone-ai`, the release plugin, and a real
2,583,085,056-byte LiteRT-LM model. The model SHA-256 was
`ab7838cdfc8f77e54d8ca45eadceb20452d9f01e4bfade03e5dce27911b27e42`,
and its imported model ID was
`litertlm.ab7838cdfc8f77e54d8ca45eadceb204`. The public Promise settled with a
nonempty result; the complete instrumentation method took 7.071 seconds and
reported `OK (1 test)` with `INSTRUMENTATION_CODE: -1`.

The host App Debug APK SHA-256 was
`b7dab13c33b49f12f45de7a2091fabffa41618c983055fa19083ab1482af9561`,
the instrumentation APK SHA-256 was
`09b6277f7da86d1b0a6b7143bb27236c46873c731736640b79fe8e72edcfd5cf`,
and the release plugin v32 APK SHA-256 was
`ee16cea749753b4e8d7c03d4cce72066495f8b7fb251ea0a88bf5165a6c0a5bb`.
All three used APK Signature Scheme v2 with certificate SHA-256
`31a681fcfffb3e428420cae280ded89292b12a3b0f59e19b7a73e32a8ae4c213`,
and each installed `base.apk` matched its local candidate byte for byte. Exact
package checks for users 0 and 10 found the host, instrumentation, and plugin
packages absent before the transaction and absent again after cleanup; the
three transaction-reserved external-storage paths were likewise absent at both
boundaries. The detailed transaction receipt is retained locally only.

This is a deliberately narrow production positive-path smoke, not broad
acceptance. It covers one physical device, one real model, one non-streaming
one-shot ask, and an App Debug host. That method does not cover a release host,
an API or ABI matrix, plugin-backed chat or streaming, tools, structured output,
usage, clipboard behavior, active-request update/uninstall, performance, or
soak. Opening the model through DocumentsUI may also change its last-location UI
state; that state has no lossless snapshot/restore contract. The earlier fake
provider Android evidence remains separate, and all broader promotion gates
remain open.

The same date also has one deliberately narrow public stream conformance smoke.
`AiPluginPublicStreamSmokeTest#publicRhinoAiStreamCompletesThroughExactFakeProvider`
returned `OK (1 test)` in 1.645 seconds with instrumentation code `-1` on
`QV710AF65F` (Android 12/API 31, arm64-v8a, user 0), from source HEAD
`50b43d00cc634f8a220856bf68f936fc800442a4`. It ran through the real
`ScriptEngineService` and public Rhino `ai.stream`, selecting the test-only
package `org.autojs.plugin.ai.provider.fake`, service class
`org.autojs.plugin.ai.provider.fake.service.FakeAiProviderService`, provider
`fake.local`, and model `fake.stream`. One `open`, more than eight
chunks, the same number of deltas, and one `done` were observed. Chunk and delta
aggregates and `done.text` exactly reproduced the UTF-8 prompt; no usage,
tool-call, error, or cancellation event was observed.

The host arm64 App Debug, instrumentation, and fake-provider APK SHA-256 values
were, respectively,
`10782233667a77c2df5c02e1299dd0d6d4194cc188d4f586f8d23257dc5cc951`,
`6025b35860be52b0477013327f7c458e12ce5751f568dfacf1ab299ae074d309`,
and `6c7319a6682b08cab2e3c610d6db0919c7c71bc034c2cec8b46eb23623d55a18`.
Final exact checks found all three packages absent for users 0 and 10 and all
nine reserved external data/media/obb paths absent.

This is **PARTIAL** fake-provider evidence, not production-model stream
acceptance. The method completed naturally; it did not invoke public
`stream.cancel()` or force-stop the engine. Exactly-once public cancellation,
the paused cancel/completion race, and silent runtime close remain JVM-only
evidence in `AiStreamTest` and `AiPluginStreamRuntimeTest`. A real production
provider/model stream, public active cancellation, release-host behavior, and
the API/ABI matrix remain untested. No broad R1 or protocol exit criterion is
promoted.

The same date also has one deliberately narrow public chat conformance smoke.
`AiPluginPublicChatSmokeTest#publicRhinoAiChatReturnsNormalizedResponseThroughExactFakeProvider`
reported `OK (1 test)`, `Time: 1.54`, and instrumentation code `-1` on
`QV710AF65F` (Android 12/API 31, arm64-v8a, user 0). The APK-tested source was
`b0aa6768484d6046551264f69ecc84b527bbb442`, following feature commit
`d480b6918`; main later integrated the production/test changes as `2d256b99f` /
`c35f199b8`, neither of which is the tested APK source. The method used the real
`ScriptEngineService` and public Rhino `ai.chat`, selecting the test-only
package `org.autojs.plugin.ai.provider.fake`, service class
`org.autojs.plugin.ai.provider.fake.service.FakeAiProviderService`, provider
`fake.local`, and model `fake.echo`.

The naturally completed Promise settled once. The result contained exactly
`text`, `reasoning`, `toolCalls`, `usage`, `finishReason`, `message`, `error`,
`raw`, `profile`, `route`, `provider`, and `model`. Text exactly reproduced the
UTF-8 prompt, reasoning was empty, toolCalls was an empty array, the six optional
fields were null, and route/provider/model were `plugin`, `fake.local`, and
`fake.echo`.

The host arm64 App Debug, instrumentation, and test-only fake-provider APK
SHA-256 values were, respectively,
`2d3dfb9c16ad91ccb73c6a969db7dcc9b10046df14f716c82d9e61f07faaf1d3`,
`9caa1cc6936d98c380c9c9ffe81178a0e81e667207da9623fa95c19bf3a11166`,
and `6c7319a6682b08cab2e3c610d6db0919c7c71bc034c2cec8b46eb23623d55a18`.
Exact checks before installation and after cleanup found the host,
instrumentation, and fake-provider packages absent for users 0 and 10 and all
nine reserved external data/media/obb paths absent.

This is **PARTIAL** fake-provider evidence. Natural completion does not prove a
real provider/model, a release host, another API/ABI, a device-side failure or
Binder-death path, or forced engine/runtime cancellation. Those lifecycle
failure and silent-close assertions remain JVM-only where covered. No broad R1
or protocol exit criterion is promoted.

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
dispatch and callback admission with exact package reinspection. Only the
explicit ask/chat/stream routes enable that path in production.

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

This phase has read-only Android package discovery and exact component
reinspection, an explicit-component metadata handshake, pure host policies,
exact-component Android model-list coordination, and an exact-component Android
`openSession`/`IAiCallback`/PFD transport. Their only production entries are
the strict prompt-only `ai.ask`, `ai.chat`, and `ai.stream` selectors described
above; there is no automatic discovery, UI selection, or multi-message/
provider-native plugin chat path.
Installing either the independent production provider or the in-repository conformance provider
does not switch the public AI module or alter its built-in defaults. The
conformance APK is test-only, has no launcher or network permission, and is
intended only for explicit conformance environments.

The narrow production integration preserves the built-in implementation when
the selector is absent, independently obtains and revalidates package facts,
owns descriptor transactions, and enforces lifecycle policies in the host. It
does not establish compatibility for proprietary `message` or `raw` fields,
nor does it close the remaining promotion gates.
