# 3-Stove Agent plugin protocol V2

Status: **versioned V2**. V2 (2026-09-27) is the renamed contract line of the former
AI Agent protocol V1 (`docs/dev/ai-agent-protocol-v1.md` in git history before the
rename). The plugin ID/engine/category, service action, attach broadcast and extras,
AIDL package, interface names, contract classes and capability keys all changed to
3-Stove Agent identifiers, so a V1 plugin cannot bind the V2 host and
`ThreeStoveAgentContract.MIN_SUPPORTED_CONTRACT_VERSION` is **2**. The transaction
order, Bundle keys, JSON envelopes, limits and error vocabulary are unchanged from V1;
the host build shipping the rename is **5298** and
`ThreeStoveAgentIds.REQUIRED_HOST_VERSION_CODE` equals it. The host provides
model/capability brokers, registered script discovery/execution, screen
observations/actions, on-demand connection and Plugin Center management. The plugin
implements the Agent loop, parameter completion, confirmations, task workbench,
history, presets, memory and system entry points. The additive native-tool broker
extension below is P9.1 host support; Provider and Agent adoption have separate
roadmap gates.

The contract is `plugin-api/three-stove-agent-api`, package
`org.autojs.plugin.three.stove.agent.api`. Historical V1 floors (AI Agent 1.0.0,
`REQUIRED_HOST_VERSION_CODE` **5286**): the plugin's INFO/runtime metadata required
AutoJs6 6.8.0 / build **5289**, for inspected actions bound to execution; complete
public task APIs required build **5293**; registered-script `result`/`context` first
appeared in build 5287. Consumers must check both installed metadata and the
feature they intend to use. Native model tool continuations require host build
**5297** and the broker's explicit `toolCallingVersion: 1` advertisement; a version
number alone is not feature negotiation. The original 1.0.0 structured JSON path
remains available. The public JavaScript `mcp` tool-group option requires a host
containing the P10 public-option change (`3cdf7de13c`) and a supporting Agent
version; that build number alone does not prove MCP-option support. Neither
addition changed the V1 contract number or AIDL order; V2 changes identifiers only.

## Identity and discovery

Plugin ID/engine: `three-stove-agent`; variant: `default`; official package:
`io.github.supermonster003.autojs6.plugin.three.stove.agent`.
The service action is `org.autojs.plugin.THREE_STOVE_AGENT`, with category `three-stove-agent` and
the `org.autojs.permission.PLUGIN` signature permission. An INFO service remains
the shared plugin-center discovery surface. ABI support is explicitly empty.

The host selects the exact package and service, checks contract/version metadata,
enablement, signer authorization and host compatibility, and holds a dedicated
binding lease. An officially signed installation defaults to enabled, including
installations completed while Plugin Center is stopped. Other signers default to
disabled. A saved user choice takes precedence; discovery failures do not overwrite
Agent enablement, and runtime availability checks still reject unavailable builds.
User-granted signers require the
additional signer confirmation used by the host inspection policy. Provider and
device brokers belong to one attachment and are revoked together on failure,
detach or plugin death. A rejected attach is never automatically retried.

Management uses the existing INFO service to validate identity and the declared
minimum host build. Runtime attachment independently validates the dedicated
Agent interface and its contract. P2.5 connected task controls and the runner
to the real host brokers. Both plugin service manifests and PluginInfo must agree
on the packaged plugin's minimum host build; it may exceed the base V2 floor (5298).

`ThreeStoveAgentConnectionOwner` owns the link for the host process. Plugin Center
is the sole enable/disable control; the home drawer contains no Agent switch.
Scripts and the plugin UI connect on demand. Concurrent requests share one bind,
and enablement is checked again after binding so a concurrent disable revokes the
new lease. `ai.agent.status()` is read-only. Cold synchronous script calls use a
worker thread: they must never block the main looper that delivers service callbacks.
Host and Plugin Center activities own lifecycle-scoped dialogs, independently of
the link. Plugin Center opens the plugin's task workbench. Failures show installation,
activation, enablement, authorization, signer confirmation or compatibility
guidance. Confirmation is pinned to the current signing fingerprint set.

## Frozen Binder surface

| Interface | Transactions, in order |
| --- | --- |
| IThreeStoveAgentPlugin | getInfo, getCapabilities, attach |
| IThreeStoveAgentLink | getStatus, startRun, respond, cancelRun, listRuns, getRun, listPresets, updateConfig, detach |
| IThreeStoveAgentLinkCallback (oneway) | onStatus, onEvent |
| IThreeStoveAgentRunCallback (oneway) | onRunEvent |
| IThreeStoveAgentModelBroker | getBrokerInfo, listTargets, generate, cancel, destroy, submitToolResults (optional extension) |
| IThreeStoveAgentModelCallback (oneway) | onEvent |

`attach(config, modelBroker, capabilityBroker, callback)` returns the link. The
capability argument is `IHostCapabilityBroker` from the [shared contract](host-capability-contract-v1.md).
Every Agent control/model Bundle includes `ThreeStoveAgentContract.KEY_CONTRACT_VERSION = 2`;
capability dispatch/response Bundles follow their separate shared envelope.
Methods only
validate/admit work synchronously; model and tool work returns through callbacks.
New transactions may only be appended and require explicit version negotiation.

## Control and model envelopes

The public constants and KDoc in `ThreeStoveAgentContract` freeze the complete vocabulary.

| Key | JSON contents |
| --- | --- |
| linkConfigJson | hostLabel, locale, scriptRoots, grantSummary |
| statusJson | state, attachedAt, runningRunId, queuedCount, pluginVersion, lastErrorCode, scriptRoots |
| runRequestJson | goal/options/origin, or a list query |
| runEventJson | runId, type, sequence and event-specific fields; also link events |
| runResponseJson | acknowledgement/query result or runId/requestId/value/allowed/scope |
| runRefJson | runId and optional reason |
| modelRequestJson | generation: requestId, targetId, messages, structuredJson, responseSchema, maximumOutputTokens, temperature, stream, timeoutMs; native extension adds tools/maximumToolRounds, or requestId/round/results for submission |
| modelEventJson | requestId, type, sequence and event-specific fields |
| modelRefJson | requestId and optional reason |
| modelBrokerInfoJson | available, providerId and public request/output/schema limits |

Model `messages` is an array of objects with exactly two fields: string `role`
(`system`, `user` or `assistant`) and nonempty string `content`. For example:

```json
{
  "requestId": "decision-1",
  "targetId": "local:example",
  "messages": [{"role": "user", "content": "Describe the next action."}],
  "structuredJson": true,
  "responseSchema": {"type": "object"}
}
```

The target ID must come from `listTargets`; `local:example` is only illustrative.
There are 1-256 messages, and the final message must have role `user`; the selected
Provider may impose a smaller negotiated limit. This is the Agent JSON request
shape, which the host converts to its internal Provider message objects.
`responseSchema`, when supplied, is an object and requires `structuredJson: true`;
structured mode without a schema defaults to `{"type":"object"}`. Unknown request
fields, duplicate JSON keys and invalid field types are rejected. `timeoutMs`
defaults to 30,000 and accepts 1,000-600,000 ms for model calls.

Each admitted request with a live callback has
`started`, zero or more `chunk` events (streaming), optional `usage`, then exactly
one `completed`, `failed` or `cancelled`. Sequences increase per request. The host
does not expose credentials, private profile fields or raw Provider error bodies.
Image inputs require the negotiated extension below. Requests without tools keep the original
ask/stream behavior and do not enable native tool rounds. Public script `ai.ask`
and `ai.stream` options do not expose this internal model-broker extension.

### Model image extension

Read `modelBrokerInfoJson.visionVersion` before using images; missing means
unsupported. Extension version 1 preserves the Agent contract (V1, now V2) and its existing
transactions. The selected catalog target must advertise `vision`, and the host
must negotiate Provider 2.1 with vision capabilities. This is an internal broker
extension; it does not add a public script `ai.ask` image option.

Generation JSON can add `vision: true` to reserve support for later image tool
results. Initial `imageRefs` also imply vision. Keep `messages` as role/content
objects, and add references at the request root:

```json
{
  "requestId": "vision-1",
  "targetId": "profile:example",
  "messages": [{"role": "user", "content": "Describe this synthetic image."}],
  "vision": true,
  "imageRefs": [{
    "messageIndex": 0,
    "descriptorIndex": 0,
    "mimeType": "image/png",
    "byteLength": 1234,
    "width": 32,
    "height": 24,
    "sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
  }]
}
```

The dimensions, byte length and hash above are illustrative; supply the actual
encoded image values. The Bundle carries `modelImageFds`, a
`ParcelFileDescriptor[]`, separate from the optional JSON `payloadFd`. Image
indices consume every array entry exactly once. `messageIndex` must reference a
user message. An individual native `results` entry can add `imageRefs` with the
same fields except `messageIndex`; all entries share one FD array for that batch.
The original request must have opted into vision before such results are sent.

Only JPEG/PNG are accepted. Limits advertised by the broker are 4 images, 4 MiB
per image, 8 MiB per batch, 4096 per edge, 16,777,216 pixels, and 16 images / 32 MiB
across the initial request and its native continuations. Negotiation can narrow
them. The UTF-8 JSON/text grant is independent of encoded image bytes. Target,
index, quota, dimensions, digest and actual decode checks happen before model
dispatch; unsupported inputs fail without dropping images or switching targets.
Pipe reads honor cancellation and the original deadline. Malformed Bundles,
foreign UIDs, null callbacks, teardown and failed reads also close image FDs.

Admission reserves an image estimate of
`1024 + 4 * ceil(width/32) * ceil(height/32)` tokens per image. Each native model
continuation includes all retained images in its estimate, and cumulative
reported Provider usage replaces estimates when available. This is a bounded
fallback accounting policy, not a guarantee about a particular model's billing.
The existing model-call rate, token, output and deadline budgets remain active.
Raw images/base64/file paths are not emitted in events or ordinary logs.

### Native model tool extension

Read `modelBrokerInfoJson.toolCallingVersion` before using the appended transaction
or supplying tool definitions. A missing value means unsupported. Version 1 of
this extension retains the Agent contract (V1, now V2) and the original five transaction codes.
The selected catalog target must also advertise the Provider `tools` capability.
Streaming remains optional and separately negotiated.

`tools` is a nonempty array of `{name, description, inputSchema}` definitions;
schemas are JSON objects. Names are unique and obey the Provider stable-ID rules.
`maximumToolRounds` is required with tools, accepts 1-16, and is zero/absent
without tools. Provider negotiation may impose smaller limits. The input grant
also counts UTF-8 tool names/descriptions and serialized schemas.

After `started`, a generation can emit `tool_calls` with a positive sequential
`round` and `calls: [{callId, name, arguments}]`. Arguments are materialized JSON,
including bounded Provider descriptor payloads. Tool names must have been declared
in the request. One batch is outstanding at a time; Provider replayed IDs, an
extra round, chunks during a pending batch or premature completion terminate the
session. The host only transports these calls; the Agent must apply its existing
validation, confirmation and journaling before executing any device action.

Submit the complete batch to `submitToolResults` using `modelRequestJson`:

```json
{
  "requestId": "native-1",
  "round": 1,
  "results": [{"callId": "call-1", "output": "Observed result", "isError": false}]
}
```

Each result has a string output and optional boolean `isError`. IDs must exactly
match the pending batch, without duplicates; round numbers must match. Outputs
are capped at 64 KiB each, and the complete submission at 128 KiB of UTF-8 JSON.
These limits are also advertised as `maximumToolResultBytes` and
`maximumToolResultBatchBytes`; `maximumToolRounds` advertises 16. Submissions may
use the existing correlated `payloadFd` transport. A stopped/incomplete pipe does
not block cancellation or the original generation deadline. Only one submission
may be queued per outstanding batch. A stale ID with no live generation is ignored.

Results continue the original generation, callback and event sequence. There is
one terminal event. Cancellation, deadline, callback death and link teardown
remain active while awaiting results. Each continuation rechecks the input grant,
reserves tokens and consumes another entry in the link's model-call rate limit.
Usage events are cumulative and may repeat across native rounds. Reported usage
replaces prior byte estimates, including reports arriving after `tool_calls`,
instead of charging a cumulative count again for each round. The original
one-shot usage behavior is unchanged.

For model generation requests and model callback events, `payloadFd` is the
exclusive alternative to their inline JSON key. It contains the complete request
or event JSON in UTF-8; it is not a Node Bridge result payload. Model event
descriptors are bounded to 2 MiB, with the 64 KiB generated-text cap still applied
before serializing the event. Receivers close their transferred FD on every path,
including rejection. `listTargets` and `cancel` requests are inline only; the
current host also rejects descriptors on link status/event callbacks. Model
requests using an FD must also supply `modelRefJson: {requestId}` as a small
correlation header, bounded to 512 UTF-8 bytes. The body's ID must match it. This
allows cancellation and correlated timeout events before a pipe has delivered its
body. Active request IDs are checked for duplicates after decoding and before
Provider invocation. The header carries no model messages or credentials. Requests
are capped at 128 KiB inline / 2 MiB via FD before the narrower input grant;
model output at 64 KiB, schema at 16 KiB, and inline events at 32 KiB. The input
grant counts UTF-8 message content plus the serialized schema, independently of
the outer JSON transport size. Control
status/link notifications are small inline records. No byte limit counts UTF-16
characters in place of UTF-8 bytes.

Link states are `detached`, `attaching`, `attached`, `host_unavailable`, `failed`.
The centralized `RUN_STATES` set contains `queued`, `running`, `waiting_input`,
`waiting_confirmation`, `cancelling`, `completed`, `partial`, `failed`, `blocked`
and `cancelled`. The last five are terminal; reconnecting does not resume a
blocked run. Run events are `state`, `progress`, `step`, `input`, `confirmation`,
`done` and `error`; link events also include `queue_changed` and `preset_changed`.
The host owns JS handles and fails outstanding handles with `PLUGIN_UNAVAILABLE`
when the plugin process dies. Rebinding restores only the connection and never
starts or replays a task. The plugin records unfinished persisted tasks as
`failed` with the historical interruption reason `process-died`; an already
terminal record, including `blocked` after host death, keeps its original result.
No queue or pending confirmation is restored after plugin process death.

For host-owned Agent brokers, screen observations and actions require an
interactive, unlocked device. The host rejects accessibility screen operations,
application launches and notification/quick-settings actions with a stable
`SCREEN_LOCKED` diagnostic while the screen is off or the keyguard is showing.
The plugin returns that observation to the model and does not discard it during
the pre-action screen sample. Read-only screen-state and device-info calls remain
available. This does not change direct Node.js scripts or other plugin families.

The `ERROR_CODES` vocabulary is fixed by `ThreeStoveAgentContract`:

| Area | Codes |
| --- | --- |
| Link and queue | `PLUGIN_UNAVAILABLE`, `LINK_DETACHED`, `HOST_UNAVAILABLE`, `QUEUE_FULL` |
| Run lookup/interaction | `RUN_NOT_FOUND`, `RUN_NOT_INTERACTIVE` |
| Tool admission | `TOOL_UNKNOWN`, `TOOL_DISABLED`, `TOOL_ARGUMENTS_INVALID`, `CAPABILITY_DENIED` |
| External tool execution | `TOOL_FAILED` |
| Resource admission | `QUOTA_EXCEEDED`, `RATE_LIMITED`, `LIMIT_EXCEEDED` |
| Model and decision | `TARGET_UNSUPPORTED`, `TARGET_UNAVAILABLE`, `MODEL_FAILED`, `MODEL_TIMEOUT`, `DECISION_UNPARSABLE` |
| Observation/action | `A11Y_SERVICE_NOT_RUNNING`, `NODE_REF_STALE`, `NODE_NOT_FOUND`, `SCREEN_LOCKED`, `OCR_PLUGIN_REQUIRED` |
| Script execution | `SCRIPT_NOT_REGISTERED`, `SCRIPT_TIMEOUT`, `SCRIPT_FAILED` |
| User, budget and protocol | `USER_DENIED`, `USER_TIMEOUT`, `BUDGET_EXCEEDED`, `CANCELLED`, `INVALID_REQUEST`, `UNSUPPORTED_CONTRACT` |

These are Agent control-plane codes used by the runner and public JS handles.
Capability responses retain the shared broker's lowercase error categories;
the plugin's tool layer maps those observations into Agent errors. Defining a
code here does not grant a capability or imply support for a future tool.
`TOOL_FAILED` represents an unsuccessful external tool operation, including an
MCP `isError` result. It is not evidence of host death or successful task progress.
The plugin can include a fixed MCP diagnostic reason in the observation; this
does not add another public error-code family or expose remote error prose.
The pre-P10 host at `cdf1b6a564` already preserves task-event/result JSON error
codes without checking `ERROR_CODES`. Its run registry, run record and JS handle
receive paths are unchanged by P10, so receiving `TOOL_FAILED` does not itself
require a contract-version or minimum-host increase. `ERROR_CODES` also classifies
ordinary exception messages in the JS error adapter; that is distinct from
receiving a plugin task result. An older host can still reject an explicit `mcp`
tool-group option before admission because that public-option vocabulary changed.

Task protocol ceilings are one running task per link, eight queued tasks,
4 KiB goal text, 8 KiB supplied context, 200 steps, 300 model calls, 30 minutes
for attached runs and 60 minutes for detached runs. A step journal is bounded
to 200 records / 1 MiB. P2 implements run admission and budgets within these
ceilings; P5 implements detached ownership. Capability grants already limit
Agent dispatch to two concurrent tool calls. Model limits and transport byte
limits described above are enforced by the existing host brokers.

The Agent grant includes `keys.notifications` and `keys.quickSettings`, matching
the notification-panel values of `ui_press_key`. The `keys` permission token does
not enable other methods: `lockScreen`, `powerDialog`, `takeScreenshot` and
`splitScreen` remain outside the method allowlist.

## Model admission

The production broker currently selects the official 3-Stone AI component through
the existing host runner. The constructor accepts an injected Provider component
for host tests, but this is not a plugin-facing Provider selector or an automatic
catalog merge across installed Providers. The Agent chooses a public `targetId`
from that broker's catalog (`local:*` or `profile:*`) and cannot supply a component,
profile credentials or a Provider ID. `modelBrokerInfoJson.providerId` identifies
the host-selected Provider.

`listTargets` takes `modelRequestJson` containing `requestId` and optional
`timeoutMs`, and returns a completed model event containing `targets`. `generate`
uses the full model request shape above; completion includes `text`, `finishReason`
and `targetId`. Chunk events include `text` and increasing `chunkSequence`; failure
events include a stable `code` and, when available, a stable Provider enum `reason`.
For PROVIDER_FAILED, the host may preserve an exact, closed ONLINE_* category from the
existing Provider error field; arbitrary provider codes and messages are discarded. This
diagnostic does not change the main error code or permit replay. ONLINE_REQUEST_REJECTED
is distinct from the bare REQUEST_REJECTED format-fallback signal.
`cancel` uses `modelRefJson`, and identifies a request by `requestId`.

Each link has bounded work queues, callback death cleanup, request deadlines,
cancel support and a plugin UID guard. Catalog results expose targetId,
displayName, locality, capabilityIds, configured/available, maximumContextBytes
and supportedControls. Targets are revalidated before generation. An explicit
structured request requires the target's `structured-json` capability; P2's
degraded loop sends a plain request for targets lacking that capability.

The host enforces the grant's requests/minute and total-token limits and narrows
input bytes by target context limits. Missing token usage is estimated at
0.4 token/UTF-8 byte. A successful response cannot bypass accounting by omitting
usage. No logging includes goals, messages, schema contents or model output.

## Attachment request authentication

The explicit broadcast action is `org.autojs.autojs6.action.THREE_STOVE_AGENT_ATTACH` and
its package is set to the host. The exported receiver requires PLUGIN permission
and routes to the same process-owned connection flow as script use.
Its authentication implementation also validates a PendingIntent under
`ThreeStoveAgentActions.EXTRA_ATTACH_IDENTITY`. The plugin creates this immutable token
in its own package; the host checks `creatorPackage`, `creatorUid`, installed UID
and signer authorization. A user-granted signer still needs the separate pinned
Agent capability confirmation before attachment. On API 31 and newer it explicitly rejects a mutable
token; earlier Android versions expose the creator identity but not that immutable
flag check. It never sends or retains the token. A plain package
name extra, or `Binder.getCallingUid()` inside `onReceive`, is not sender proof.
This token permits the same validation on Android versions before API 34.

The existing MainActivity also consumes `EXTRA_THREE_STOVE_AGENT_ATTACH` with the same
identity token and authorization checks. It removes the request extras before
handling the request so activity recreation does not replay it. The plugin uses
this fallback after 500 ms if the background attachment is not available, as
specified by D41. An unavailable request queues guidance for the resumed host
UI instead of launching an activity from the broadcast receiver.

Host main-process startup inspects availability without connecting. The former
`isNormallyClosed` drawer preference is ignored. Actual use connects automatically;
connection establishment neither starts nor replays tasks. Following roadmap decisions D15/D16,
there is no Agent boot receiver; P1.5's conflicting "boot" wording does not add
an auto-start policy. Plugin package and authorization/enablement changes refresh
admission and detach an unavailable link.

Since build 5286, either authenticated attachment entry may include the existing
`ThreeStoveAgentContract.KEY_LINK_CONFIG_JSON` extra with exactly `{scriptRoots:[...]}`.
This is a proposal from plugin settings, not a grant: unknown/duplicate fields,
duplicate paths, relative paths, traversal and oversized input are rejected.
There are at most 32 extra roots, 1024 UTF-8 bytes per path and 8 KiB per proposal.
The host independently checks canonical containment, visibility and existence.
On an attached link it merges only roots into the last accepted configuration,
preserving the host's locale and `grantSummary`, and prepares the capability
broker's catalog before asking the plugin to accept the change. Only a successful
update commits the roots and persists the proposal for reconnection. Invalid
roots preserve the accepted configuration. Accepted changes cancel current and
queued plugin tasks and invalidate its catalog cache; no task is resumed.
The plugin's status reports the accepted `scriptRoots`, allowing the launcher to
distinguish a connected link from a rejected or still-pending settings change.
Task `options.scriptRoots` defaults to that set and may only select a subset;
an explicit empty array selects the working directory alone. The plugin queries
the broker with `roots:[".", ...selectedExtraRoots]` (at most 33 selectors).

The plugin refreshes the catalog at task start and uses a 60-second link cache
for `script_catalog` searches. Deterministic keyword ranking presents at most
24 records, further reduced as whole records to fit the model's context budget.
Metadata is data, not task instructions. Descriptions and parameter summaries
are bounded, duplicate IDs are omitted as ambiguous, and source text is never
sent to the plugin. The plugin validates/defaults parameters, asks for missing
values and confirms invocation; the host rechecks the manifest and arguments at
execution admission.

Entry evidence is recorded in [the host entry report](evidence/ai-agent-p15-20260923.md)
and the [six-device attachment report](evidence/ai-agent-p7-compatibility-20260925.md).
Independent peer, grant and reciprocal fake-host cases are documented in
[P7 conformance](evidence/ai-agent-p7-conformance-20260925.md). Model and script
execution have their own [broker](three-stove-agent-model-broker-testing.md) and
[script](agent-script-execution-testing.md) tests. Deterministic conformance is
separate from real-model task success and the final release gate.

## Public JavaScript API

Build 5293 connects `ai.agent.run`, `create`, `get`, `list` and `status` to the
plugin-owned loop; `catalog`, `result` and `context` expose the host registration
and result channel. A run returns an AgentRun handle with progress/input/
confirmation events, a result promise and cancellation. A fulfilled result may
be completed, partial, failed, blocked or cancelled; callers must inspect status.
`interaction: "plugin"` delegates answers/approval to the plugin UI, while
`"script"` requires an owning script to respond. Observing a run does not grant
ownership. Detached execution changes script-lifetime cancellation, not host
permissions or confirmation rules, and never enables restart recovery.

P10 adds `mcp` to the accepted tool-group names and leaves it disabled by default,
including when an assistant's empty `tools` object resolves the defaults.
Explicit selections such as `tools: ["mcp", "user"]` are still constrained by
the plugin's global settings and selected preset. They do not configure a server,
provide credentials, discover tools or approve an action. Server profiles,
explicit tool selection, per-server risk, credentials and MCP transport belong
to the Agent plugin. This host change does not implement the proposed MCP Client
plugin or add MCP network requests to the model/capability brokers.

The plugin freezes admitted MCP definitions into the same per-run catalog used
for prompts, validation, confirmation and execution. Its JSON and native model
paths preserve failed-tool observations; a native continuation marks an external
tool failure with `isError: true` and does not replay the action. External server
authority is independent of the host's built-in capability grant. Transport
compatibility and validation limits are documented by the Agent implementation;
acceptance of the `mcp` group alone is not a claim to support every MCP server.
The scoped host change and its current validation are recorded in
[P10 host evidence](evidence/ai-agent-p10-host-20260926.md).

The maintained API and type documentation are in
[AutoJs6 Documentation](https://github.com/SuperMonster003/AutoJs6-Documentation/blob/master/api/ai.md).
Bundled `sample/ai/agent-*.js` files demonstrate basic, interactive and detached
task control. Registered-script examples are under `sample/agent/`.

## Installation catalog and release index

The host installation wizard categorizes 3-Stove Agent under Tools using the exact
official package. It is optional in both built-in profiles. Plugin Center is the
sole enablement control; the official installation defaults to enabled unless the
user has explicitly saved a disabled choice.

Download entries come from the separate AutoJs6-Official-Plugins-Index repository.
Its generator admits published releases with APK assets and validates the required
repository inventory. Add 3-Stove Agent to that inventory and regenerate after the
first APK release is published. Before publication, the wizard omits the missing
download entry; no placeholder release URL or fabricated version/hash is used.

## P4.2 inspected actions

Host build 5289 adds accessibility.inspectNode(target, action) for click, longClick,
setText, scrollForward and scrollBackward. It performs a bounded read-only search
and resolves the actual eligible recipient, including an ancestor when applicable.
The result contains a target with nodeRef, snapshotId and an opaque actionToken,
plus bounded text/desc/packageName and password/enabled/uncertain risk metadata.
Password text is omitted. Containers and clipped labels are uncertain and require
individual confirmation in the Agent. Tokens stay inside the execution adapter.

Prepared targets are scoped to one broker provider, limited to eight and expire
after five minutes. Execution checks the same method, snapshot, window identity,
unique full node fingerprint, bounds, password flag, enabled flag and action
eligibility. It acts directly on the inspected recipient without a later ancestor
fallback. If inspection climbed from a child, its full identity and presence
inside that recipient are checked too. The token authorizes only the recipient
reference, not other nodes in the private inspection snapshot. Changed, evicted
or foreign targets return NODE_REF_STALE.

Prepared setText accepts a third {append:true} options argument. Reading and
appending happen inside the host after identity validation. Combined text has a
64 KiB UTF-8 bound. Password append is rejected with CAPABILITY_DENIED because
accessibility may expose a mask; password replacement remains supported. Existing
selector/reference actions without a preparation token retain their legacy
behavior. This is an additive bridge extension, not an AIDL or public JS API change.

Compact dumps also include an opaque windowIdentity digest, so observation clients
can distinguish windows within one package/activity without receiving the raw
root identity. The Agent keeps visible snapshot references separate from hidden
inspection and stabilization dumps. Its operation replies retain ok, actionResult
and windowChanged (null when unavailable), plus bounded change/stability evidence.
An acknowledged action alone does not establish that the task goal was achieved.

## P1.4 host observations and registered scripts

These methods use the shared capability broker's existing request envelope and
JSON-array `args`. They do not add an AIDL transaction. The public `ai.agent`
augment described above delegates to these host-backed paths.

- `accessibility.dump([{format:"compact", maxNodes:200, maxDepth:32,
  visibleOnly:true}])` returns the existing dump metadata plus `text`,
  `snapshotId`, `nodeCount` and `truncated`. Text follows the MCP formatter's
  existing header and one-line `#n1` references. Text is capped at 256 KiB on
  complete lines; only emitted nodes are registered.
- `click`, `longClick`, `setText`, `scrollForward` and `scrollBackward` accept
  `{nodeRef:"#n1", snapshotId:"..."}` as their first argument. `setText` takes
  the string as its second argument. Omitting snapshotId selects the newest
  snapshot of this provider. Each provider/link retains at most eight snapshots
  using LRU eviction and discards them on destruction. No extra time-based expiry
  is added. A fresh tree walk must find exactly one matching identity in the same
  window scope and within the bounds tolerance. Missing, ambiguous, foreign,
  cleared or evicted references return `invalid-request` with `NODE_REF_STALE`.
  The existing selector and coordinate call forms retain their original behavior.
- `accessibility.readScreenText([{region:[x,y,width,height]}])` selects an enabled,
  authorized OCR plugin in the same automatic order as `ocr.recognizeText`
  (ML Kit, Paddle, Rapid). It tries accessibility screenshot capture on API 30+
  and falls back to this link's already-authorized MediaProjection session.
  It does not open a new capture-consent prompt. The result is an array of
  `{text,bounds,confidence}` in screen coordinates, at most 400 entries / 64 KiB.
  Absent OCR returns `unavailable` with `OCR_PLUGIN_REQUIRED`, before capture.
  The method requires `accessibility`, `screen_capture` and `ocr` capabilities.

Screenshots and cropped bitmaps are owned and recycled by the host observation
operation. No pixels, image handles or payload descriptors are returned to Agent.
The existing external OCR plugin still receives image input using its established
host-to-OCR transport; the roadmap's image-ownership statement does not mean that
an external OCR service can run without receiving its input.

`agent.listScripts`, `agent.readManifest` and `agent.execRegistered` are described
in [the script manifest protocol](agent-script-manifest-v1.md). Catalog operations
require `agent`; execution additionally requires `agent.exec`, `engines` and
`engines.exec`. The `agent` capability alone never implies `agent.exec`.

Link configuration may carry `scriptRoots` (at most 32 absolute directory paths).
The host validates the complete replacement before applying it. Failed plugin
configuration updates leave the prior roots intact. Configuration is a complete
snapshot: omitting `scriptRoots` clears the additional roots, including after a
later reattachment. Per-request `roots` only
narrow the working directory and these configured roots. Preset and task-scoped
configuration use this same host boundary and cannot widen the approved roots.

Registered execution accepts `timeoutMs`, `captureConsole`, `agentRunId` and
`presetName` in its third options argument. A missing run ID is generated by the
host. The effective execution timeout is bounded by the manifest, the requested
timeout and the enclosing request deadline, reserving up to 750 ms for dispatch
and stop cleanup. Very short enclosing deadlines can still return the broker's
timeout before a structured outcome is delivered. Link destruction cancels its
active invocation handles. Public result/context calls report into this execution
state; the script-execution report linked above covers the JS-level path.
