# AI Agent plugin protocol V1

Status: P1 host foundation, implemented in AutoJs6 6.8.0 / build **5285**. The host
provides model/capability brokers, registered-script catalog and execution bridge
methods, compact node and screen-text observations, drawer controls and
plugin-center management. The plugin's Agent loop, task workbench and script
chooser, and the public `ai.agent` script API remain in their original roadmap
stages. The contract is `plugin-api/ai-agent-api`, package
`org.autojs.plugin.ai.agent.api`. `AiAgentIds.REQUIRED_HOST_VERSION_CODE` is 5285.

## Identity and discovery

Plugin ID/engine: `ai-agent`; variant: `default`; official package:
`io.github.supermonster003.autojs6.plugin.ai.agent`.
The service action is `org.autojs.plugin.AI_AGENT`, with category `ai-agent` and
the `org.autojs.permission.PLUGIN` signature permission. An INFO service remains
the shared plugin-center discovery surface. ABI support is explicitly empty.

The host selects the exact package and service, checks contract/version metadata,
enablement, signer authorization and host compatibility, and holds a dedicated
binding lease. Enablement defaults to false without an explicit stored decision;
the shared official fresh-install authorization policy is unchanged.
User-granted signers require the
additional signer confirmation used by the host inspection policy. Provider and
device brokers belong to one attachment and are revoked together on failure,
detach or plugin death. A rejected attach is never automatically retried.

Management uses the existing INFO service to validate identity and the declared
minimum host build. Runtime attachment independently validates the dedicated
Agent interface and its contract. The current P0 plugin can be listed, enabled
and launched, but its placeholder Binder does not implement runnable tasks.
The complete runtime is staged in P2.5. Both service manifests, PluginInfo and
the host contract must agree on the minimum host build.

`AiAgentConnectionOwner` owns the link for the host process. Drawer instances
own their dialogs only; disposing a drawer does not close the link. The drawer
shows attachment state, one running task at most, and the queued task count.
Its management action opens the plugin's existing explicit launcher (the task
workbench arrives in P6). Discovery failures show state-specific installation,
activation, enablement, authorization, signer confirmation or compatibility
guidance. Confirmation is pinned to the current signing fingerprint set.

## Frozen Binder surface

| Interface | Transactions, in order |
| --- | --- |
| IAiAgentPlugin | getInfo, getCapabilities, attach |
| IAiAgentLink | getStatus, startRun, respond, cancelRun, listRuns, getRun, listPresets, updateConfig, detach |
| IAiAgentLinkCallback (oneway) | onStatus, onEvent |
| IAiAgentRunCallback (oneway) | onRunEvent |
| IAiAgentModelBroker | getBrokerInfo, listTargets, generate, cancel, destroy |
| IAiAgentModelCallback (oneway) | onEvent |

`attach(config, modelBroker, capabilityBroker, callback)` returns the link. The
capability argument is `IHostCapabilityBroker` from the [shared contract](host-capability-contract-v1.md).
Every Agent control/model Bundle includes `AiAgentContract.KEY_CONTRACT_VERSION = 1`;
capability dispatch/response Bundles follow their separate shared envelope.
Methods only
validate/admit work synchronously; model and tool work returns through callbacks.
New transactions may only be appended and require explicit version negotiation.

## Control and model envelopes

The public constants and KDoc in `AiAgentContract` freeze the complete vocabulary.

| Key | JSON contents |
| --- | --- |
| linkConfigJson | hostLabel, locale, scriptRoots, grantSummary |
| statusJson | state, attachedAt, runningRunId, queuedCount, pluginVersion, lastErrorCode |
| runRequestJson | goal/options/origin, or a list query |
| runEventJson | runId, type, sequence and event-specific fields; also link events |
| runResponseJson | acknowledgement/query result or runId/requestId/value/allowed/scope |
| runRefJson | runId and optional reason |
| modelRequestJson | requestId, targetId, messages, structuredJson, responseSchema, maximumOutputTokens, temperature, stream, timeoutMs |
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
Unsupported native tool or image inputs are rejected. The structured JSON loop
uses the existing ask/stream runners and does not enable native tool rounds.

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
Task/run ownership and the JS facade are implemented in P5, not by this host
foundation. Rebinding after plugin process death restores the connection only;
the controller reports link loss to observers and never starts or replays a task.
P5 will connect that notification to task handles.

The `ERROR_CODES` vocabulary is fixed by `AiAgentContract`:

| Area | Codes |
| --- | --- |
| Link and queue | `PLUGIN_UNAVAILABLE`, `LINK_DETACHED`, `HOST_UNAVAILABLE`, `QUEUE_FULL` |
| Run lookup/interaction | `RUN_NOT_FOUND`, `RUN_NOT_INTERACTIVE` |
| Tool admission | `TOOL_UNKNOWN`, `TOOL_DISABLED`, `TOOL_ARGUMENTS_INVALID`, `CAPABILITY_DENIED` |
| Resource admission | `QUOTA_EXCEEDED`, `RATE_LIMITED`, `LIMIT_EXCEEDED` |
| Model and decision | `TARGET_UNSUPPORTED`, `TARGET_UNAVAILABLE`, `MODEL_FAILED`, `MODEL_TIMEOUT`, `DECISION_UNPARSABLE` |
| Observation/action | `A11Y_SERVICE_NOT_RUNNING`, `NODE_REF_STALE`, `NODE_NOT_FOUND`, `SCREEN_LOCKED`, `OCR_PLUGIN_REQUIRED` |
| Script execution | `SCRIPT_NOT_REGISTERED`, `SCRIPT_TIMEOUT`, `SCRIPT_FAILED` |
| User, budget and protocol | `USER_DENIED`, `USER_TIMEOUT`, `BUDGET_EXCEEDED`, `CANCELLED`, `INVALID_REQUEST`, `UNSUPPORTED_CONTRACT` |

These are Agent control-plane codes, including future runner/JS behavior.
Capability responses retain the shared broker's lowercase error categories;
the plugin's tool layer maps those observations into Agent errors. Defining a
code here does not mean the P0 placeholder implements the associated behavior.

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

The explicit broadcast action is `org.autojs.autojs6.action.AI_AGENT_ATTACH` and
its package is set to the host. The exported receiver requires PLUGIN permission
and routes to the same process-owned connection flow as the drawer.
Its authentication implementation also validates a PendingIntent under
`AiAgentActions.EXTRA_ATTACH_IDENTITY`. The plugin creates this immutable token
in its own package; the host checks `creatorPackage`, `creatorUid`, installed UID
and official/trusted signer. On API 31 and newer it explicitly rejects a mutable
token; earlier Android versions expose the creator identity but not that immutable
flag check. It never sends or retains the token. A plain package
name extra, or `Binder.getCallingUid()` inside `onReceive`, is not sender proof.
This token permits the same validation on Android versions before API 34.

The existing MainActivity also consumes `EXTRA_AI_AGENT_ATTACH` with the same
identity token and authorization checks. It removes the request extras before
handling the request so activity recreation does not replay it. The plugin-side
decision to use this fallback after 500 ms remains the D41/P7 compatibility
decision. An unavailable background request queues guidance for the resumed host
UI instead of launching an activity from the broadcast receiver.

Host main-process startup restores a connection when `isNormallyClosed` is false.
It neither starts nor replays tasks. Following fixed roadmap decisions D15/D16,
there is no Agent boot receiver; P1.5's conflicting "boot" wording does not add
an auto-start policy. Plugin package and authorization/enablement changes refresh
admission and detach an unavailable link.

P1.5 entry and device evidence is recorded in
[the host entry report](evidence/ai-agent-p15-20260923.md). The independent P7
conformance APK's combined model/observation/script round trip is still pending;
the implemented host foundation is not evidence that the whole Agent is usable.

## P1.4 host observations and registered scripts

These methods use the shared capability broker's existing request envelope and
JSON-array `args`. They do not add an AIDL transaction or a public `ai.agent`
augment; the latter remains P5.

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
narrow the working directory and these configured roots. Run-scoped configuration
will use this host boundary when the P3/P5 callers are integrated.

Registered execution accepts `timeoutMs`, `captureConsole`, `agentRunId` and
`presetName` in its third options argument. A missing run ID is generated by the
host. The effective execution timeout is bounded by the manifest, the requested
timeout and the enclosing request deadline, reserving up to 750 ms for dispatch
and stop cleanup. Very short enclosing deadlines can still return the broker's
timeout before a structured outcome is delivered. Link destruction cancels its
active invocation handles. The public result/context API and its JS-level
acceptance test are intentionally left for P5; P1.4 device tests exercise the
same execution state through an internal Java hook.
