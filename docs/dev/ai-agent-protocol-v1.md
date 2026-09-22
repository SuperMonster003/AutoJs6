# AI Agent plugin protocol V1

Status: P1 host foundation. This document does not declare the Agent loop, task
workbench, script catalog, drawer entry or `ai.agent` script API available. Those
remain in their original roadmap stages. The contract is
`plugin-api/ai-agent-api`, package `org.autojs.plugin.ai.agent.api`.

## Identity and discovery

Plugin ID/engine: `ai-agent`; variant: `default`; official package:
`io.github.supermonster003.autojs6.plugin.ai.agent`.
The service action is `org.autojs.plugin.AI_AGENT`, with category `ai-agent` and
the `org.autojs.permission.PLUGIN` signature permission. An INFO service remains
the shared plugin-center discovery surface. ABI support is explicitly empty.

The host selects the exact package and service, checks contract/version metadata,
enablement, signer authorization and host compatibility, and holds a dedicated
binding lease. Default enablement is false. User-granted signers require the
additional signer confirmation used by the host inspection policy. Provider and
device brokers belong to one attachment and are revoked together on failure,
detach or plugin death. A rejected attach is never automatically retried.

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
Run states and errors are the centralized `RUN_STATES` and `ERROR_CODES` sets.
Task/run ownership and the JS facade are implemented in P5, not by this host
foundation. Rebinding after plugin process death restores the connection only;
the controller reports link loss to observers and never starts or replays a task.
P5 will connect that notification to task handles.

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
its package is set to the host. Registration and user guidance land in P1.5.
When registered, the receiver must require PLUGIN permission; its current
authentication implementation also validates a PendingIntent under
`AiAgentActions.EXTRA_ATTACH_IDENTITY`. The plugin creates this immutable token
in its own package; the host checks `creatorPackage`, `creatorUid`, installed UID
and official/trusted signer. On API 31 and newer it explicitly rejects a mutable
token; earlier Android versions expose the creator identity but not that immutable
flag check. It never sends or retains the token. A plain package
name extra, or `Binder.getCallingUid()` inside `onReceive`, is not sender proof.
This token permits the same validation on Android versions before API 34.

The 500 ms foreground fallback remains the D41/P7 compatibility decision. No
boot receiver or automatic task restart is introduced. The host minimum build is
provisional until P1.6 closes all host integration work.
