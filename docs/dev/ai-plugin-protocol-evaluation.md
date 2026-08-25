# AI Plugin Protocol Pack Evaluation

This note records the architectural decision and current implementation status
for the provider-neutral AI plugin boundary in AutoJs6.

## Decision

Proceed with a dedicated text-generation protocol pack, a strict Binder trust
boundary, and an independently released official AI plugin. Use one unified
target catalog for on-device and explicitly configured online destinations.

The decision remains a NO-GO for a mixed-modality AI transport and for moving
credential bytes through the host. Image, audio, and video require separate
protocol families. Remote credentials remain encrypted and resolved inside the
selected plugin process.

## Current Status

As of 2026-08-25, the host and official 3-Stone AI plugin implement AI Provider
V2 directly:

- the API exposes only the V2 target catalog and session surface;
- `listTargets` pages both `local:*` and `profile:*` destinations;
- every target declares logical provider/model identity, locality,
  configuration and availability, capabilities, limits, controls, and HTTPS
  origins;
- provider declarations support `ON_DEVICE`, `REMOTE`, and `HYBRID`, with
  `NONE` or `PLUGIN_MANAGED` credential modes as appropriate;
- host session plans contain no credential bytes or arbitrary network
  configuration;
- completed events carry a verified final payload and finish reason;
- fake-provider, hostile Binder, host policy, Android compile, and real plugin
  JVM suites cover the V2 surface.

The current public `ai.ask`, `ai.chat`, `ai.stream`, and `ai.session` plugin
route is connected. Its model selector is projected onto the V2 catalog while
the next roadmap stage adds an explicit public `target` selector and
`ai.catalog()` view. Existing cloud-backed calls without a plugin selector are
unchanged during that stage.

The normative contract is [AI Provider Protocol V2](ai-provider-protocol-v2.md).
Repository and runtime gates are tracked in
[the conformance matrix](compiler-ai-protocol-conformance.md).

## Why A Target Catalog

A model name alone cannot describe the security and routing decision required
for a hybrid plugin. The same model string can appear in a local runtime and in
several online profiles, each with different provider identity, origin,
credential state, limits, cost boundary, and availability.

The V2 target record makes those properties explicit before session opening:

```text
Binder plugin identity
  -> catalog generation
    -> exact target identity
      -> logical provider + model + locality
      -> configured/available state
      -> capabilities + limits + controls
      -> declared HTTPS origins
```

The host pins the Binder plugin independently of the logical provider stored in
the target. This permits one authenticated hybrid plugin to expose local
LiteRT-LM execution and several separately configured online services without
pretending those service IDs are Binder component identities.

## Existing Host Evidence

AutoJs6 already contains security-sensitive online behavior:

- provider definitions and encrypted profile storage;
- redirect-free and retry-free credential-bearing HTTP clients;
- OpenAI-compatible, Anthropic Messages, and Gemini request/response adapters;
- bounded SSE, cancellation, usage, reasoning, and tool-call normalization;
- public `ai.ask`, `ai.chat`, and `ai.stream` facades.

Moving all of that behavior across Binder at once would not simplify the trust
boundary. The protocol pack instead defines a narrow provider-neutral contract
and lets migration occur behind explicit routing and conformance gates.

## Trust Boundary

The host side performs read-only exact-action discovery, exact-component
selection, package UID and full-signer verification, Binder descriptor checks,
metadata negotiation, target catalog validation, and exact-component
reinspection before sensitive transitions.

The authenticated snapshot includes:

- component name and package UID;
- complete signer digests;
- version code and last-update time;
- Binder provider ID and implementation version;
- protocol range and negotiated version;
- provider locality, credential mode, HTTPS origin allowlist, and ABIs;
- negotiated capabilities and limits.

Catalog and session cache keys are domain-separated and include this snapshot,
catalog generation, target identity, target locality and credential mode,
target capabilities and origins, and effective limits. They never contain
prompts, responses, page tokens, credentials, headers, or provider-native
configuration.

## Credential And Origin Policy

An on-device target has no remote profile, uses `NONE`, and declares no origin.
A remote or hybrid target has a plugin-private profile ID, uses
`PLUGIN_MANAGED`, and declares at least one canonical HTTPS origin.

The provider-level origin list is a pinned upper bound. Every target origin
must be in that list. The official plugin derives its aggregate declaration
from the current target catalog and keeps credentials in Android Keystore
backed app-private storage. Binder receives only configured state and
non-sensitive target metadata.

The host never supplies API keys, authorization headers, cookies, arbitrary
base URLs, or retry policy. The selected plugin owns its declared HTTPS request
and must not silently switch targets, origins, providers, models, credential
modes, or local/remote boundaries.

## Catalog Transport

Catalog listing is asynchronous and paged. Continuation tokens are opaque,
bounded, single-use, and scoped to the complete pinned provider identity and
catalog generation. The host performs exact package reinspection before every
initial or continuation dispatch and before callback admission.

The callback captures its Binder caller UID synchronously, verifies typed
page/error envelope ceilings and a bounded flood slot, then performs one
defensive copy. Duplicate targets, generation changes, token replay,
cross-provider tokens, excessive pages, callback-after-terminal, and malformed
metadata fail closed.

## Session Transport

The host validates the requested feature set against host, provider, and target
capabilities before creating an immutable credential-free plan. Session opening
then pins the exact provider, catalog generation, target, effective byte limits,
and credential disposition.

The session policy owns request admission, descriptors, callback UID checks,
stream credits, sequence order, tool rounds, monotonic usage, output aggregation
and digest verification, cancellation, timeout, Binder death, terminal
arbitration, and cleanup. Exactly one completed, failed, or cancelled result can
win.

Completion is data-bearing: it contains the materialized final output and a
valid finish reason. The host verifies it against accepted chunks and quotas.
There is no structural-only tool validation path and no payload-free completion
shortcut.

## Operational Boundaries

- Plugin routing remains explicit; installing a provider alone does not
  redirect ordinary host AI traffic.
- No automatic local-to-remote or remote-to-local fallback is allowed.
- An unavailable or unconfigured target fails explicitly.
- Local target execution does not use the network.
- Online targets may contact only their configured and declared HTTPS origins.
- Model download remains user initiated and limited to pinned recommendations;
  it is not network model discovery.
- Safe diagnostics exclude prompts, output, errors containing provider details,
  page tokens, credentials, headers, and paths.

## Verification Evidence

The automated matrix includes:

- common/provider API codec and policy tests;
- local, remote, hybrid, unconfigured, and hostile fake targets;
- logical target-provider IDs distinct from the Binder provider ID;
- origin, locality, credential, capability, and limit escalation rejection;
- PackageManager, UID, signer, descriptor, deadline, and Binder death cases;
- continuation-token, generation, callback flood, and process-fuse cases;
- descriptor ownership and reliable-pipe failures;
- stream credit, aggregation, usage, finish reason, cancellation, and terminal
  race cases;
- host JVM and Android-test compilation;
- official plugin catalog, target request, declaration, backend, and UI policy
  tests.

Device evidence for 3-Stone AI additionally covers configured online profile
connection, online generation and regeneration, explicit cloud failure without
local fallback, local streaming cancellation with partial output retained, and
completed local regeneration with usage and duration.

## Remaining Product Work

Protocol and internal routing are complete for V2. Remaining roadmap work is
public product integration rather than a wire compatibility exercise:

- add the explicit public `target` selector;
- add `ai.catalog()` and derive model/profile/provider views from it;
- expose complete reasoning, tool calls, finish reason, profile, and usage data;
- normalize public target-specific errors;
- update TypeScript declarations and user documentation;
- migrate settings entry points without transporting credentials;
- remove the host online implementation only after the public plugin route and
  migration gates are complete.
